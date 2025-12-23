package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Objects;

public final class SystemContext {

    private final SimpleApplication app;
    private final AssetManager assets;
    private final ScriptEventBus events;
    private final EcsWorld ecs;
    private final GraalScriptRuntime runtime;

    /**
     * JS-stable API surface: ctx.api
     * MUST be a field/property (not a method), so JS can do ctx.api.log().info(...)
     */
    @HostAccess.Export
    public final EngineApi api;

    public SystemContext(SimpleApplication app,
                         AssetManager assets,
                         ScriptEventBus events,
                         EcsWorld ecs,
                         GraalScriptRuntime runtime,
                         EngineApi api) {

        this.app = Objects.requireNonNull(app, "app");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.events = Objects.requireNonNull(events, "events");
        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.api = Objects.requireNonNull(api, "api");
    }

    // Java-only (package-private)
    SimpleApplication app() { return app; }
    AssetManager assets() { return assets; }
    ScriptEventBus events() { return events; }
    public EcsWorld ecs() { return ecs; }
    GraalScriptRuntime runtime() { return runtime; }
}