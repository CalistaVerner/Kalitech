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

public final class SystemContext {

    private final SimpleApplication app;
    private final AssetManager assets;
    private final ScriptEventBus events;
    private final EcsWorld ecs;

    private final WorldAppState worldAppState;
    private final PhysicsSpace physicsSpace;

    private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();

    @HostAccess.Export public final EngineApi api;
    @HostAccess.Export public final EngineDomain engine;
    @HostAccess.Export public final WorldDomain world;
    @HostAccess.Export public final RenderDomain render;
    @HostAccess.Export public final StateDomain stateDomain;

    public SystemContext(SimpleApplication app, WorldAppState worldAppState) {
        this.app = Objects.requireNonNull(app, "app");
        this.worldAppState = Objects.requireNonNull(worldAppState, "worldAppState");

        this.assets = app.getAssetManager();
        this.events = worldAppState.getBus();
        this.ecs = worldAppState.getEcs();
        this.api = worldAppState.getApi();
        this.physicsSpace = worldAppState.getPhysicsSpace();

        this.engine = new EngineDomain(api);
        this.world = new WorldDomain(ecs, events);
        this.render = new RenderDomain(api);
        this.stateDomain = new StateDomain(state);
    }

    // Java-only
    public SimpleApplication app() { return app; }
    AssetManager assets() { return assets; }
    ScriptEventBus events() { return events; }
    public EcsWorld ecs() { return ecs; }
    public PhysicsSpace getPhysicsSpace() { return physicsSpace; }

    // CDPR: runtime access
    GraalScriptRuntime runtime() { return worldAppState.getRuntime(); }
    GraalScriptRuntime runtime(String profile) { return worldAppState.getRuntime(profile); }

    // CDPR: allow providers to enforce contract decisions
    public WorldAppState.RuntimePolicy runtimePolicy() { return worldAppState.getRuntimePolicy(); }

    @HostAccess.Export
    public ScriptJobQueue jobs() {
        return runtime().jobs();
    }

    // -------------------- JS state --------------------

    @HostAccess.Export public StateDomain state() { return stateDomain; }
    @HostAccess.Export public void put(String key, Object value) { stateDomain.set(key, value); }
    @HostAccess.Export public Object get(String key) { return stateDomain.get(key); }
    @HostAccess.Export public Object remove(String key) { return stateDomain.remove(key); }
    @HostAccess.Export public boolean has(String key) { return stateDomain.has(key); }

    // -------------------- Domains --------------------

    public static final class EngineDomain {
        private final EngineApi api;
        EngineDomain(EngineApi api) { this.api = api; }
        @HostAccess.Export public EngineApi api() { return api; }
    }

    public static final class WorldDomain {
        private final EcsWorld ecs;
        private final ScriptEventBus events;
        WorldDomain(EcsWorld ecs, ScriptEventBus events) { this.ecs = ecs; this.events = events; }
        @HostAccess.Export public void emit(String name, Object payload) { events.emit(name, payload); }
        @HostAccess.Export public EcsWorld ecs() { return ecs; }
    }

    public static final class RenderDomain {
        private final EngineApi api;
        RenderDomain(EngineApi api) { this.api = api; }
        @HostAccess.Export public EngineApi api() { return api; }
    }

    public static final class StateDomain {
        private final ConcurrentHashMap<String, Object> map;
        StateDomain(ConcurrentHashMap<String, Object> map) { this.map = Objects.requireNonNull(map, "map"); }

        @HostAccess.Export
        public void set(String key, Object value) {
            String k = normKey(key);
            if (value == null) map.remove(k);
            else map.put(k, value);
        }

        @HostAccess.Export public Object get(String key) { return map.get(normKey(key)); }
        @HostAccess.Export public boolean has(String key) { return map.containsKey(normKey(key)); }
        @HostAccess.Export public Object remove(String key) { return map.remove(normKey(key)); }
        @HostAccess.Export public void clear() { map.clear(); }

        private static String normKey(String key) {
            if (key == null) throw new IllegalArgumentException("state key is null");
            String k = key.trim();
            if (k.isEmpty()) throw new IllegalArgumentException("state key is empty");
            return k;
        }
    }
}