// FILE: org/foxesworld/kalitech/engine/api/EngineApiImpl.java
package org.foxesworld.kalitech.engine.api;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.KalitechApplication;
import org.foxesworld.kalitech.engine.api.interfaces.*;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.module.ApiModuleProvider;
import org.foxesworld.kalitech.engine.api.module.ApiRegistry;
import org.foxesworld.kalitech.engine.api.module.EngineCameraModule;
import org.foxesworld.kalitech.engine.api.module.EngineDebugModule;
import org.foxesworld.kalitech.engine.api.module.EnginePhysicsModule;
import org.foxesworld.kalitech.engine.api.module.EngineRenderModule;
import org.foxesworld.kalitech.engine.api.module.EngineTimeModule;
import org.foxesworld.kalitech.engine.api.registry.TaskRegistry;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.app.RuntimeAppState;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.perf.PerfProfiler;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
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


    private final ApiContext apiCtx;
    private final ApiRegistry apiRegistry;
    private final TaskRegistry taskRegistry;

    private final LogApi logApi;
    private final AssetsApi assetsApi;
    private final EventsApi eventsApi;
    private final EntityApi entityApi;
    private final RenderApi renderApi;
    private final CameraApi cameraApi;
    private final TimeApi timeApi;
    private final InputApi inputApi;
    private final WorldApi worldApi;
    private final MaterialApi materialApi;
    private final EditorApi editorApi;
    private final EditorLinesApi editorLinesApi;
    private final PhysicsApi physicsApi;
    private final HudApi hudApi;
    private final MeshApi meshApi;
    private final LightApi lightApi;
    private final SoundApi soundApi;
    private final DebugDrawApi debugApi;
    private final ParticlesApi particles;

    private final SurfaceRegistry surfaceRegistry;
    private final SurfaceApi surfaceApi;
    private final TerrainApi terrainApi;
    private final TerrainSplatApi terrainSplatApi;

    private final EngineTimeModule timeModule;
    private final EngineCameraModule cameraModule;
    private final EngineDebugModule debugModule;
    private final EnginePhysicsModule physicsModule;
    private final EngineRenderModule renderModule;

    private volatile double fps = 0.0;
    private double fpsAcc = 0.0;
    private int fpsFrames = 0;
    private double fpsWindowSec = 0.25;

    private double fpsEma = 0.0;
    private double fpsEmaTau = 0.20;

    public EngineApiImpl(RuntimeAppState runtimeAppState) {
        Objects.requireNonNull(runtimeAppState, "runtimeAppState");

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

        // NO legacy ctor
        this.surfaceRegistry = new SurfaceRegistry(this.app, this::getBus);

        registerApiModules();

        this.logApi = requireApi(LogApi.class);
        this.assetsApi = requireApi(AssetsApi.class);
        this.eventsApi = requireApi(EventsApi.class);
        this.timeApi = requireApi(TimeApi.class);
        this.inputApi = requireApi(InputApi.class);

        this.materialApi = requireApi(MaterialApi.class);
        this.renderApi = requireApi(RenderApi.class);
        this.entityApi = requireApi(EntityApi.class);
        this.cameraApi = requireApi(CameraApi.class);

        this.physicsApi = requireApi(PhysicsApi.class);
        this.surfaceApi = requireApi(SurfaceApi.class);

        this.terrainApi = requireApi(TerrainApi.class);
        this.terrainSplatApi = requireApi(TerrainSplatApi.class);
        this.editorLinesApi = requireApi(EditorLinesApi.class);
        this.meshApi = requireApi(MeshApi.class);

        this.lightApi = requireApi(LightApi.class);
        this.soundApi = requireApi(SoundApi.class);
        this.debugApi = requireApi(DebugDrawApi.class);
        this.hudApi = requireApi(HudApi.class);
        this.worldApi = requireApi(WorldApi.class);
        this.editorApi = requireApi(EditorApi.class);
        this.particles = requireApi(ParticlesApi.class);

        this.timeModule = requireApi(EngineTimeModule.class);
        this.cameraModule = requireApi(EngineCameraModule.class);
        this.debugModule = requireApi(EngineDebugModule.class);
        this.physicsModule = requireApi(EnginePhysicsModule.class);
        this.renderModule = requireApi(EngineRenderModule.class);
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
        timeModule.update(tpf);
        perf.end("time.update", t);

        t = perf.begin("camera.flush");
        cameraModule.flush();
        perf.end("camera.flush", t);

        t = perf.begin("debug.tick");
        debugModule.tick(tpf);
        perf.end("debug.tick", t);

        //try {
        //    var cam = app.getCamera();
        //    KalitechAudioBridge.syncListener(cam.getLocation(), cam.getRotation());
        //} catch (Throwable ignored) {}

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
        return materialApi;
    }

    @HostAccess.Export
    @Override
    public EntityApi entity() {
        return entityApi;
    }

    @HostAccess.Export
    @Override
    public SoundApi sound() {
        return soundApi;
    }

    @HostAccess.Export
    @Override
    public RenderApi render() {
        return renderApi;
    }

    @HostAccess.Export
    @Override
    public CameraApi camera() {
        return cameraApi;
    }

    @HostAccess.Export
    @Override
    public PhysicsApi physics() {
        return physicsApi;
    }

    @HostAccess.Export
    @Override
    public LightApi light() {
        return lightApi;
    }

    @HostAccess.Export
    @Override
    public DebugDrawApi debug() {
        return debugApi;
    }

    @HostAccess.Export
    @Override
    public ParticlesApi particles() {
        return particles;
    }
    @HostAccess.Export @Override public SurfaceApi surface() { return surfaceApi; }
    @HostAccess.Export @Override public TerrainApi terrain() { return terrainApi; }
    @HostAccess.Export @Override public TerrainSplatApi terrainSplat() { return terrainSplatApi; }

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
    @HostAccess.Export @Override public TimeApi time() { return timeApi; }
    @HostAccess.Export @Override public InputApi input() { return inputApi; }
    @HostAccess.Export @Override public WorldApi world() { return worldApi; }
    @HostAccess.Export @Override public EditorApi editor() { return editorApi; }

    @HostAccess.Export
    @Override
    public String engineVersion() {
        return app != null ? ((KalitechApplication) app).getVersion() : "unknown";
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
            physicsModule.clearAll();
        } catch (Throwable t) {
            LOG.warn("__resetWorldState: physics.__clearAll failed reason={}", why, t);
        }

        try {
            renderModule.resetWorldCache(why);
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
                physicsModule.cleanupSurface(surfaceId);
            } catch (Throwable t) {
                LOG.warn("__surfaceCleanupOnEntityDestroy: physics cleanup failed surfaceId={} entityUuid={}",
                        surfaceId, uuid, t);
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
                LOG.warn("__surfaceCleanupOnEntityDestroy: registry destroy failed surfaceId={} entityUuid={}",
                        surfaceId, uuid, t);
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

    private void registerApiModules() {
        ServiceLoader<ApiModuleProvider> loader = ServiceLoader.load(ApiModuleProvider.class);
        List<ApiModuleProvider> providers = new ArrayList<>();
        for (ApiModuleProvider provider : loader) {
            providers.add(provider);
        }

        if (providers.isEmpty()) {
            LOG.warn("[api] no ApiModuleProvider implementations found");
            return;
        }

        providers.sort(Comparator.comparingInt(ApiModuleProvider::order).thenComparing(ApiModuleProvider::id));

        for (ApiModuleProvider provider : providers) {
            try {
                provider.register(apiRegistry);
                LOG.info("[api] ApiModuleProvider registered: id={} class={}",
                        provider.id(), provider.getClass().getName());
            } catch (Throwable t) {
                LOG.error("[api] ApiModuleProvider failed: id={} class={}",
                        provider.id(), provider.getClass().getName(), t);
            }
        }
    }

    private <T> T requireApi(Class<T> type) {
        Objects.requireNonNull(type, "type");
        for (ApiRegistry.Entry entry : apiRegistry.entries()) {
            if (type.isInstance(entry.api)) {
                return type.cast(entry.api);
            }
        }
        throw new IllegalStateException("Missing API module for type: " + type.getName());
    }
}
