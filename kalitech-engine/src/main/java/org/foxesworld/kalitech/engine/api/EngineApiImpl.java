package org.foxesworld.kalitech.engine.api;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.KalitechApplication;
import org.foxesworld.kalitech.engine.api.impl.*;
import org.foxesworld.kalitech.engine.api.interfaces.*;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Objects;

public final class EngineApiImpl implements EngineApi {

    private static final Logger log = LogManager.getLogger(EngineApiImpl.class);

    private final SimpleApplication app;
    private final AssetManager assets;
    private final ScriptEventBus bus;
    private final EcsWorld ecs;

    private final org.foxesworld.kalitech.engine.api.impl.CameraState cameraState;
    private final LogApi logApi;
    private final AssetsApi assetsApi;
    private final EventsApi eventsApi;
    private final EntityApi entityApi;
    private final RenderApi renderApi;
    private final CameraApi cameraApi;
    private final TimeApiImpl timeApi;
    private final InputApiImpl inputApi;
    private final WorldApi worldApi;

    private final EditorApi editorApi;

    public EngineApiImpl(SimpleApplication app, AssetManager assets, ScriptEventBus bus, EcsWorld ecs) {
        this.app = Objects.requireNonNull(app, "app");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.ecs = Objects.requireNonNull(ecs, "ecs");

        this.logApi = new LogApiImpl(log);
        this.assetsApi = new AssetsApiImpl(assets);
        this.eventsApi = new EventsApiImpl(bus);
        this.entityApi = new EntityApiImpl(ecs);
        this.renderApi = new RenderApiImpl(app, assets);
        this.cameraState = new org.foxesworld.kalitech.engine.api.impl.CameraState();
        this.cameraApi = new CameraApiImpl(app, cameraState);
        this.timeApi = new TimeApiImpl();
        this.inputApi = new InputApiImpl(app.getInputManager());
        this.worldApi = new WorldApiImpl(ecs, bus);

        this.editorApi = new EditorApiImpl(app);
    }

    @HostAccess.Export @Override public LogApi log() { return logApi; }
    @HostAccess.Export @Override public AssetsApi assets() { return assetsApi; }
    @HostAccess.Export @Override public EventsApi events() { return eventsApi; }
    @HostAccess.Export @Override public EntityApi entity() { return entityApi; }
    @HostAccess.Export @Override public RenderApi render() { return renderApi; }
    @HostAccess.Export @Override public CameraApi camera() { return cameraApi; }
    @HostAccess.Export @Override public String engineVersion() { return ((KalitechApplication) app).getVersion(); }
    @HostAccess.Export @Override public TimeApi time() { return timeApi; }
    @HostAccess.Export @Override public InputApi input() { return inputApi; }
    @HostAccess.Export @Override public WorldApi world() { return worldApi; }
    @HostAccess.Export @Override public EditorApi editor() { return editorApi; }

    // internal hooks
    public void __updateTime(double tpf) { timeApi.update(tpf); }
    public void __endFrameInput() { inputApi.endFrame(); }

    /** internal: used by RuntimeAppState / WorldBuilder based on world.mode */
    public void __setEditorMode(boolean enabled) {
        try {
            editorApi.setEnabled(enabled);
        } catch (Throwable t) {
            log.error("__setEditorMode failed", t);
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

    public CameraState getCameraState() {
        return cameraState;
    }
}