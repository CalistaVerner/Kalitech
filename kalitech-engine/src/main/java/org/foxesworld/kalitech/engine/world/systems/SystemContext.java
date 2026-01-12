// FILE: org/foxesworld/kalitech/engine/world/systems/SystemContext.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.ScriptJobQueue;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.world.HotReloadHub;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SystemContext
 *
 * Universal execution context for systems and app scripts.
 * World subsystem is OPTIONAL.
 */
public final class SystemContext {

    private static final Logger FALLBACK_LOG = LogManager.getLogger(SystemContext.class);

    // ---------------- Optional extension points ----------------
    // JS-visible stable domains
    @HostAccess.Export public final EngineDomain engine;
    private final EngineApi api;
    private final Logger log;

    // ---------------- Core environment ----------------

    private final SimpleApplication app;
    private final AssetManager assets;
    private final ScriptEventBus events;      // nullable
    private final EcsWorld ecs;               // nullable
    private final PhysicsSpace physicsSpace;  // nullable
    private final ScriptRuntime runtime;      // nullable (app-only may still have it)
    private final RuntimeProvider runtimeProvider; // nullable
    private final RuntimePolicy runtimePolicy;     // nullable
    private final SystemScheduler scheduler;  // nullable (world-only)
    private final MainThreadBudgetQueue mainQueue; // nullable (world-only)
    private final PerfProvider perfProvider;  // nullable (world-only)

    // NEW: JS-visible hot reload domain
    @HostAccess.Export
    public final HotReloadDomain hotReloadDomain;
    // ---------------- Hot Reload ----------------
    private final HotReloadHub hotReloadHub;

    public SimpleApplication app() {
        return app;
    }

    private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();

    public AssetManager assets() {
        return assets;
    }

    @HostAccess.Export public final WorldDomain world;
    @HostAccess.Export public final RenderDomain render;
    @HostAccess.Export public final StateDomain stateDomain;
    @HostAccess.Export public final PerfDomain perfDomain;

    public SystemContext(
            SimpleApplication app,
            EngineApi api,
            EcsWorld ecs,
            ScriptEventBus events,
            PhysicsSpace physicsSpace,
            ScriptRuntime runtime,
            RuntimeProvider runtimeProvider,
            RuntimePolicy runtimePolicy,
            SystemScheduler scheduler,
            MainThreadBudgetQueue mainQueue,
            PerfProvider perfProvider,
            Logger log
    ) {
        this.app = Objects.requireNonNull(app, "app");
        this.assets = app.getAssetManager();
        this.api = Objects.requireNonNull(api, "api");
        this.log = (log != null) ? log : FALLBACK_LOG;

        this.ecs = ecs;
        this.events = events;
        this.physicsSpace = physicsSpace;

        this.runtime = runtime;
        this.runtimeProvider = runtimeProvider;
        this.runtimePolicy = runtimePolicy;

        this.scheduler = scheduler;
        this.mainQueue = mainQueue;
        this.perfProvider = perfProvider;

        // NEW: always present, even outside "world"
        this.hotReloadHub = new HotReloadHub();

        this.engine = new EngineDomain(this.api);
        this.world = new WorldDomain(this.ecs, this.events);
        this.render = new RenderDomain(this.api);
        this.stateDomain = new StateDomain(this.state);
        this.perfDomain = new PerfDomain(this.perfProvider);

        // NEW: JS-visible domain
        this.hotReloadDomain = new HotReloadDomain(this.hotReloadHub);
    }

    public EngineApi api() {
        return api;
    }

    // ---------------- Java helpers ----------------

    public Logger log() {
        return log;
    }

