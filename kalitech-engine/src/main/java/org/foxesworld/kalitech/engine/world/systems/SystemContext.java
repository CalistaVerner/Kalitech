package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Objects;

/**
 * Stable runtime context passed to JS.
 * Java is skeleton: ctx stays stable, worlds and systems can swap under it.
 */
public final class SystemContext {

    private final SimpleApplication app;
    private final AssetManager assets;
    private final ScriptEventBus events;
    private final EcsWorld ecs;
    private final GraalScriptRuntime runtime;

    /** Legacy stable API surface (kept). */
    @HostAccess.Export
    public final EngineApi api;

    /** JS-first domains (engine/world/render). */
    @HostAccess.Export
    public final EngineDomain engine;

    @HostAccess.Export
    public final WorldDomain world;

    @HostAccess.Export
    public final RenderDomain render;

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

        // domains are stable singletons bound to this ctx
        this.engine = new EngineDomain(api);
        this.world = new WorldDomain(ecs, events);
        this.render = new RenderDomain(api);
    }

    // Java-only (package-private)
    SimpleApplication app() { return app; }
    AssetManager assets() { return assets; }
    ScriptEventBus events() { return events; }
    public EcsWorld ecs() { return ecs; }
    GraalScriptRuntime runtime() { return runtime; }

    /**
     * Diamond layer bridge:
     * Expose job queue to scripts (optional). This does NOT allow running jobs from JS;
     * jobs are executed when Java drains runtime.drainJobs(...) on owner thread.
     */
    @HostAccess.Export
    public GraalScriptRuntime.ScriptJobQueue jobs() {
        return runtime.jobs();
    }

    // ------------------------------
    // Domains (small, stable, JS-safe)
    // ------------------------------

    public static final class EngineDomain {
        private final EngineApi api;
        EngineDomain(EngineApi api) { this.api = api; }

        @HostAccess.Export public EngineApi api() { return api; } // escape hatch
        // тут позже: time(), config(), editorToggle(), etc.
    }

    public static final class WorldDomain {
        private final EcsWorld ecs;
        private final ScriptEventBus events;
        WorldDomain(EcsWorld ecs, ScriptEventBus events) { this.ecs = ecs; this.events = events; }

        @HostAccess.Export public void emit(String name, Object payload) { events.emit(name, payload); }
        @HostAccess.Export public EcsWorld ecs() { return ecs; } // временно как escape hatch
        // тут позже: spawn(), query(), tags(), prefabs()
    }

    public static final class RenderDomain {
        private final EngineApi api;
        RenderDomain(EngineApi api) { this.api = api; }

        // тут позже: ambient({}), sun({}), fog({}), skybox({})
        @HostAccess.Export public EngineApi api() { return api; } // временно
    }
}