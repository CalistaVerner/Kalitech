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
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.jobs.ScriptJobQueue;
import org.foxesworld.kalitech.engine.world.HotReloadHub;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SystemContext
 *
 * Universal execution context for systems and app scripts.
 * World subsystem is optional.
 *
 * <p>Key guarantees:</p>
 * <ul>
 *   <li>Runtime policy is always non-null (fallback policy is installed if none provided).</li>
 *   <li>Errors are observable (no silent swallow in core domains).</li>
 * </ul>
 */
public final class SystemContext {

    private static final Logger FALLBACK_LOG = LogManager.getLogger(SystemContext.class);

    // ---------------- JS-visible stable domains ----------------

    @HostAccess.Export public final EngineDomain engine;
    @HostAccess.Export
    public final WorldDomain world;
    @HostAccess.Export
    public final RenderDomain render;
    @HostAccess.Export
    public final StateDomain stateDomain;
    @HostAccess.Export
    public final PerfDomain perfDomain;
    @HostAccess.Export
    public final HotReloadDomain hotReloadDomain;

    // ---------------- Core environment ----------------

    private final SimpleApplication app;
    private final AssetManager assets;
    private final EngineApi api;
    private final Logger log;

    private final ScriptEventBus events;      // nullable
    private final EcsWorld ecs;               // nullable
    private final PhysicsSpace physicsSpace;  // nullable

    private final ScriptRuntime runtime;             // nullable
    private final RuntimeProvider runtimeProvider;   // nullable
    private final RuntimePolicy runtimePolicy;       // never null (fallback installed)

    private final SystemScheduler scheduler;         // nullable (world-only)
    private final MainThreadBudgetQueue mainQueue;   // nullable (world-only)
    private final PerfProvider perfProvider;         // nullable (world-only)

    // ---------------- Hot Reload ----------------

    private final HotReloadHub hotReloadHub;

    private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();

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
        this.runtimePolicy = (runtimePolicy != null) ? runtimePolicy : RuntimePolicies.defaultPolicy(this.log);

        this.scheduler = scheduler;
        this.mainQueue = mainQueue;
        this.perfProvider = perfProvider;

        this.hotReloadHub = new HotReloadHub();

