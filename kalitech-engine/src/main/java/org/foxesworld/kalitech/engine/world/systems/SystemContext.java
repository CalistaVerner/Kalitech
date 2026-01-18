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
import org.foxesworld.kalitech.engine.world.WorldTime;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal execution context for systems and app scripts.
 * World subsystem is optional.
 */
public final class SystemContext {

    private static final Logger FALLBACK_LOG = LogManager.getLogger(SystemContext.class);

    @HostAccess.Export public final EngineDomain engine;
    @HostAccess.Export
    public final WorldDomain world;
    @HostAccess.Export
    public final TimeDomain time;
    @HostAccess.Export
    public final RenderDomain render;
    @HostAccess.Export
    public final StateDomain stateDomain;
    @HostAccess.Export
    public final PerfDomain perfDomain;
    @HostAccess.Export
    public final HotReloadDomain hotReloadDomain;

    private final SimpleApplication app;
    private final AssetManager assets;
    private final EngineApi api;
    private final Logger log;

    private final ScriptEventBus events;      // nullable
    private final EcsWorld ecs;               // nullable
    private final PhysicsSpace physicsSpace;  // nullable
    private final WorldTime worldTime;        // nullable

    private final ScriptRuntime runtime;           // nullable
    private final RuntimeProvider runtimeProvider; // nullable
    private final RuntimePolicy runtimePolicy;     // never null (fallback installed)

    private final SystemScheduler scheduler;       // nullable (world-only)
    private final MainThreadBudgetQueue mainQueue; // nullable (world-only)
    private final PerfProvider perfProvider;       // nullable (world-only)

    private final HotReloadHub hotReloadHub;

    private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();

    public SystemContext(
            SimpleApplication app,
            EngineApi api,
            EcsWorld ecs,
            ScriptEventBus events,
            PhysicsSpace physicsSpace,
            WorldTime worldTime,
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
        this.worldTime = worldTime;

        this.runtime = runtime;
        this.runtimeProvider = runtimeProvider;
        this.runtimePolicy = (runtimePolicy != null) ? runtimePolicy : RuntimePolicies.defaultPolicy(this.log);

        this.scheduler = scheduler;
        this.mainQueue = mainQueue;
        this.perfProvider = perfProvider;

        this.hotReloadHub = new HotReloadHub();

        this.engine = new EngineDomain(this.api);
        this.world = new WorldDomain(this.ecs, this.events, this.log);
        this.time = new TimeDomain(this.worldTime);
        this.render = new RenderDomain(this.api);
        this.stateDomain = new StateDomain(this.state);
        this.perfDomain = new PerfDomain(this.perfProvider, this.log);
        this.hotReloadDomain = new HotReloadDomain(this.hotReloadHub, this.log);
    }

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

    public WorldTime worldTime() {
        return worldTime;
    }

    public ScriptRuntime runtime() {
        return runtime;
    }

    public ScriptRuntime runtime(String profile) {
        if (runtimeProvider == null) return runtime;
        String p = (profile == null) ? "" : profile.trim();
        if (p.isEmpty()) return runtime;
        return runtimeProvider.runtime(p);
    }

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

    public interface RuntimeProvider {
        ScriptRuntime runtime(String profile);
    }

    public interface RuntimePolicy {
        void assertAllowed(String profile, String systemId, Capability capability);

        enum Capability {
            MAIN_THREAD,
            IO,
            WORLD_ACCESS,
            UNSAFE
        }
    }

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

                if (capability == Capability.UNSAFE) {
                    log.warn("[RuntimePolicy] denied capability={} profile='{}' system='{}'", capability, p, s);
                    throw new SecurityException("Denied capability=" + capability + " for system=" + s + " profile=" + p);
                }
            }
        }
    }

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

    public static final class TimeDomain {
        private final WorldTime time;

        TimeDomain(WorldTime time) {
            this.time = time;
        }

        @HostAccess.Export
        public boolean available() {
            return time != null;
        }

        @HostAccess.Export
        public double now() {
            return (time != null) ? time.getWorldTimeSec() : 0.0;
        }

        @HostAccess.Export
        public double rate() {
            return (time != null) ? time.getTimeRate() : 1.0;
        }

        @HostAccess.Export
        public boolean paused() {
            return time != null && time.isPaused();
        }

        @HostAccess.Export
        public Double fixedStepSec() {
            return (time != null) ? time.fixedStepSec() : null;
        }

        @HostAccess.Export
        public Double maxDeltaSec() {
            return (time != null) ? time.getMaxDeltaSec() : null;
        }

        @HostAccess.Export
        public double accumulatorSec() {
            return (time != null) ? time.accumulatorSec() : 0.0;
        }

        @HostAccess.Export
        public double daySeconds() {
            return (time != null) ? time.daySeconds() : 0.0;
        }

        @HostAccess.Export
        public Double dayLengthSec() {
            return (time != null) ? time.effectiveDayLengthSec() : null;
        }

        @HostAccess.Export
        public int dayIndex() {
            return (time != null) ? time.dayIndex() : 0;
        }

        @HostAccess.Export
        public double timeOfDaySec() {
            return (time != null) ? time.timeOfDaySec() : 0.0;
        }

        @HostAccess.Export
        public double timeOfDay01() {
            return (time != null) ? time.timeOfDay01() : 0.0;
        }

        @HostAccess.Export
        public int hour() {
            return (time != null) ? time.hour() : 0;
        }

        @HostAccess.Export
        public int minute() {
            return (time != null) ? time.minute() : 0;
        }

        @HostAccess.Export
        public int second() {
            return (time != null) ? time.second() : 0;
        }


        @HostAccess.Export
        public long frameIndex() {
            return (time != null) ? time.frameIndex() : 0L;
        }


        @HostAccess.Export
        public long tickIndex() {
            return (time != null) ? time.tickIndex() : 0L;
        }

        @HostAccess.Export
        public double realDtSec() {
            return (time != null) ? time.lastRealDtSec() : 0.0;
        }

        @HostAccess.Export
        public double simDtSec() {
            return (time != null) ? time.lastSimDtSec() : 0.0;
        }

        @HostAccess.Export
        public double stepDtSec() {
            return (time != null) ? time.lastStepDtSec() : 0.0;
        }

        @HostAccess.Export
        public void setRate(double rate) {
            if (time == null) return;
            time.setTimeRate(rate);
        }

        @HostAccess.Export
        public void setPaused(boolean paused) {
            if (time == null) return;
            time.setPaused(paused);
        }

        @HostAccess.Export
        public void seek(double worldTimeSec) {
            if (time == null) return;
            time.seek(worldTimeSec);
        }
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
