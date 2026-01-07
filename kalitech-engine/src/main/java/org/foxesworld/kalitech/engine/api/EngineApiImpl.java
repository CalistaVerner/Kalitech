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
 * <p>
 * Script-driven principles:
 * - No must-have subsystems: world/editor/etc are optional by design.
 * - This class provides stable, high-perf host APIs for JS.
 * - It must NOT interpret game/app semantics (no "worldDesc mode/entities" logic).
 * <p>
 * Notes:
 * - ScriptEventBus is optional during early boot. All API impls must tolerate null bus.
 * - PhysicsSpace is injected later by RuntimeAppState; treat as optional.
 */
public final class EngineApiImpl implements EngineApi {

    private static final Logger LOG = LogManager.getLogger(EngineApiImpl.class);

    private final PerfProfiler perf;

    private final SimpleApplication app;
    private final AssetManager assets;

    /**
     * Script event bus used to bridge engine <-> JS.
     * Optional: can be null during early boot / some tool runtimes.
     */
    private final ScriptEventBus bus;

    private final EcsWorld ecs;
    private final Thread jmeThread;
    private volatile PhysicsSpace physicsSpace;
    private final ScriptRuntime runtime;

    private final BulletAppState bullet;

    // core apis
    private final LogApi logApi;
    private final AssetsApi assetsApi;
    private final EventsApi eventsApi;
    private final EntityApi entityApi;
    private final RenderApi renderApi;
    private final CameraApi cameraApi;
    private final TimeApiImpl timeApi;
    private final InputApiImpl inputApi;
    private final WorldApi worldApi;           // optional subsystem behind implementation
    private final MaterialApi materialApi;
    private final EditorApi editorApi;         // optional subsystem behind implementation
    private final EditorLinesApi editorLinesApi;
    private final PhysicsApiImpl physicsApi;
    private final HudApiImpl hudApi;
    private final MeshApi meshApi;
    private final LightApiImpl lightApi;
    private final SoundApiImpl soundApi;
    private final DebugDrawApiImpl debugApi;

    // ✅ unified surface registry + apis
    private final SurfaceRegistry surfaceRegistry;
    private final SurfaceApi surfaceApi;
    private final TerrainApi terrainApi;
    private final TerrainSplatApi terrainSplatApi;

    // FPS measurement (stable)
    private volatile double fps = 0.0;
    private double fpsAcc = 0.0;
    private int fpsFrames = 0;
    private double fpsWindowSec = 0.25;

    // optional EMA smoothing
    private double fpsEma = 0.0;
    private double fpsEmaTau = 0.20;

    public EngineApiImpl(RuntimeAppState runtimeAppState) {
        Objects.requireNonNull(runtimeAppState, "runtimeAppState");

        this.bullet = runtimeAppState.getBullet();

        // --- Profiler config (NO hard requirement; can be disabled by property) ---
        PerfProfiler.Config pcfg = new PerfProfiler.Config();

        // defaults: safe + useful in dev, still not "must-have"
        pcfg.enabled = boolProp("kalitech.perf.enabled", true);
        pcfg.writeToFile = boolProp("kalitech.perf.writeToFile", true);
        pcfg.writeToLog = boolProp("kalitech.perf.writeToLog", false);

        pcfg.windowFrames = intProp("kalitech.perf.windowFrames", 900);
        pcfg.summaryEveryFrames = intProp("kalitech.perf.summaryEveryFrames", 60);
        pcfg.spikeThresholdNanos = longProp("kalitech.perf.spikeThresholdNanos", 500_000);

        pcfg.outputFile = System.getProperty("kalitech.perf.outputFile", "logs/perf-engine.jsonl");
        pcfg.flushEverySummary = boolProp("kalitech.perf.flushEverySummary", true);

        this.perf = new PerfProfiler(LOG, pcfg);

        // --- Environment ---
        this.app = runtimeAppState.getSa();
        this.assets = app.getAssetManager();

        this.bus = runtimeAppState.getBus();   // may be null (allowed)
        this.ecs = runtimeAppState.getEcs();
        this.runtime = runtimeAppState.getRuntime();

        // captured on init thread; RuntimeAppState initializes on JME thread
        this.jmeThread = Thread.currentThread();

        // --- Base APIs (no semantics) ---
        this.logApi = new LogApiImpl(this);
        this.assetsApi = new AssetsApiImpl(this);
        this.eventsApi = new EventsApiImpl(this);
        this.materialApi = new MaterialApiImpl(this);

        // ✅ registry must be created early
        this.surfaceRegistry = new SurfaceRegistry(this.app, this.bus);

        // APIs that depend on surfaceRegistry
        this.terrainApi = new TerrainApiImpl(this);
        this.terrainSplatApi = new TerrainSplatApiImpl(this);
        this.editorLinesApi = new EditorLinesApiImpl(this, surfaceRegistry);
        this.physicsApi = new PhysicsApiImpl(this, surfaceRegistry);
        this.meshApi = new MeshApiImpl(this, assets, surfaceRegistry);
        this.surfaceApi = new SurfaceApiImpl(this, surfaceRegistry);

        // remaining APIs
        this.lightApi = new LightApiImpl(this);
        this.soundApi = new SoundApiImpl(this);
        this.debugApi = new DebugDrawApiImpl(this);
        this.entityApi = new EntityApiImpl(this);
        this.renderApi = new RenderApiImpl(this);
        this.hudApi = new HudApiImpl(this);

        this.cameraApi = new CameraApiImpl(this);
        this.timeApi = new TimeApiImpl(this);
        this.inputApi = new InputApiImpl(this);

        // Optional subsystems: implementations MUST be no-op friendly when subsystem not used.
        this.worldApi = new WorldApiImpl(this);
        this.editorApi = new EditorApiImpl(this);
    }

