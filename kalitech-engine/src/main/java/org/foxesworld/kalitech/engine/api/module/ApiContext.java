package org.foxesworld.kalitech.engine.api.module;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.registry.TaskRegistry;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Objects;


public final class ApiContext {

    public final EngineApiImpl engine;
    public final SimpleApplication app;
    public final AssetManager assets;
    public final ScriptRuntime runtime;
    public final ScriptEventBus bus; // may be null
    public final EcsWorld ecs;
    public final Logger log;
    public final TaskRegistry tasks;

    public ApiContext(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = engine.getApp();
        this.assets = engine.getAssets();
        this.runtime = engine.getRuntime();
        this.bus = engine.getBus();
        this.ecs = engine.getEcs();
        this.log = engine.getLog();
        this.tasks = engine.getTaskRegistry();
    }
}
