package org.foxesworld.kalitech.engine.api;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.KalitechApplication;
import org.foxesworld.kalitech.engine.api.impl.*;
import org.foxesworld.kalitech.engine.api.impl.debug.DebugDrawApiImpl;
import org.foxesworld.kalitech.engine.api.impl.hud.HudApiImpl;
import org.foxesworld.kalitech.engine.api.impl.input.InputApiImpl;
import org.foxesworld.kalitech.engine.api.impl.light.LightApiImpl;
import org.foxesworld.kalitech.engine.api.impl.material.MaterialApiImpl;
import org.foxesworld.kalitech.engine.api.impl.physics.PhysicsApiImpl;
import org.foxesworld.kalitech.engine.api.impl.sound.SoundApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.*;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.app.RuntimeAppState;
import org.foxesworld.kalitech.engine.perf.PerfProfiler;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

public final class EngineApiImpl implements EngineApi {

    private final Logger log = LogManager.getLogger(EngineApiImpl.class);


    private final PerfProfiler perf;
    private final SimpleApplication app;
    private final AssetManager assets;
    private final ScriptEventBus bus;
    private final EcsWorld ecs;
    private final Thread jmeThread;
    private volatile PhysicsSpace physicsSpace;
    private final GraalScriptRuntime runtime;

    private final CameraState cameraState;
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
    private final MeshApi mesh;
    private final LightApiImpl light;
    private final SoundApiImpl sound;
    private final DebugDrawApiImpl debug;

    // ✅ new: unified surface registry + apis
    private final SurfaceRegistry surfaceRegistry;
    private final SurfaceApi surfaceApi;
    private final TerrainApi terrainApi;
    private final TerrainSplatApi terrainSplatApi;

    public EngineApiImpl(RuntimeAppState runtimeAppState) {

        // Profiler
        PerfProfiler.Config pcfg = new PerfProfiler.Config();

        pcfg.enabled = true;
        pcfg.writeToFile = true;
        pcfg.writeToLog = false;

        pcfg.windowFrames = 900;          // 15 сек истории
        pcfg.summaryEveryFrames = 60;     // каждую секунду
        pcfg.spikeThresholdNanos = 500_000; // 0.5ms (жёстко)


        // ⬇⬇⬇ ВАЖНО ⬇⬇⬇
        pcfg.outputFile = "logs/perf-engine.jsonl"; // путь к файлу
        pcfg.flushEverySummary = true;            // безопасно (можно false)
        this.perf = new PerfProfiler(log, pcfg);



        this.app = runtimeAppState.getSa();
        this.assets = runtimeAppState.getSa().getAssetManager();
        this.bus = runtimeAppState.getBus();
        this.ecs = runtimeAppState.getEcs();
        this.runtime = runtimeAppState.getRuntime();

        this.logApi = new LogApiImpl(this);
        this.assetsApi = new AssetsApiImpl(this);
        this.light = new LightApiImpl(this);
        this.sound = new SoundApiImpl(this);
        this.eventsApi = new EventsApiImpl(this);
        this.materialApi = new MaterialApiImpl(this);
        this.jmeThread = Thread.currentThread();
        //this.ui = new UiApiImpl();

        // ✅ registry must be created early
        this.surfaceRegistry = new SurfaceRegistry(this.app);
        //ALL ABOVE REQUIRE surfaceRegistry
        this.terrainApi = new TerrainApiImpl(this);
        this.terrainSplatApi = new TerrainSplatApiImpl(this);
        this.editorLinesApi = new EditorLinesApiImpl(this, surfaceRegistry);
        this.physicsApi = new PhysicsApiImpl(this, surfaceRegistry);
        this.mesh = new MeshApiImpl(this, assets, surfaceRegistry);
        this.surfaceApi = new SurfaceApiImpl(this, surfaceRegistry);


        this.debug = new DebugDrawApiImpl(this);
        this.entityApi = new EntityApiImpl(this);
        this.renderApi = new RenderApiImpl(this);
        this.hudApi = new HudApiImpl(this);

        this.cameraState = new CameraState();
        this.cameraApi = new CameraApiImpl(this);

        this.timeApi = new TimeApiImpl(this);
        this.inputApi = new InputApiImpl(this);
        this.worldApi = new WorldApiImpl(this);
        this.editorApi = new EditorApiImpl(this);
    }