    // ---------------- EngineApi exports ----------------

    @HostAccess.Export @Override public LogApi log() { return logApi; }
    @HostAccess.Export @Override public AssetsApi assets() { return assetsApi; }
    @HostAccess.Export @Override public EventsApi bus() { return eventsApi; }
    @HostAccess.Export @Override public MaterialApi material() { return materialApi; }

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

    @HostAccess.Export @Override public RenderApi render() { return renderApi; }
    @HostAccess.Export @Override public CameraApi camera() { return cameraApi; }
    @HostAccess.Export @Override public PhysicsApi physics() { return physicsApi; }

    private static long longProp(String key, long def) {
        String v = System.getProperty(key);
        if (v == null) return def;
        try {
            return Long.parseLong(v.trim());
        } catch (Throwable ignored) {
            return def; }
    }

    @HostAccess.Export @Override public EntityApi entity() { return entityApi;
    }

    @HostAccess.Export
    @Override
    public SoundApi sound() {
        return soundApi; }

    @HostAccess.Export @Override public HudApi hud() { return hudApi;
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
    public DebugDrawApi debug() {
        return debugApi; }

    @HostAccess.Export @Override public EditorLinesApi editorLines() { return editorLinesApi;
    }

    // ---------------- Internal frame hooks ----------------

    @HostAccess.Export
    @Override
    public String engineVersion() {
        // no hard dependency: if app is not KalitechApplication, return "unknown"
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

    /**
     * Called once per frame by RuntimeAppState/WorldAppState.
     * MUST remain stable and cheap.
     */
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

        // Keep audio bridge optional (no crash if bridge fails)
        try {
            var cam = app.getCamera();
            KalitechAudioBridge.syncListener(cam.getLocation(), cam.getRotation());
        } catch (Throwable ignored) {}

        perf.endFrame(tpf);
    }

    public void __endFrameInput() {
        inputApi.endFrame();
    }

    /**
     * Internal helper for optional subsystems.
     * Not tied to any "world mode" semantics.
     */
    public void __setEditorEnabled(boolean enabled) {
        try {
            editorApi.setEnabled(enabled);
        } catch (Throwable t) {
            LOG.error("__setEditorEnabled failed", t);
        }
    }

    // ---------------- Physics injection (optional) ----------------

    public void __setPhysicsSpace(PhysicsSpace space) {
        this.physicsSpace = space;
    }

    public PhysicsSpace __getPhysicsSpaceOrNull() {
        return physicsSpace;
    }

    /**
     * ✅ called by EntityApiImpl.destroy before ecs.destroyEntity
     */
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

    // ---------------- Getters for implementations ----------------

    public ScriptRuntime getRuntime() {
        return runtime;
    }

    public SurfaceRegistry getSurfaceRegistry() {
        return surfaceRegistry;
    }

    @HostAccess.Export
    @Override
    public void runOnMainThread(Value fn) {
        if (fn == null || fn.isNull()) return;
        if (!fn.canExecute()) {
            throw new IllegalArgumentException("runOnMainThread(fn): fn must be executable");
        }

        app.enqueue(() -> {
            try {
                fn.executeVoid();
            } catch (Throwable t) {
                LOG.error("JS runOnMainThread failed", t);
            }
            return null;
        });
    }

    public AssetManager getAssets() { return assets; }

    private void __updateFps(double tpf) {
        if (!(tpf > 0.0) || !Double.isFinite(tpf)) return;

        // 1) windowed FPS (stable)
        fpsAcc += tpf;
        fpsFrames++;

        if (fpsAcc >= fpsWindowSec) {
            double v = fpsFrames / fpsAcc;
            if (v > 0.0 && Double.isFinite(v)) fps = v;
            fpsAcc = 0.0;
            fpsFrames = 0;
        }

        // 2) EMA smoothing (optional)
        double inst = 1.0 / tpf;
        if (inst > 1000.0) inst = 1000.0;

        final double tau = (fpsEmaTau > 1e-6) ? fpsEmaTau : 0.20;
        double alpha = 1.0 - Math.exp(-tpf / tau);
        if (!(alpha > 0.0 && alpha <= 1.0)) alpha = 0.15;

        if (fpsEma <= 0.0) fpsEma = inst;
        else fpsEma += (inst - fpsEma) * alpha;
    }

    /**
     * Called before ecs.reset()/world rebuild, if a caller decides to do so.
     * Safe no-op when physics API is not ready.
     */
    public void __physicsClearWorld() {
        try {
            physicsApi.__clearAll();
        } catch (Throwable ignored) {}
    }

    public BulletAppState getBullet() {
        return bullet; }

    public SimpleApplication getApp() { return app;
    }

    /** May return null; callers MUST tolerate it. */
    public ScriptEventBus getBus() { return bus; }

    // ---------------- Small helpers ----------------

    public EcsWorld getEcs() { return ecs;
    }

    public Logger getLog() {
        return LOG;
    }

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }
}