        this.engine = new EngineDomain(this.api);
        this.world = new WorldDomain(this.ecs, this.events, this.log);
        this.render = new RenderDomain(this.api);
        this.stateDomain = new StateDomain(this.state);
        this.perfDomain = new PerfDomain(this.perfProvider, this.log);
        this.hotReloadDomain = new HotReloadDomain(this.hotReloadHub, this.log);
    }

    // ---------------- Accessors ----------------

    public SimpleApplication app() {
        return app;
    }

    public AssetManager assets() {
        return assets;
    }

    public EngineApi api() {
        return api;
    }

    public Logger log() {
        return log;
    }

    public ScriptEventBus events() {
        return events;
    }

    public EcsWorld ecs() {
        return ecs;
    }

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
     * If no provider is installed, returns {@link #runtime()}.
     */
    public ScriptRuntime runtime(String profile) {
        if (runtimeProvider == null) return runtime;
        String p = (profile == null) ? "" : profile.trim();
        if (p.isEmpty()) return runtime;
        return runtimeProvider.runtime(p);
    }

    /**
     * Runtime policy is never null (fallback policy installed).
     */
    public RuntimePolicy runtimePolicy() {
        return runtimePolicy;
    }

    public SystemScheduler scheduler() {
        return scheduler;
    }

    MainThreadBudgetQueue mainQueue() {
        return mainQueue;
    }

    public PerfProvider perfProvider() {
        return perfProvider;
    }

    public HotReloadHub hotReloadHub() {
        return hotReloadHub;
    }

    // ---------------- JS helpers ----------------

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

    @HostAccess.Export
    public boolean has(String key) {
        return stateDomain.has(key);
    }

    @HostAccess.Export public PerfDomain perf() { return perfDomain; }
    @HostAccess.Export public StateDomain state() { return stateDomain; }

    @HostAccess.Export
    public HotReloadDomain hotReload() {
        return hotReloadDomain;
    }

    @HostAccess.Export public void put(String key, Object value) { stateDomain.set(key, value); }
    @HostAccess.Export public Object get(String key) { return stateDomain.get(key); }
    @HostAccess.Export public Object remove(String key) { return stateDomain.remove(key); }

    // ---------------- Optional extension points ----------------

    public interface RuntimeProvider {
        ScriptRuntime runtime(String profile);
    }

    /**
     * Mandatory policy in practice. A fallback is installed when constructor param is null.
     */
    public interface RuntimePolicy {

        /**
         * Enforce capability for a system in a given runtime profile.
         *
         * @param profile    runtime profile/lane (may be empty)
         * @param systemId   system id/name
         * @param capability requested capability
         * @throws SecurityException if denied
         */
        void assertAllowed(String profile, String systemId, Capability capability);

        enum Capability {
            MAIN_THREAD,
            IO,
            WORLD_ACCESS,
            UNSAFE
        }
    }

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

    private static final class RuntimePolicies {

        private RuntimePolicies() {
        }

        static RuntimePolicy defaultPolicy(Logger log) {
            return new DefaultRuntimePolicy(log);
        }

        private static final class DefaultRuntimePolicy implements RuntimePolicy {
            private final Logger log;

            DefaultRuntimePolicy(Logger log) {
                this.log = (log != null) ? log : FALLBACK_LOG;
            }

            @Override
            public void assertAllowed(String profile, String systemId, Capability capability) {
                Objects.requireNonNull(capability, "capability");
                String p = (profile == null) ? "" : profile.trim();
                String s = (systemId == null) ? "" : systemId.trim();

                // Default stance: deny UNSAFE, allow others.
                if (capability == Capability.UNSAFE) {
                    log.warn("[RuntimePolicy] denied capability={} profile='{}' system='{}'", capability, p, s);
                    throw new SecurityException("Denied capability=" + capability + " for system=" + s + " profile=" + p);
                }
            }
        }
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
        private final Logger log;

        WorldDomain(EcsWorld ecs, ScriptEventBus events, Logger log) {
            this.ecs = ecs;
            this.events = events;
            this.log = (log != null) ? log : FALLBACK_LOG;
        }

        @HostAccess.Export
        public void emit(String name, Object payload) {
            if (events == null) return;
            try {
                events.emit(name, payload);
            } catch (Throwable t) {
                log.error("[WorldDomain] emit failed: {}", name, t);
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

        StateDomain(ConcurrentHashMap<String, Object> map) {
            this.map = Objects.requireNonNull(map, "map");
        }

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
        private final Logger log;

        PerfDomain(PerfProvider perf, Logger log) {
            this.perf = perf;
            this.log = (log != null) ? log : FALLBACK_LOG;
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
            if (perf == null) return;
            try {
                perf.dumpPerfSnapshotToLog();
            } catch (Throwable t) {
                log.error("[PerfDomain] dump failed", t);
            }
        }

        @HostAccess.Export
        public void targetFps(int fps) {
            if (perf == null) return;
            try {
                perf.setTargetFps(fps);
            } catch (Throwable t) {
                log.error("[PerfDomain] targetFps failed: fps={}", fps, t);
            }
        }

        @HostAccess.Export
        public void workerLogEverySeconds(int sec) {
            if (perf == null) return;
            try {
                perf.setStatsLogEverySeconds(sec);
            } catch (Throwable t) {
                log.error("[PerfDomain] workerLogEverySeconds failed: sec={}", sec, t);
            }
        }

        @HostAccess.Export
        public void frameLogEverySeconds(int sec) {
            if (perf == null) return;
            try {
                perf.setFrameOverBudgetLogEverySeconds(sec);
            } catch (Throwable t) {
                log.error("[PerfDomain] frameLogEverySeconds failed: sec={}", sec, t);
            }
        }
    }

    // ---------------- Hot Reload ----------------

    public static final class HotReloadDomain {
        private final HotReloadHub hub;
        private final Logger log;

        HotReloadDomain(HotReloadHub hub, Logger log) {
            this.hub = Objects.requireNonNull(hub, "hub");
            this.log = (log != null) ? log : FALLBACK_LOG;
        }

        /**
         * Register a JS hook: fn(reason)
         *
         * @param fn callback
         */
        @HostAccess.Export
        public void register(Value fn) {
            if (fn == null || !fn.canExecute()) return;
            hub.register(reason -> {
                try {
                    fn.execute(reason);
                } catch (Throwable t) {
                    log.error("[HotReloadDomain] callback failed: reason={}", reason, t);
                }
            });
        }

        @HostAccess.Export
        public void fire(String reason) {
            try {
                hub.fire(reason);
            } catch (Throwable t) {
                log.error("[HotReloadDomain] fire failed: reason={}", reason, t);
            }
        }

        @HostAccess.Export
        public int size() {
            return hub.size();
        }

        @HostAccess.Export
        public void clear() {
            try {
                hub.clear();
            } catch (Throwable t) {
                log.error("[HotReloadDomain] clear failed", t);
            }
        }
    }
}