// FILE: org/foxesworld/kalitech/engine/api/EngineApiImpl.java
package org.foxesworld.kalitech.engine.api;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.audio.KalitechAudioBridge;
import org.foxesworld.kalitech.engine.KalitechApplication;
import org.foxesworld.kalitech.engine.api.impl.*;
import org.foxesworld.kalitech.engine.api.interfaces.*;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.module.ApiRegistry;
import org.foxesworld.kalitech.engine.app.RuntimeAppState;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.perf.PerfProfiler;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;

/**
 * EngineApiImpl
 * Script-driven principles:
 * - No must-have subsystems: world/editor/etc are optional by design.
 * - Stable, high-perf host APIs for JS.
 * - Must NOT interpret game/app semantics (no "worldDesc mode/entities" logic).
 */
public final class EngineApiImpl implements EngineApi {

    private static final Logger LOG = LogManager.getLogger(EngineApiImpl.class);

    private final PerfProfiler perf;

    private final SimpleApplication app;
    private final AssetManager assets;

    private final ScriptEventBus bus; // may be null
    private final EcsWorld ecs;
    private final Thread jmeThread;
    private volatile PhysicsSpace physicsSpace;
    private final ScriptRuntime runtime;

    private final BulletAppState bullet;

    // ✅ API registry / context
    private final ApiContext apiCtx;
    private final ApiRegistry apiRegistry;

    // core apis
    private final LogApi logApi;
    private final AssetsApi assetsApi;
    private final EventsApi eventsApi;
    private final EntityApi entityApi;
    private final RenderApi renderApi;
    private final CameraApi cameraApi;
    private final TimeApiImpl timeApi;
    private final InputApiImpl inputApi;
    private final WorldApi worldApi;
    private final MaterialApi materialApi;
    private final EditorApi editorApi;
    private final EditorLinesApi editorLinesApi;
    private final PhysicsApiImpl physicsApi;
    private final HudApiImpl hudApi;
    private final MeshApi meshApi;
    private final LightApiImpl lightApi;
    private final SoundApiImpl soundApi;
    private final DebugDrawApiImpl debugApi;

    private final SurfaceRegistry surfaceRegistry;
    private final SurfaceApi surfaceApi;
    private final TerrainApi terrainApi;
    private final TerrainSplatApi terrainSplatApi;

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

        this.app = runtimeAppState.getSa();
        this.assets = app.getAssetManager();

        this.bus = runtimeAppState.getBus();
        this.ecs = runtimeAppState.getEcs();
        this.runtime = runtimeAppState.getRuntime();

        this.jmeThread = Thread.currentThread();

        // ✅ registry/bootstrap context (one for all modules)
        this.apiCtx = new ApiContext(this);
        this.apiRegistry = new ApiRegistry(apiCtx);

        // --- Base APIs ---
        this.logApi = apiRegistry.register(new LogApiImpl());
        this.assetsApi = apiRegistry.register(new AssetsApiImpl());
        this.eventsApi = apiRegistry.register(new EventsApiImpl());
        this.timeApi = apiRegistry.register(new TimeApiImpl());
        this.inputApi = apiRegistry.register(new InputApiImpl());

        // ✅ next wave: material/render/entity/camera
        this.materialApi = apiRegistry.register(new MaterialApiImpl());
        this.renderApi = apiRegistry.register(new RenderApiImpl());
        this.entityApi = apiRegistry.register(new EntityApiImpl());
        this.cameraApi = apiRegistry.register(new CameraApiImpl());
        this.surfaceRegistry = new SurfaceRegistry(this.app, this.bus);

        this.physicsApi = apiRegistry.register(new PhysicsApiImpl());
        this.surfaceApi = apiRegistry.register(new SurfaceApiImpl());

        this.terrainApi = apiRegistry.register(new TerrainApiImpl());
        this.terrainSplatApi = apiRegistry.register(new TerrainSplatApiImpl());
        this.editorLinesApi = apiRegistry.register(new EditorLinesApiImpl());
        this.meshApi = apiRegistry.register(new MeshApiImpl());

