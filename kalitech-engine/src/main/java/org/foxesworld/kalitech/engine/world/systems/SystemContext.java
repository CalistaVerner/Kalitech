package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.script.ScriptJobQueue;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.world.WorldAppState;
import org.graalvm.polyglot.HostAccess;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
    //private final PhysicsAccess physicsAccess;
    private final PhysicsSpace physicsSpace;

    /**
     * JS-visible per-system state storage.
     * IMPORTANT: don't rely on arbitrary ctx._field writes (host objects are strict).
     * Use ctx.state().set/get (or ctx.put/get) instead.
     */
    private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();

    /**
     * Legacy stable API surface (kept).
     */
    @HostAccess.Export
    public final EngineApi api;

    /**
     * JS-first domains (engine/world/render).
     */
    @HostAccess.Export
    public final EngineDomain engine;

    @HostAccess.Export
    public final WorldDomain world;

    @HostAccess.Export
    public final RenderDomain render;

    /**
     * New: JS-safe state access domain.
     */
    @HostAccess.Export
    public final StateDomain stateDomain;

    public SystemContext(SimpleApplication app, WorldAppState worldAppState) {

        this.app = Objects.requireNonNull(app, "app");
        this.assets = app.getAssetManager();
        this.events = worldAppState.getBus();
        this.ecs = worldAppState.getEcs();
        this.runtime = worldAppState.getRuntime();
        this.api = worldAppState.getApi();
        this.physicsSpace = worldAppState.getPhysicsSpace();

        // domains are stable singletons bound to this ctx
        this.engine = new EngineDomain(api);
        this.world = new WorldDomain(ecs, events);
        this.render = new RenderDomain(api);

        this.stateDomain = new StateDomain(state);
        //physicsAccess = new BulletPhysicsAccess(worldAppState.getPhysicsSpace());
    }

    // Java-only (package-private)
    public SimpleApplication app() {
        return app;
    }

    //public PhysicsAccess physicsAccess() {
    //    return physicsAccess;
    //}

    AssetManager assets() {
        return assets;
    }

    ScriptEventBus events() {
        return events;
    }

    public EcsWorld ecs() {
        return ecs;
    }

    GraalScriptRuntime runtime() {
        return runtime;
    }

    /**
     * Diamond layer bridge:
     * Expose job queue to scripts (optional). This does NOT allow running jobs from JS;
     * jobs are executed when Java drains runtime.drainJobs(...) on owner thread.
     */
    @HostAccess.Export
    public ScriptJobQueue jobs() {
        return runtime.jobs();
    }

    // ---------------------------------------
    // JS State (recommended way to store stuff)
    // ---------------------------------------

    /**
     * Preferred: ctx.state().set/get/...
     */
    @HostAccess.Export
    public StateDomain state() {
        return stateDomain;
    }

    /**
     * Shortcuts: ctx.put("k", v), ctx.get("k"), ctx.remove("k")
     */
    @HostAccess.Export
    public void put(String key, Object value) {
        stateDomain.set(key, value);
    }

    @HostAccess.Export
    public Object get(String key) {
        return stateDomain.get(key);
    }

    @HostAccess.Export
    public Object remove(String key) {
        return stateDomain.remove(key);
    }

    @HostAccess.Export
    public boolean has(String key) {
        return stateDomain.has(key);
    }

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }
// ------------------------------
    // Domains (small, stable, JS-safe)
    // ------------------------------

    public static final class EngineDomain {
        private final EngineApi api;

        EngineDomain(EngineApi api) {
            this.api = api;
        }

        @HostAccess.Export
        public EngineApi api() {
            return api;
        } // escape hatch
        // later: time(), config(), editorToggle(), etc.
    }

    public static final class WorldDomain {
        private final EcsWorld ecs;
        private final ScriptEventBus events;

        WorldDomain(EcsWorld ecs, ScriptEventBus events) {
            this.ecs = ecs;
            this.events = events;
        }

        @HostAccess.Export
        public void emit(String name, Object payload) {
            events.emit(name, payload);
        }

        @HostAccess.Export
        public EcsWorld ecs() {
            return ecs;
        } // temporary escape hatch
        // later: spawn(), query(), tags(), prefabs()
    }

    public static final class RenderDomain {
        private final EngineApi api;

        RenderDomain(EngineApi api) {
            this.api = api;
        }

        // later: ambient({}), sun({}), fog({}), skybox({})
        @HostAccess.Export
        public EngineApi api() {
            return api;
        } // temporary
    }

    /**
     * JS-safe state storage wrapper.
     * Avoid exposing raw Map to JS — keep it methods-only.
     */
    public static final class StateDomain {
        private final ConcurrentHashMap<String, Object> map;

        StateDomain(ConcurrentHashMap<String, Object> map) {
            this.map = Objects.requireNonNull(map, "map");
        }

        @HostAccess.Export
        public void set(String key, Object value) {
            String k = normKey(key);
            if (value == null) map.remove(k);
            else map.put(k, value);
        }

        @HostAccess.Export
        public Object get(String key) {
            return map.get(normKey(key));
        }

        @HostAccess.Export
        public boolean has(String key) {
            return map.containsKey(normKey(key));
        }

        @HostAccess.Export
        public Object remove(String key) {
            return map.remove(normKey(key));
        }

        @HostAccess.Export
        public void clear() {
            map.clear();
        }

        private static String normKey(String key) {
            if (key == null) throw new IllegalArgumentException("state key is null");
            String k = key.trim();
            if (k.isEmpty()) throw new IllegalArgumentException("state key is empty");
            return k;
        }
    }
}