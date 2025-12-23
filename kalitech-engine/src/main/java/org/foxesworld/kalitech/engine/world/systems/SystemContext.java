package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

public final class SystemContext {

    private final SimpleApplication app;
    private final AssetManager assets;
    private final ScriptEventBus events;

    public SystemContext(SimpleApplication app, AssetManager assets, ScriptEventBus events) {
        this.app = app;
        this.assets = assets;
        this.events = events;
    }

    public SimpleApplication app() { return app; }
    public AssetManager assets() { return assets; }
    public ScriptEventBus events() { return events; }
}