        this.lightApi = apiRegistry.register(new LightApiImpl());
        this.soundApi = apiRegistry.register(new SoundApiImpl());
        this.debugApi = apiRegistry.register(new DebugDrawApiImpl());
        this.hudApi = apiRegistry.register(new HudApiImpl());
        this.worldApi = apiRegistry.register(new WorldApiImpl());
        this.editorApi = apiRegistry.register(new EditorApiImpl());
    }

    @HostAccess.Export @Override public LogApi log() { return logApi; }
    @HostAccess.Export @Override public AssetsApi assets() { return assetsApi; }
    @HostAccess.Export @Override public EventsApi bus() { return eventsApi; }
    @HostAccess.Export @Override public MaterialApi material() { return materialApi; }

    @HostAccess.Export @Override public RenderApi render() { return renderApi; }
    @HostAccess.Export @Override public CameraApi camera() { return cameraApi; }
    @HostAccess.Export @Override public PhysicsApi physics() { return physicsApi; }

    private static boolean boolProp(String key, boolean def) {
        String v = System.getProperty(key);
        if (v == null) return def;
        v = v.trim();
        if (v.isEmpty()) return def;
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
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

    @HostAccess.Export @Override public SurfaceApi surface() { return surfaceApi; }
    @HostAccess.Export @Override public TerrainApi terrain() { return terrainApi; }
    @HostAccess.Export @Override public TerrainSplatApi terrainSplat() { return terrainSplatApi; }

    @HostAccess.Export
    @Override
    public MeshApi mesh() {
        return meshApi;
    }

    @HostAccess.Export
    @Override
    public LightApi light() {
        return lightApi;
    }
    @HostAccess.Export @Override public TimeApi time() { return timeApi; }
    @HostAccess.Export @Override public InputApi input() { return inputApi; }
    @HostAccess.Export @Override public WorldApi world() { return worldApi; }
    @HostAccess.Export @Override public EditorApi editor() { return editorApi; }

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
    public String engineVersion() {
        try {
            if (app instanceof KalitechApplication ka) return ka.getVersion();
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    @HostAccess.Export
    @Override
    public boolean isJmeThread() {
        return Thread.currentThread() == jmeThread;
    }

    @HostAccess.Export
    @Override
    public double fps() {
        final double v = (fpsEma > 0.0) ? fpsEma : fps;
        return (v > 0.0 && Double.isFinite(v)) ? v : 0.0;
    }

    public void __updateTime(double tpf) {
        __updateFps(tpf);

        perf.beginFrame();

        long t;

        t = perf.begin("time.update");
        timeApi.update(tpf);
        perf.end("time.update", t);

        t = perf.begin("camera.flush");
        if (cameraApi instanceof CameraApiImpl c) c.__flush();
        perf.end("camera.flush", t);

        t = perf.begin("debug.tick");
        debugApi.tick(tpf);
        perf.end("debug.tick", t);

        try {
            var cam = app.getCamera();
            KalitechAudioBridge.syncListener(cam.getLocation(), cam.getRotation());
        } catch (Throwable ignored) {}

        perf.endFrame(tpf);
    }

    public void __endFrameInput() {
        inputApi.endFrame();
    }

    public void __setEditorEnabled(boolean enabled) {
        try {
            editorApi.setEnabled(enabled);
        } catch (Throwable t) {
            LOG.error("__setEditorEnabled failed", t);
        }
    }

    public void __setPhysicsSpace(PhysicsSpace space) {
        this.physicsSpace = space;
    }

    public PhysicsSpace __getPhysicsSpaceOrNull() {
        return physicsSpace;
    }

    public void __surfaceCleanupOnEntityDestroy(int entityId) {
        try {
            Integer surfaceId = surfaceRegistry.detachEntity(entityId);
            if (surfaceId != null) {
                try { physicsApi.__cleanupSurface(surfaceId); } catch (Throwable ignored) {}
                surfaceRegistry.destroy(surfaceId);
            }
            try { ecs.components().removeByName(entityId, "Surface"); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            LOG.warn("__surfaceCleanupOnEntityDestroy failed entityId={}", entityId, t);
        }
    }

    public ScriptRuntime getRuntime() {
        return runtime;
    }

    public SurfaceRegistry getSurfaceRegistry() {
        return surfaceRegistry;
    }

    @HostAccess.Export
    @Override
    public HudApi hud() {
        return hudApi;
    }

    public AssetManager getAssets() { return assets; }

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
    public DebugDrawApi debug() {
        return debugApi;
    }

    @HostAccess.Export
    @Override
    public EditorLinesApi editorLines() {
        return editorLinesApi;
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
    public ScriptEventBus getBus() { return bus; }

    public void __physicsClearWorld() {
        try {
            physicsApi.__clearAll();
        } catch (Throwable ignored) {
        }
    }

    public Logger getLog() {
        return LOG;
    }

    public BulletAppState getBullet() {
        return bullet;
    }

    public SimpleApplication getApp() {
        return app;
    }

    public EcsWorld getEcs() {
        return ecs;
    }

    // ✅ expose registry internally for diagnostics/tools (не ломаем JS контракт)
    public ApiRegistry getApiRegistry() {
        return apiRegistry;
    }
}