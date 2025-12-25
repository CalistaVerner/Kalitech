package org.foxesworld.kalitech.engine.api;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.KalitechApplication;
import org.foxesworld.kalitech.engine.api.impl.*;
import org.foxesworld.kalitech.engine.api.impl.hud.HudApiImpl;
import org.foxesworld.kalitech.engine.api.impl.physics.PhysicsApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.*;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Objects;

public final class EngineApiImpl implements EngineApi {

    private final Logger log = LogManager.getLogger(EngineApiImpl.class);

    private final SimpleApplication app;
    private final AssetManager assets;
    private final ScriptEventBus bus;
    private final EcsWorld ecs;
    private final Thread jmeThread;
    private volatile PhysicsSpace physicsSpace;

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
    //private final UiApiImpl ui;

    // ✅ new: unified surface registry + apis
    private final SurfaceRegistry surfaceRegistry;
    private final SurfaceApi surfaceApi;
    private final TerrainApi terrainApi;
    private final TerrainSplatApi terrainSplatApi;

    public EngineApiImpl(SimpleApplication app, AssetManager assets, ScriptEventBus bus, EcsWorld ecs) {
        this.app = Objects.requireNonNull(app, "app");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.ecs = Objects.requireNonNull(ecs, "ecs");

        this.logApi = new LogApiImpl(this);
        this.assetsApi = new AssetsApiImpl(this);
        this.eventsApi = new EventsApiImpl(this);
        this.materialApi = new MaterialApiImpl(this);
        this.jmeThread = Thread.currentThread();
        //this.ui = new UiApiImpl();

        // ✅ registry must be created early
        this.surfaceRegistry = new SurfaceRegistry(this.app);
        this.surfaceApi = new SurfaceApiImpl(this, surfaceRegistry);
        this.terrainApi = new TerrainApiImpl(this, surfaceRegistry);
        this.terrainSplatApi = new TerrainSplatApiImpl(this, surfaceRegistry);
        this.editorLinesApi = new EditorLinesApiImpl(this, surfaceRegistry);
        this.physicsApi = new PhysicsApiImpl(this, surfaceRegistry);

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
    @HostAccess.Export @Override public RenderApi render() { return renderApi; }
    @HostAccess.Export @Override public CameraApi camera() { return cameraApi; }
    @HostAccess.Export @Override public PhysicsApi physics() { return physicsApi; }
    @HostAccess.Export @Override public HudApi hud() { return hudApi; }


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
        timeApi.update(tpf);
        this.hudApi.__tick();
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

    public void __tickHud() { hudApi.__tick(); }
    public CameraState getCameraState() { return cameraState; }
    public AssetManager getAssets() { return assets; }
    public SimpleApplication getApp() { return app; }
    public ScriptEventBus getBus() { return bus; }
    public EcsWorld getEcs() { return ecs; }
    public Logger getLog() { return log; }
}