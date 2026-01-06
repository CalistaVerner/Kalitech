// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.ScriptJobQueue;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.world.WorldAppState;
import org.graalvm.polyglot.HostAccess;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SystemContext
 *
 * Shared execution context passed to every {@link KSystem}.
 * Wraps engine APIs, ECS, event bus, runtime access and production profiling domains.
 */
public final class SystemContext {

    private final SimpleApplication app;
    private final AssetManager assets;
    private final ScriptEventBus events;
    private final EcsWorld ecs;

    private final WorldAppState worldAppState;
    private final PhysicsSpace physicsSpace;

    private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();

    // JS-visible (stable handles)
    @HostAccess.Export public final EngineApi api;
    @HostAccess.Export public final EngineDomain engine;
    @HostAccess.Export public final WorldDomain world;
    @HostAccess.Export public final RenderDomain render;
    @HostAccess.Export public final StateDomain stateDomain;
    @HostAccess.Export public final PerfDomain perfDomain;

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
        this.perfDomain = new PerfDomain(worldAppState);
    }

    // ---------------------------------------------------------------------
    // Java-only helpers
    // ---------------------------------------------------------------------

    public SimpleApplication app() { return app; }
    AssetManager assets() { return assets; }
    ScriptEventBus events() { return events; }
    public EcsWorld ecs() { return ecs; }
    public PhysicsSpace getPhysicsSpace() { return physicsSpace; }

    // Runtime access (thread-confined; use correct profile on the correct thread)
    ScriptRuntime runtime() { return worldAppState.getRuntime(); }
    ScriptRuntime runtime(String profile) { return worldAppState.getRuntime(profile); }

    // Worker scheduler
    public SystemScheduler scheduler() { return worldAppState.getScheduler(); }

    // Policy (providers enforce contract decisions)
    public WorldAppState.RuntimePolicy runtimePolicy() { return worldAppState.getRuntimePolicy(); }

    // Main apply queue (budgeted)
    MainThreadBudgetQueue mainQueue() { return worldAppState.getMainQueue(); }

    // ---------------------------------------------------------------------
    // JS-facing helpers
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public ScriptJobQueue jobs() { return runtime().jobs(); }

    /** Monotonic time source for scripts/tools (nanos). */
    @HostAccess.Export
    public long nowNanos() { return System.nanoTime(); }

    /** True if the current thread is the world/main thread. */
    @HostAccess.Export
    public boolean isWorldThread() { return worldAppState.isWorldThread(); }

    // -------------------- Perf / profiling (JS-visible) --------------------

    /**
     * Usage from JS:
     *  - const fs = ctx.perf().frame();
     *  - const w  = ctx.perf().workers();
     *  - ctx.perf().dump();
     */
    @HostAccess.Export public PerfDomain perf() { return perfDomain; }

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
        public Object set(String key, Object value) {
            if (key == null) return null;
            return map.put(normKey(key), value);
        }

        @HostAccess.Export public Object get(String key) { return map.get(normKey(key)); }
        @HostAccess.Export public boolean has(String key) { return map.containsKey(normKey(key)); }
        @HostAccess.Export public Object remove(String key) { return map.remove(normKey(key)); }
        @HostAccess.Export public void clear() { map.clear(); }

        private static String normKey(String key) {
            String k = (key == null) ? "" : key.trim();
            return k.isEmpty() ? "" : k;
        }
    }

    public static final class PerfDomain {
        private final WorldAppState world;
        PerfDomain(WorldAppState world) { this.world = Objects.requireNonNull(world, "world"); }

        @HostAccess.Export public FrameStats frame() { return world.getLastFrameStats(); }
        @HostAccess.Export public WorkerSystemStats[] workers() { return world.getWorkerStatsSnapshot(); }

        @HostAccess.Export public void dump() { world.dumpPerfSnapshotToLog(); }
        @HostAccess.Export public void targetFps(int fps) { world.setTargetFps(fps); }

        @HostAccess.Export public void workerLogEverySeconds(int sec) { world.setStatsLogEverySeconds(sec); }
        @HostAccess.Export public void frameLogEverySeconds(int sec) { world.setFrameOverBudgetLogEverySeconds(sec); }
    }
}