    public ScriptEventBus events() { return events; }

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }

    /**
     * Base runtime (may be null).
     */
    public ScriptRuntime runtime() {
        return runtime;
    }

    /**
     * Runtime by profile (optional).
     * If no provider is installed -> returns base runtime().
     */
    public ScriptRuntime runtime(String profile) {
        if (runtimeProvider == null) return runtime;
        String p = (profile == null) ? "" : profile.trim();
        if (p.isEmpty()) return runtime;
        return runtimeProvider.runtime(p);
    }

    public EcsWorld ecs() { return ecs; }

    /**
     * Optional policy (may be null).
     */
    public RuntimePolicy runtimePolicy() {
        return runtimePolicy;
    }

    /**
     * Optional scheduler (may be null).
     */
    public SystemScheduler scheduler() {
        return scheduler;
    }

    /** Optional main-thread queue (may be null). */
    MainThreadBudgetQueue mainQueue() {
        return mainQueue;
    }

    /**
     * Optional perf provider (may be null).
     */
    public PerfProvider perfProvider() {
        return perfProvider;
    }

    // NEW: Java-side access to hub
    public HotReloadHub hotReloadHub() {
        return hotReloadHub;
    }

    @HostAccess.Export
    public ScriptJobQueue jobs() {
        ScriptRuntime rt = runtime();
        return (rt != null) ? rt.jobs() : null;
    }

    @HostAccess.Export
    public long nowNanos() {
        return System.nanoTime();
    }

    @HostAccess.Export
    public boolean isWorldThread() {
        return perfProvider != null && perfProvider.isWorldThread();
    }

    // ---------------- JS helpers ----------------

    @HostAccess.Export
    public boolean has(String key) { return stateDomain.has(key); }

    /**
     * Optional runtime pool/provider (for worker lanes / profiles).
     */
    public interface RuntimeProvider {
        ScriptRuntime runtime(String profile);
    }

    /**
     * Optional policy to resolve/deny runtime profile requests (CDPR-style).
     */
    public interface RuntimePolicy {
        String resolveProfile(String requested, Origin origin);

        enum Origin {SCRIPT_CONFIG, JAVA_PROVIDER}
    }

    @HostAccess.Export public PerfDomain perf() { return perfDomain; }
    @HostAccess.Export public StateDomain state() { return stateDomain; }

    // NEW: sugar for JS
    @HostAccess.Export
    public HotReloadDomain hotReload() {
        return hotReloadDomain;
    }

    @HostAccess.Export public void put(String key, Object value) { stateDomain.set(key, value); }
    @HostAccess.Export public Object get(String key) { return stateDomain.get(key); }
    @HostAccess.Export public Object remove(String key) { return stateDomain.remove(key); }

    /**
     * Optional perf provider (world-only).
     */
    public interface PerfProvider {
        FrameStats getLastFrameStats();

        WorkerSystemStats[] getWorkerStatsSnapshot();

        void dumpPerfSnapshotToLog();

        void setTargetFps(int fps);

        void setStatsLogEverySeconds(int sec);

        void setFrameOverBudgetLogEverySeconds(int sec);

        boolean isWorldThread();
    }

    // ---------------- Domains ----------------

    public static final class EngineDomain {
        private final EngineApi api;
        EngineDomain(EngineApi api) { this.api = api; }
        @HostAccess.Export public EngineApi api() { return api; }
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
            if (events == null) return;
            try {
                events.emit(name, payload);
            } catch (Throwable ignored) {
            }
        }

        @HostAccess.Export
        public EcsWorld ecs() { return ecs; }
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
        private static final WorkerSystemStats[] EMPTY_WORKERS = new WorkerSystemStats[0];
        private final PerfProvider perf;

        PerfDomain(PerfProvider perf) {
            this.perf = perf;
        }

        @HostAccess.Export
        public FrameStats frame() {
            return (perf != null) ? perf.getLastFrameStats() : null;
        }

        @HostAccess.Export
        public WorkerSystemStats[] workers() {
            return (perf != null) ? perf.getWorkerStatsSnapshot() : EMPTY_WORKERS;
        }

        @HostAccess.Export
        public void dump() {
            if (perf != null) try {
                perf.dumpPerfSnapshotToLog();
            } catch (Throwable ignored) {
            }
        }

        @HostAccess.Export
        public void targetFps(int fps) {
            if (perf != null) try {
                perf.setTargetFps(fps);
            } catch (Throwable ignored) {
            }
        }

        @HostAccess.Export
        public void workerLogEverySeconds(int sec) {
            if (perf != null) try {
                perf.setStatsLogEverySeconds(sec);
            } catch (Throwable ignored) {
            }
        }

        @HostAccess.Export
        public void frameLogEverySeconds(int sec) {
            if (perf != null) try { perf.setFrameOverBudgetLogEverySeconds(sec); } catch (Throwable ignored) {} }
    }

    // ---------------- NEW DOMAIN: Hot Reload ----------------

    public static final class HotReloadDomain {
        private final HotReloadHub hub;

        HotReloadDomain(HotReloadHub hub) {
            this.hub = Objects.requireNonNull(hub, "hub");
        }

        /**
         * Register JS hook: fn(reason)
         */
        @HostAccess.Export
        public void register(Value fn) {
            if (fn == null || !fn.canExecute()) return;
            hub.register((reason) -> {
                try {
                    fn.execute(reason);
                } catch (Throwable ignored) {
                }
            });
        }

        @HostAccess.Export
        public void fire(String reason) {
            hub.fire(reason);
        }

        @HostAccess.Export
        public int size() {
            return hub.size();
        }

        @HostAccess.Export
        public void clear() {
            hub.clear();
        }
    }
}