    @HostAccess.Export @Override public LogApi log() { return logApi; }
    @HostAccess.Export @Override public AssetsApi assets() { return assetsApi; }
    @HostAccess.Export @Override public EventsApi events() { return eventsApi; }
    @HostAccess.Export @Override public MaterialApi material() { return materialApi; }
    @HostAccess.Export @Override public EntityApi entity() { return entityApi; }
    @HostAccess.Export @Override public SoundApi sound() { return sound; }

    @HostAccess.Export @Override public RenderApi render() { return renderApi; }
    @HostAccess.Export @Override public CameraApi camera() { return cameraApi; }
    @HostAccess.Export @Override public PhysicsApi physics() { return physicsApi; }
    @HostAccess.Export @Override public HudApi hud() { return hudApi; }
    @HostAccess.Export @Override public MeshApi mesh() { return mesh; }
    @HostAccess.Export @Override public LightApi light() { return light; }
    @HostAccess.Export @Override public DebugDrawApi debug() { return debug; }



    @HostAccess.Export @Override public SurfaceApi surface() { return surfaceApi; }
    @HostAccess.Export @Override public TerrainApi terrain() { return terrainApi; }
    @HostAccess.Export @Override public TerrainSplatApi terrainSplat() { return terrainSplatApi; }
    @HostAccess.Export @Override public EditorLinesApi editorLines() { return editorLinesApi; }


    @HostAccess.Export @Override public String engineVersion() { return ((KalitechApplication) app).getVersion(); }
    @HostAccess.Export @Override public TimeApi time() { return timeApi; }
    @HostAccess.Export @Override public InputApi input() { return inputApi; }
    @HostAccess.Export @Override public WorldApi world() { return worldApi; }
    @HostAccess.Export @Override public EditorApi editor() { return editorApi; }
    @HostAccess.Export
    @Override public boolean isJmeThread() {        return Thread.currentThread() == jmeThread;}


    // internal hooks
    public void __updateTime(double tpf) {
        perf.beginFrame();

        long t;

        t = perf.begin("time.update");
        timeApi.update(tpf);
        perf.end("time.update", t);

        // camera flush
        t = perf.begin("camera.flush");
        if (cameraApi instanceof CameraApiImpl c) c.__flush();
        perf.end("camera.flush", t);

        t = perf.begin("hud.tick");
        this.hudApi.__tick();
        perf.end("hud.tick", t);

        t = perf.begin("debug.tick");
        this.debug.tick(tpf);
        perf.end("debug.tick", t);

        perf.endFrame(tpf);
    }


    public void __endFrameInput() { inputApi.endFrame(); }

    /** internal: used by RuntimeAppState / WorldBuilder based on world.mode */
    public void __setEditorMode(boolean enabled) {
        try {
            editorApi.setEnabled(enabled);
        } catch (Throwable t) {
            log.error("__setEditorMode failed", t);
        }
    }

    // ✅ called by EntityApiImpl.destroy before ecs.destroyEntity
    public void __surfaceCleanupOnEntityDestroy(int entityId) {
        try {
            Integer surfaceId = surfaceRegistry.detachEntity(entityId);
            if (surfaceId != null) {
                // ✅ remove physics first (or after) — безопасно
                try { physicsApi.__cleanupSurface(surfaceId); } catch (Throwable ignored) {}

                surfaceRegistry.destroy(surfaceId);
            }
            try { ecs.components().removeByName(entityId, "Surface"); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            log.warn("__surfaceCleanupOnEntityDestroy failed entityId={}", entityId, t);
        }
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
                log.error("JS runOnMainThread failed", t);
            }
            return null;
        });
    }

    public void __setPhysicsSpace(PhysicsSpace space) {
        this.physicsSpace = space;
    }

    public PhysicsSpace __getPhysicsSpaceOrNull() {
        return physicsSpace;
    }

    // called before ecs.reset()/world rebuild
    public void __physicsClearWorld() {
        try {
            if (physics() instanceof PhysicsApiImpl p) {
                p.__clearAll();
            }
        } catch (Throwable ignored) {}
    }


    public GraalScriptRuntime getRuntime() {
        return runtime;
    }

    public SurfaceRegistry getSurfaceRegistry() {
        return surfaceRegistry;
    }

    public void __tickHud() { hudApi.__tick(); }
    public CameraState getCameraState() { return cameraState; }
    public AssetManager getAssets() { return assets; }
    public SimpleApplication getApp() { return app; }
    public ScriptEventBus getBus() { return bus; }
    public EcsWorld getEcs() { return ecs; }
    public Logger getLog() { return log; }

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }
}