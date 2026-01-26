// FILE: org/foxesworld/kalitech/engine/api/EngineApiImpl.java
package org.foxesworld.kalitech.engine.api;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.KalitechApplication;
import org.foxesworld.kalitech.engine.api.impl.*;
import org.foxesworld.kalitech.engine.api.interfaces.*;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.module.ApiRegistry;
import org.foxesworld.kalitech.engine.api.registry.TaskRegistry;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.app.RuntimeAppState;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.modules.moduleLoader.ModuleManager;
import org.foxesworld.kalitech.engine.modules.moduleLoader.RuntimeJsBridge;
import org.foxesworld.kalitech.engine.perf.PerfProfiler;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class EngineApiImpl implements EngineApi {

    private static final Logger LOG = LogManager.getLogger(EngineApiImpl.class);

    private final PerfProfiler perf;

    private final KalitechApplication app;
    private final AssetManager assets;

    private final ScriptEventBus bus; // may be null
    private final EcsWorld ecs;
    private final Thread jmeThread;
    private volatile PhysicsSpace physicsSpace;
    private final ScriptRuntime runtime;

    private final BulletAppState bullet;

    private final ApiContext apiCtx;
    private final ApiRegistry apiRegistry;
    private final TaskRegistry taskRegistry;

    private final LogApi logApi;
    private final AssetsApi assetsApi;
    private final EventsApi eventsApi;
    private final EntityApi entityApi;
    private RenderApi renderApi;
    private final CameraApi cameraApi;
    private final TimeApiImpl timeApi;
    private final WorldApi worldApi;
    //private final MaterialApi materialApi;
    private final EditorApi editorApi;
    private final EditorLinesApi editorLinesApi;
    private final PhysicsApiImpl physicsApi;
    private final HudApiImpl hudApi;
    private final ModulesApi modulesApi;
    private final MeshApi meshApi;
    //private final SoundApiImpl soundApi;
    private final DebugDrawApiImpl debugApi;
    private final ModuleManager moduleManager;

    private final SurfaceRegistry surfaceRegistry;
    private final SurfaceApi surfaceApi;
    //private final TerrainApi terrainApi;
    //private final TerrainSplatApi terrainSplatApi;

    private volatile double fps = 0.0;
    private double fpsAcc = 0.0;
    private int fpsFrames = 0;
    private double fpsWindowSec = 0.25;

    private double fpsEma = 0.0;
    private double fpsEmaTau = 0.20;

    public EngineApiImpl(RuntimeAppState runtimeAppState) {
        Objects.requireNonNull(runtimeAppState, "runtimeAppState");

        this.bullet = runtimeAppState.getBullet();

        PerfProfiler.Config pcfg = new PerfProfiler.Config();
        pcfg.enabled = boolProp("kalitech.perf.enabled", true);
        pcfg.writeToFile = boolProp("kalitech.perf.writeToFile", true);
        pcfg.writeToLog = boolProp("kalitech.perf.writeToLog", false);

        pcfg.windowFrames = intProp("kalitech.perf.windowFrames", 900);
        pcfg.summaryEveryFrames = intProp("kalitech.perf.summaryEveryFrames", 60);
        pcfg.spikeThresholdNanos = longProp("kalitech.perf.spikeThresholdNanos", 500_000);

        pcfg.outputFile = System.getProperty("kalitech.perf.outputFile", "logs/perf-engine.jsonl");
        pcfg.flushEverySummary = boolProp("kalitech.perf.flushEverySummary", true);

        this.perf = new PerfProfiler(LOG, pcfg);

        this.app = (KalitechApplication) runtimeAppState.getSa();
        this.assets = app.getAssetManager();

        this.bus = runtimeAppState.getBus();
        this.ecs = runtimeAppState.getEcs();
        this.runtime = runtimeAppState.getRuntime();

        this.jmeThread = Thread.currentThread();

        this.taskRegistry = new TaskRegistry();
        this.apiCtx = new ApiContext(this);
        this.apiRegistry = new ApiRegistry(apiCtx);

        this.moduleManager = new ModuleManager(LOG, this.apiRegistry, new RuntimeJsBridge(this.runtime), getClass().getClassLoader());

        // NO legacy ctor
        this.surfaceRegistry = new SurfaceRegistry(this.app, this::getBus);

        this.logApi = apiRegistry.register(new LogApiImpl());
        this.assetsApi = apiRegistry.register(new AssetsApiImpl());
        this.eventsApi = apiRegistry.register(new EventsApiImpl());
        this.timeApi = apiRegistry.register(new TimeApiImpl());
        //this.inputApi = apiRegistry.register(new InputApiImpl());

        //this.materialApi = apiRegistry.register(new MaterialApiImpl());
        //this.renderApi = apiRegistry.register(new RenderApiImpl());
        this.entityApi = apiRegistry.register(new EntityApiImpl());
        //this.cameraApi = apiRegistry.register(new CameraApiImpl());


        this.physicsApi = apiRegistry.register(new PhysicsApiImpl());
        this.surfaceApi = apiRegistry.register(new SurfaceApiImpl());

        //this.terrainApi = apiRegistry.register(new TerrainApiImpl());
        //this.terrainSplatApi = apiRegistry.register(new TerrainSplatApiImpl());
        this.editorLinesApi = apiRegistry.register(new EditorLinesApiImpl());
        this.meshApi = apiRegistry.register(new MeshApiImpl());

        //this.lightApi = apiRegistry.register(new LightApiImpl());
        //this.soundApi = apiRegistry.register(new SoundApiImpl());
        this.debugApi = apiRegistry.register(new DebugDrawApiImpl());
        this.hudApi = apiRegistry.register(new HudApiImpl());
        this.worldApi = apiRegistry.register(new WorldApiImpl());
        this.editorApi = apiRegistry.register(new EditorApiImpl());
        this.modulesApi = apiRegistry.register(new ModulesApiImpl());
        this.moduleManager.loadFromDir(java.nio.file.Path.of("./modules"));
        this.cameraApi = apiRegistry.api("camera", CameraApi.class);
        this.renderApi = apiRegistry.api("render", RenderApi.class);
    }

    private static boolean boolProp(String key, boolean def) {
        String v = System.getProperty(key);
        if (v == null) return def;
        v = v.trim();
        if (v.isEmpty()) return def;
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    }

    public void __updateTime(double tpf) {
        __updateFps(tpf);

        perf.beginFrame();

        long t;

        t = perf.begin("time.update");
        timeApi.update(tpf);
        perf.end("time.update", t);

        t = perf.begin("camera.flush");
        cameraApi.__flush();
        perf.end("camera.flush", t);

        t = perf.begin("debug.tick");
        debugApi.tick(tpf);
        perf.end("debug.tick", t);

        perf.endFrame(tpf);
    }


    private static int intProp(String key, int def) {
        String v = System.getProperty(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static long longProp(String key, long def) {
        String v = System.getProperty(key);
        if (v == null) return def;
        try {
            return Long.parseLong(v.trim());
        } catch (Throwable ignored) {
            return def;
        }
    }

    @HostAccess.Export
    @Override
    public LogApi log() {
        return logApi;
    }

    @HostAccess.Export
    @Override
    public AssetsApi assets() {
        return assetsApi;
    }

    @HostAccess.Export
    @Override
    public EventsApi bus() {
        return eventsApi;
    }

    @HostAccess.Export
    @Override
    public MaterialApi material() {
        return apiRegistry.api("material", MaterialApi.class);
    }

    @HostAccess.Export
    @Override
    public EntityApi entity() {
        return entityApi;
    }

    @HostAccess.Export
    @Override
    public SoundApi sound() {
        return apiRegistry.api("sound", SoundApi.class);
    }

    @HostAccess.Export
    @Override
    public RenderApi render() {
        return apiRegistry.api("render", RenderApi.class);
    }

    @Override
    @HostAccess.Export
    public CameraApi camera() {
        return apiRegistry.api("camera", CameraApi.class);
    }

    @HostAccess.Export
    @Override
    public PhysicsApi physics() {
        return physicsApi;
    }

    @HostAccess.Export
    @Override
    public LightApi light() {
        return apiRegistry.api("light", LightApi.class);
    }

    @HostAccess.Export
    @Override
    public DebugDrawApi debug() {
        return debugApi;
    }

    @HostAccess.Export
    @Override
    public ParticlesApi particles() {
        return apiRegistry.api("particles", ParticlesApi.class);
    }

    @HostAccess.Export
    @Override
    public SurfaceApi surface() {
        return surfaceApi;
    }

    @HostAccess.Export
    @Override
    public TerrainApi terrain() {
        return apiRegistry.api("terrain", TerrainApi.class);
    }

    @HostAccess.Export
    @Override
    public TerrainSplatApi terrainSplat() {
        return apiRegistry.api("terrainSplat", TerrainSplatApi.class);
    }

    @HostAccess.Export
    @Override
    public EditorLinesApi editorLines() {
        return editorLinesApi;
    }

    @HostAccess.Export
    @Override
    public MeshApi mesh() {
        return meshApi;
    }

    @HostAccess.Export
    @Override
    public HudApi hud() {
        return hudApi;
    }

    @HostAccess.Export
    @Override
    public TimeApi time() {
        return timeApi;
    }

    @Override
    @HostAccess.Export
    public InputApi input() {
        return apiRegistry.api("input", InputApi.class);
    }

    @HostAccess.Export
    @Override
    public WorldApi world() {
        return worldApi;
    }

    @HostAccess.Export
    @Override
    public EditorApi editor() {
        return editorApi;
    }

    @HostAccess.Export
    @Override
    public ModulesApi modules() {
        return modulesApi;
    }

    @HostAccess.Export
    @Override
    public String engineVersion() {
        return app != null ? app.getVersion() : "unknown";
    }

    @HostAccess.Export
    @Override
    public boolean isJmeThread() {
        return Thread.currentThread() == jmeThread;
    }

    @HostAccess.Export
    @Override
    public double fps() {
        return fps;
    }

    public void __setPhysicsSpace(PhysicsSpace space) {
        this.physicsSpace = space;
    }

    public PhysicsSpace __getPhysicsSpaceOrNull() {
        return physicsSpace;
    }

    public ScriptRuntime getRuntime() {
        return runtime;
    }

    public TaskRegistry getTaskRegistry() {
        return taskRegistry;
    }

    public ApiRegistry getApiRegistry() {
        return apiRegistry;
    }

    public SurfaceRegistry getSurfaceRegistry() {
        return surfaceRegistry;
    }

    public AssetManager getAssets() {
        return assets;
    }

    public ScriptEventBus getBus() {
        return bus;
    }

    public void __resetWorldState(String reason) {
        final String why = (reason == null || reason.isBlank()) ? "F5" : reason.trim();

        try {
            ecs.reset();
        } catch (Throwable t) {
            LOG.warn("__resetWorldState: ecs.reset failed reason={}", why, t);
        }

        try {
            if (isJmeThread()) {
                surfaceRegistry.resetAll(why);
            } else {
                Future<?> f = app.enqueue(() -> {
                    surfaceRegistry.resetAll(why);
                    return null;
                });
                f.get(2, TimeUnit.SECONDS);
            }
        } catch (Throwable t) {
            LOG.warn("__resetWorldState: surfaceRegistry.resetAll failed reason={}", why, t);
        }

        try {
            physicsApi.__clearAll();
        } catch (Throwable t) {
            LOG.warn("__resetWorldState: physics.__clearAll failed reason={}", why, t);
        }

        try {
            renderApi.__resetWorldCache(why);
        } catch (Throwable t) {
            LOG.warn("__resetWorldState: render cache reset failed reason={}", why, t);
        }
    }

    /**
     * UUID-only surface cleanup hook.
     * Call this from ECS when an entity is destroyed.
     */
    public void __surfaceCleanupOnEntityDestroy(String entityUuid) {
        if (entityUuid == null) return;
        String uuid = entityUuid.trim();
        if (uuid.isEmpty()) return;

        Integer surfaceId;
        try {
            surfaceId = surfaceRegistry.detachEntity(uuid);
        } catch (Throwable t) {
            LOG.warn("__surfaceCleanupOnEntityDestroy: detachEntity failed entityUuid={}", uuid, t);
            surfaceId = null;
        }

        if (surfaceId != null) {
            try {
                physicsApi.__cleanupSurface(surfaceId);
            } catch (Throwable t) {
                LOG.warn("__surfaceCleanupOnEntityDestroy: physics cleanup failed surfaceId={} entityUuid={}", surfaceId, uuid, t);
            }

            try {
                if (isJmeThread()) {
                    surfaceRegistry.destroy(surfaceId);
                } else {
                    Integer finalSurfaceId = surfaceId;
                    Future<?> f = app.enqueue(() -> {
                        surfaceRegistry.destroy(finalSurfaceId);
                        return null;
                    });
                    f.get(2, TimeUnit.SECONDS);
                }
            } catch (Throwable t) {
                LOG.warn("__surfaceCleanupOnEntityDestroy: registry destroy failed surfaceId={} entityUuid={}", surfaceId, uuid, t);
            }
        }

        try {
            ecs.removeComponentByName(uuid, "Surface");
        } catch (Throwable t) {
            LOG.warn("__surfaceCleanupOnEntityDestroy: ecs component cleanup failed entityUuid={}", uuid, t);
        }
    }

    private void __updateFps(double tpf) {
        if (!(tpf > 0.0) || !Double.isFinite(tpf)) return;

        fpsAcc += tpf;
        fpsFrames++;

        if (fpsAcc >= fpsWindowSec) {
            double v = fpsFrames / fpsAcc;
            if (v > 0.0 && Double.isFinite(v)) fps = v;
            fpsAcc = 0.0;
            fpsFrames = 0;
        }

        double inst = 1.0 / tpf;
        if (inst > 1000.0) inst = 1000.0;

        final double tau = (fpsEmaTau > 1e-6) ? fpsEmaTau : 0.20;
        double alpha = 1.0 - Math.exp(-tpf / tau);
        if (!(alpha > 0.0 && alpha <= 1.0)) alpha = 0.15;

        if (fpsEma <= 0.0) fpsEma = inst;
        else fpsEma += (inst - fpsEma) * alpha;
    }

    @HostAccess.Export
    @Override
    public void runOnMainThread(Value fn) {
        if (fn == null || fn.isNull()) return;
        if (!fn.canExecute()) throw new IllegalArgumentException("runOnMainThread(fn): fn must be executable");

        app.enqueue(() -> {
            try {
                fn.executeVoid();
            } catch (Throwable t) {
                LOG.error("JS runOnMainThread failed", t);
            }
            return null;
        });
    }

    public Logger getLog() {
        return LOG;
    }

    public SimpleApplication getApp() {
        return app;
    }

    public EcsWorld getEcs() {
        return ecs;
    }

    @HostAccess.Export
    public Object api(String id) {
        if (id == null) return null;
        ApiRegistry.Entry e = this.apiRegistry.get(id);
        return (e != null) ? e.api : null;
    }

}
