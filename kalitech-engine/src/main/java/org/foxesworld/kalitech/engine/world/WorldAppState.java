package org.foxesworld.kalitech.engine.world;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.bullet.PhysicsSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.app.RuntimeAppState;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.foxesworld.kalitech.engine.world.systems.*;
import org.foxesworld.kalitech.engine.world.systems.proxy.MainThreadDispatcher;
import org.foxesworld.kalitech.engine.world.systems.proxy.MainThreadProxyFactory;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * CDPR-style:
 * - 1 runtime per world by default (fast, shared cache)
 * - isolated runtimes on demand via RuntimePool + Policy
 * - worker runtimes get hard sandbox (API-level): engine/api/render are main-thread proxies
 * - per-worker stats via SystemScheduler
 * - frame budget guard (60fps baseline)
 */
public final class WorldAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(WorldAppState.class);

    private final ScriptEventBus bus;
    private final EcsWorld ecs;

    private final EngineApi api;
    private final PhysicsSpace physicsSpace;

    private final RuntimePolicy runtimePolicy;
    private final RuntimePool runtimePool;

    private HotReloadWatcher hotReload;

    private int jobDrainBudget = 256;

    // AAA perf budget (default: 60 FPS)
    private volatile int targetFps = 60;
    private volatile long frameBudgetNanos = fpsToBudgetNanos(60);
    private long frameIndex = 0L;
    private volatile FrameStats lastFrameStats;

    // overload logging (rare, but loud)
    private long lastFrameOverBudgetLogNanos = 0L;
    private long frameOverBudgetLogEveryNanos = TimeUnit.SECONDS.toNanos(1);

    private KWorld world;
    private SystemContext ctx;

    private SystemScheduler scheduler;

    // main thread + sandbox infra
    private Thread worldThread;
    private MainThreadDispatcher dispatcher;
    private MainThreadProxyFactory proxyFactory;

    // periodic stats logging
    private long lastStatsLogNanos = 0L;
    private long statsLogEveryNanos = TimeUnit.SECONDS.toNanos(2);

    private boolean running = false;
    private boolean restartRequested = false;

    private long lastPoolMaintenanceNanos = 0L;

    public WorldAppState(RuntimeAppState runtimeAppState) {
        this.bus = runtimeAppState.getBus();
        this.ecs = runtimeAppState.getEcs();

        final ScriptRuntime base = runtimeAppState.getRuntime();
        this.api = runtimeAppState.getEngineApi();
        this.physicsSpace = runtimeAppState.getSpace();

        this.runtimePolicy = RuntimePolicy.fromSystemProps();
        this.runtimePool = new RuntimePool(base, runtimePolicy);
    }

    public RuntimePolicy getRuntimePolicy() {
        return runtimePolicy;
    }

    public void setHotReloadWatcher(HotReloadWatcher watcher) {
        this.hotReload = watcher;
    }

    public void setJobDrainBudget(int budget) {
        this.jobDrainBudget = Math.max(0, budget);
    }

    public void setStatsLogEverySeconds(int sec) {
        this.statsLogEveryNanos = TimeUnit.SECONDS.toNanos(Math.max(0, sec));
    }

    /** Target FPS used by the frame budget guard (default: 60). */
    public void setTargetFps(int fps) {
        int f = Math.max(10, fps);
        this.targetFps = f;
        this.frameBudgetNanos = fpsToBudgetNanos(f);
        log.info("[perf] targetFps={} budgetMs={}", f, TimeUnit.NANOSECONDS.toMicros(frameBudgetNanos) / 1000.0);
    }

    public int getTargetFps() { return targetFps; }
    public long getFrameBudgetNanos() { return frameBudgetNanos; }

    /** How often to print over-budget frame breakdown logs (0 = never). */
    public void setFrameOverBudgetLogEverySeconds(int sec) {
        this.frameOverBudgetLogEveryNanos = TimeUnit.SECONDS.toNanos(Math.max(0, sec));
    }

    public FrameStats getLastFrameStats() { return lastFrameStats; }

    public WorkerSystemStats[] getWorkerStatsSnapshot() {
        SystemScheduler s = this.scheduler;
        return (s != null) ? s.statsSnapshot() : new WorkerSystemStats[0];
    }

    @Override
    protected void initialize(Application app) {
        if (!(app instanceof SimpleApplication sa)) {
            throw new IllegalStateException("WorldAppState requires SimpleApplication (got " + app.getClass().getName() + ")");
        }

        // world thread == app thread during initialize/update
        this.worldThread = Thread.currentThread();

        this.ctx = new SystemContext(sa, this);

        this.scheduler = new SystemScheduler(this);
        this.scheduler.setDefaultAwaitBudgetMs(2);

        // dispatcher uses WORLD jobs queue
        this.dispatcher = new MainThreadDispatcher(worldThread, getRuntime().jobs());
        this.dispatcher.setDefaultTimeoutMs(2000);

        this.proxyFactory = new MainThreadProxyFactory(dispatcher);

        // Bind globals into WORLD runtime only (main pipeline)
        installWorldGlobals(this.ctx, this.api);

        tryStartWorld();
    }

    public void setWorld(KWorld newWorld) {
        if (this.world == newWorld) return;
        tryStopWorld();
        this.world = newWorld;
        tryStartWorld();
    }

    public KWorld getWorld() { return world; }
    public SystemContext getContextForJs() { return ctx; }

    @Override
    public void update(float tpf) {
        if (!isEnabled() || ctx == null) return;

        final long frameStart = System.nanoTime();
        final long budget = frameBudgetNanos;
        final long fi = ++frameIndex;

        long t0, t1;
        long drainJobsNanos = 0L;
        long hotReloadNanos = 0L;
        long eventsNanos = 0L;
        long worldUpdateNanos = 0L;
        long awaitWorkersNanos = 0L;
        long poolMaintenanceNanos = 0L;

        // 1) Drain world ScriptJobQueue (critical for worker->main calls)
        t0 = System.nanoTime();
        try {
            if (jobDrainBudget > 0) {
                runtimePool.get("world").jobs().drain(jobDrainBudget);
            }
        } catch (NoSuchMethodError ignored) {
        } catch (Exception e) {
            log.error("Script job drain failed", e);
        } finally {
            t1 = System.nanoTime();
            drainJobsNanos = Math.max(0L, t1 - t0);
        }

        // 2) Hot reload invalidation (optional)
        t0 = System.nanoTime();
        try {
            HotReloadWatcher hr = this.hotReload;
            if (hr != null) {
                Set<String> changed = hr.pollChanged();
                if (changed != null && !changed.isEmpty()) {
                    int removed;
                    try {
                        removed = runtimePool.get("world").invalidateManyWithReason(changed, "hotReload");
                    } catch (NoSuchMethodError e) {
                        removed = runtimePool.get("world").invalidateMany(changed);
                    }

                    if (removed > 0) {
                        log.info("HotReload invalidated modules: {}", removed);
                        restartRequested = true;
                    }
                }
            }
        } catch (Exception e) {
            log.error("HotReload poll/invalidate failed", e);
        } finally {
            t1 = System.nanoTime();
            hotReloadNanos = Math.max(0L, t1 - t0);
        }

        // 3) Restart after reload
        if (restartRequested) {
            restartRequested = false;
            try {
                tryStopWorld();
                installWorldGlobals(this.ctx, this.api);
                tryStartWorld();
                log.info("World restarted after hot reload");
            } catch (Exception e) {
                log.error("World restart after hot reload failed", e);
            }
        }

        // 4) Pump events
        t0 = System.nanoTime();
        try { bus.pump(); } catch (Exception e) { log.error("Event bus pump failed", e); }
        finally {
            t1 = System.nanoTime();
            eventsNanos = Math.max(0L, t1 - t0);
        }

        // 5) Update world
        t0 = System.nanoTime();
        if (world != null && running) {
            try { world.update(ctx, tpf); } catch (Exception e) { log.error("World update failed", e); }
        }
        t1 = System.nanoTime();
        worldUpdateNanos = Math.max(0L, t1 - t0);

        // 6) Best-effort wait for worker ticks — BUT do not kill the frame.
        t0 = System.nanoTime();
        if (scheduler != null) {
            long elapsed = t0 - frameStart;
            long remaining = budget - elapsed;

            long awaitBudget = 0L;
            if (remaining > 0L) {
                long def = TimeUnit.MILLISECONDS.toNanos(scheduler.getDefaultAwaitBudgetMs());
                awaitBudget = Math.min(def, remaining);
            }
            scheduler.awaitBudgetNanos(awaitBudget);
        }
        t1 = System.nanoTime();
        awaitWorkersNanos = Math.max(0L, t1 - t0);

        // 7) RuntimePool maintenance ~ once per second
        final long now = System.nanoTime();
        t0 = now;
        if (now - lastPoolMaintenanceNanos >= TimeUnit.SECONDS.toNanos(1)) {
            lastPoolMaintenanceNanos = now;
            runtimePool.evictIdle(now);
        }
        t1 = System.nanoTime();
        poolMaintenanceNanos = Math.max(0L, t1 - t0);

        final long frameNanos = Math.max(0L, now - frameStart);
        this.lastFrameStats = new FrameStats(
                fi,
                budget,
                frameNanos,
                drainJobsNanos,
                hotReloadNanos,
                eventsNanos,
                worldUpdateNanos,
                awaitWorkersNanos,
                poolMaintenanceNanos,
                jobDrainBudget,
                dispatcher != null ? dispatcher.getCalls() : 0L,
                dispatcher != null ? dispatcher.getTimeouts() : 0L
        );

        // Over-budget frame breakdown log (rare, but loud)
        if (frameOverBudgetLogEveryNanos > 0 && frameNanos > budget && (now - lastFrameOverBudgetLogNanos) >= frameOverBudgetLogEveryNanos) {
            lastFrameOverBudgetLogNanos = now;
            logFrameOverBudget(lastFrameStats);
        }

        // Periodic worker stats logging
        if (statsLogEveryNanos > 0 && now - lastStatsLogNanos >= statsLogEveryNanos) {
            lastStatsLogNanos = now;
            logWorkerStats();
        }
    }

    private void logWorkerStats() {
        if (scheduler == null) return;
        WorkerSystemStats[] ss = scheduler.statsSnapshot();
        if (ss.length == 0) return;

        StringBuilder b = new StringBuilder(256);
        b.append("[workers] calls=").append(dispatcher != null ? dispatcher.getCalls() : 0)
                .append(" timeouts=").append(dispatcher != null ? dispatcher.getTimeouts() : 0);

        for (WorkerSystemStats s : ss) {
            b.append("\n  - ").append(s.systemName)
                    .append(" prof=").append(s.profile)
                    .append(" thr=").append(s.threadName)
                    .append(" run=").append(s.running)
                    .append(" tickMs=").append(nsToMs(s.lastTickNanos))
                    .append(" emaMs=").append(nsToMs(s.emaTickNanos))
                    .append(" maxMs=").append(nsToMs(s.maxTickNanos))
                    .append(" lagMs=").append(nsToMs(s.lastQueueLagNanos))
                    .append(" skip=").append(s.skippedTicks);
        }

        log.info(b.toString());
    }

    private void logFrameOverBudget(FrameStats fs) {
        if (fs == null) return;

        StringBuilder b = new StringBuilder(512);
        b.append("[frame] OVER BUDGET ⚠ fpsTarget=")
                .append(targetFps)
                .append(" totalMs=").append(nsToMs(fs.frameNanos))
                .append(" budgetMs=").append(nsToMs(fs.budgetNanos))
                .append(" calls=").append(fs.dispatcherCalls)
                .append(" timeouts=").append(fs.dispatcherTimeouts)
                .append(" lanes=").append(scheduler != null ? scheduler.getLaneCount() : 0);

        b.append("\n  breakdownMs:")
                .append(" jobs=").append(nsToMs(fs.drainJobsNanos))
                .append(" hotReload=").append(nsToMs(fs.hotReloadNanos))
                .append(" events=").append(nsToMs(fs.eventsNanos))
                .append(" world=").append(nsToMs(fs.worldUpdateNanos))
                .append(" awaitWorkers=").append(nsToMs(fs.awaitWorkersNanos))
                .append(" pool=").append(nsToMs(fs.poolMaintenanceNanos));

        WorkerSystemStats[] ws = (scheduler != null) ? scheduler.statsSnapshot() : new WorkerSystemStats[0];
        if (ws.length > 0) {
            Arrays.sort(ws, (a, c) -> {
                long av = (a.emaTickNanos != 0L) ? a.emaTickNanos : a.lastTickNanos;
                long cv = (c.emaTickNanos != 0L) ? c.emaTickNanos : c.lastTickNanos;
                return Long.compare(cv, av);
            });

            int top = Math.min(3, ws.length);
            b.append("\n  worstWorkers:");
            for (int i = 0; i < top; i++) {
                WorkerSystemStats s = ws[i];
                long score = (s.emaTickNanos != 0L) ? s.emaTickNanos : s.lastTickNanos;
                b.append("\n    - ").append(s.systemName)
                        .append(" prof=").append(s.profile)
                        .append(" thr=").append(s.threadName)
                        .append(" emaMs=").append(nsToMs(s.emaTickNanos))
                        .append(" tickMs=").append(nsToMs(s.lastTickNanos))
                        .append(" maxMs=").append(nsToMs(s.maxTickNanos))
                        .append(" lagMs=").append(nsToMs(s.lastQueueLagNanos))
                        .append(" skip=").append(s.skippedTicks)
                        .append(" scoreMs=").append(nsToMs(score));
            }
        }

        log.warn(b.toString());
    }

    private static long nsToMs(long ns) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, ns));
    }

    private static long fpsToBudgetNanos(int fps) {
        int f = Math.max(1, fps);
        return 1_000_000_000L / (long) f;
    }

    @Override
    protected void cleanup(Application app) {
        tryStopWorld();
        try { if (scheduler != null) scheduler.close(); } catch (Throwable ignored) {}
        scheduler = null;

        try { runtimePool.closeAll(); } catch (Throwable ignored) {}

        ctx = null;
        dispatcher = null;
        proxyFactory = null;
        worldThread = null;

        log.info("WorldAppState cleaned up");
    }

    @Override protected void onEnable() { tryStartWorld(); }
    @Override protected void onDisable() { tryStopWorld(); }

    private void tryStartWorld() {
        if (!isInitialized() || !isEnabled()) return;
        if (ctx == null || world == null) return;
        if (running) return;

        try {
            world.start(ctx);
            running = true;
        } catch (Exception e) {
            running = false;
            log.error("Failed to start world", e);
        }
    }

    private void tryStopWorld() {
        if (!isInitialized()) return;
        if (ctx == null || world == null) return;
        if (!running) return;

        try {
            world.stop(ctx);
        } catch (Exception e) {
            log.error("Failed to stop world", e);
        } finally {
            running = false;
        }
    }

    /**
     * Bind globals into WORLD runtime (main pipeline).
     */
    private void installWorldGlobals(SystemContext sysCtx, EngineApi engineApi) {
        try {
            Value bindings = runtimePool.get("world").ctx().getBindings("js");
            bindings.putMember("ctx", sysCtx);
            bindings.putMember("api", engineApi);
            bindings.putMember("engine", engineApi);

            try {
                bindings.putMember("render", engineApi.render());
            } catch (Throwable t) {
                bindings.putMember("render", engineApi);
                log.warn("Failed to bind render as EngineApi.render(); scripts may call missing render.* methods", t);
            }
        } catch (Exception e) {
            log.error("Failed to install WORLD JS globals", e);
        }
    }

    /**
     * HARD worker sandbox WITHOUT JS rewrites:
     * - ctx is available (same object)
     * - engine/api/render are MAIN-THREAD proxies (worker cannot touch engine directly)
     * - method calls from worker are marshaled via ctx.jobs() to world thread synchronously
     */
    public void installWorkerSandboxGlobals(ScriptRuntime workerRt, SystemContext sysCtx, String systemName) {
        if (workerRt == null) throw new IllegalArgumentException("workerRt is null");
        if (sysCtx == null) throw new IllegalArgumentException("sysCtx is null");

        try {
            Value bindings = workerRt.ctx().getBindings("js");

            bindings.putMember("ctx", sysCtx);

            EngineApi engineProxy = (proxyFactory != null)
                    ? proxyFactory.wrap(this.api, EngineApi.class)
                    : this.api;

            bindings.putMember("api", engineProxy);
            bindings.putMember("engine", engineProxy);

            try {
                Object renderApi = this.api.render();
                bindings.putMember("render", renderApi);
            } catch (Throwable t) {
                bindings.putMember("render", engineProxy);
            }

            bindings.putMember("__workerSystem", systemName != null ? systemName : "worker");

        } catch (Exception e) {
            log.error("Failed to install WORKER sandbox globals for {}", systemName, e);
        }
    }

    public PhysicsSpace getPhysicsSpace() { return physicsSpace; }
    public EngineApi getApi() { return api; }
    public EcsWorld getEcs() { return ecs; }
    public ScriptEventBus getBus() { return bus; }

    public ScriptRuntime getRuntime() { return runtimePool.get("world"); }
    public ScriptRuntime getRuntime(String profile) { return runtimePool.get(profile); }

    public SystemScheduler getScheduler() { return scheduler; }

    // =====================================================================
    // AAA: perf stats access (for JS overlays / live debugging)
    // =====================================================================

    /** Print a full perf snapshot even if the frame isn't over budget. */
    public void dumpPerfSnapshotToLog() {
        FrameStats fs = lastFrameStats;
        if (fs == null) {
            log.info("[perf] no frame stats yet");
            return;
        }
        StringBuilder b = new StringBuilder(512);
        b.append("[perf] frame=").append(fs.frameIndex)
                .append(" totalMs=").append(nsToMs(fs.frameNanos))
                .append(" budgetMs=").append(nsToMs(fs.budgetNanos))
                .append(" fpsTarget=").append(targetFps)
                .append(" calls=").append(fs.dispatcherCalls)
                .append(" timeouts=").append(fs.dispatcherTimeouts)
                .append(" lanes=").append(scheduler != null ? scheduler.getLaneCount() : 0);

        b.append("\n  breakdownMs:")
                .append(" jobs=").append(nsToMs(fs.drainJobsNanos))
                .append(" hotReload=").append(nsToMs(fs.hotReloadNanos))
                .append(" events=").append(nsToMs(fs.eventsNanos))
                .append(" world=").append(nsToMs(fs.worldUpdateNanos))
                .append(" awaitWorkers=").append(nsToMs(fs.awaitWorkersNanos))
                .append(" pool=").append(nsToMs(fs.poolMaintenanceNanos));

        WorkerSystemStats[] ws = getWorkerStatsSnapshot();
        if (ws.length > 0) {
            Arrays.sort(ws, (a, c) -> {
                long av = (a.emaTickNanos != 0L) ? a.emaTickNanos : a.lastTickNanos;
                long cv = (c.emaTickNanos != 0L) ? c.emaTickNanos : c.lastTickNanos;
                return Long.compare(cv, av);
            });
            int top = Math.min(8, ws.length);
            b.append("\n  workers(top").append(top).append("):");
            for (int i = 0; i < top; i++) {
                WorkerSystemStats s = ws[i];
                b.append("\n    - ").append(s.systemName)
                        .append(" prof=").append(s.profile)
                        .append(" thr=").append(s.threadName)
                        .append(" tickMs=").append(nsToMs(s.lastTickNanos))
                        .append(" emaMs=").append(nsToMs(s.emaTickNanos))
                        .append(" maxMs=").append(nsToMs(s.maxTickNanos))
                        .append(" lagMs=").append(nsToMs(s.lastQueueLagNanos))
                        .append(" skip=").append(s.skippedTicks);
            }
        }

        log.info(b.toString());
    }

    // ======================================================================
    // CDPR CONTRACT: RuntimePolicy
    // ======================================================================

    public enum RequestOrigin { JAVA_PROVIDER, SCRIPT_CONFIG }

    public static final class RuntimePolicy {

        public final boolean allowScriptProfileRequests;
        public final boolean allowScriptSandboxRequests;

        public final int maxIsolatedRuntimes;
        public final long idleEvictMs;

        public final Set<String> allowedProfiles;
        public final Set<String> pinnedProfiles;

        public final boolean debugLogs;

        private RuntimePolicy(
                boolean allowScriptProfileRequests,
                boolean allowScriptSandboxRequests,
                int maxIsolatedRuntimes,
                long idleEvictMs,
                Set<String> allowedProfiles,
                Set<String> pinnedProfiles,
                boolean debugLogs
        ) {
            this.allowScriptProfileRequests = allowScriptProfileRequests;
            this.allowScriptSandboxRequests = allowScriptSandboxRequests;
            this.maxIsolatedRuntimes = Math.max(0, maxIsolatedRuntimes);
            this.idleEvictMs = Math.max(0L, idleEvictMs);
            this.allowedProfiles = Collections.unmodifiableSet(allowedProfiles);
            this.pinnedProfiles = Collections.unmodifiableSet(pinnedProfiles);
            this.debugLogs = debugLogs;
        }

        public static RuntimePolicy fromSystemProps() {
            boolean allowScriptProfiles = boolProp("kalitech.runtime.allowScriptProfiles", false);
            boolean allowScriptSandbox  = boolProp("kalitech.runtime.allowScriptSandbox", false);

            // AAA default: enough isolated runtimes for multiple worker systems + tools.
            int maxIsolated = intProp("kalitech.runtime.maxIsolated", 16);
            long idleEvictMs = longProp("kalitech.runtime.idleEvictMs", 120_000L);

            Set<String> allowed = csvLowerProp(
                    "kalitech.runtime.allowed",
                    "world,ui,tools,hotreload,sandbox"
            );

            Set<String> pinned = csvLowerProp(
                    "kalitech.runtime.pinned",
                    "ui,tools"
            );

            allowed.add("world");
            pinned.remove("world");

            boolean debug = boolProp("kalitech.runtime.debug", false);

            RuntimePolicy p = new RuntimePolicy(
                    allowScriptProfiles,
                    allowScriptSandbox,
                    maxIsolated,
                    idleEvictMs,
                    allowed,
                    pinned,
                    debug
            );

            if (p.debugLogs) {
                log.info("[runtimePolicy] allowScriptProfiles={} allowScriptSandbox={} maxIsolated={} idleEvictMs={} allowed={} pinned={}",
                        p.allowScriptProfileRequests, p.allowScriptSandboxRequests, p.maxIsolatedRuntimes, p.idleEvictMs, p.allowedProfiles, p.pinnedProfiles);
            }

            return p;
        }

        public String resolveProfile(String requested, RequestOrigin origin) {
            String p = norm(requested);
            if (p == null) return "world";

            // AAA: internal Java providers may request dynamic isolated profiles:
            // sys.* (and sys.*.laneN for striped workers).
            if (origin == RequestOrigin.JAVA_PROVIDER && p.startsWith("sys.")) {
                return p;
            }

            if (!allowedProfiles.contains(p)) return "world";

            if (origin == RequestOrigin.SCRIPT_CONFIG) {
                if (!allowScriptProfileRequests) return "world";
                if ("sandbox".equals(p) && !allowScriptSandboxRequests) return "world";
            }
            return p;
        }

        public boolean isPinned(String profile) {
            String p = norm(profile);
            return p != null && pinnedProfiles.contains(p);
        }

        private static String norm(String s) {
            if (s == null) return null;
            String t = s.trim().toLowerCase(Locale.ROOT);
            return t.isEmpty() ? null : t;
        }

        private static boolean boolProp(String key, boolean def) {
            String v = System.getProperty(key);
            if (v == null) return def;
            v = v.trim().toLowerCase(Locale.ROOT);
            if (v.isEmpty()) return def;
            return "1".equals(v) || "true".equals(v) || "yes".equals(v) || "on".equals(v);
        }

        private static int intProp(String key, int def) {
            String v = System.getProperty(key);
            if (v == null) return def;
            try { return Integer.parseInt(v.trim()); } catch (Exception ignored) { return def; }
        }

        private static long longProp(String key, long def) {
            String v = System.getProperty(key);
            if (v == null) return def;
            try { return Long.parseLong(v.trim()); } catch (Exception ignored) { return def; }
        }

        private static Set<String> csvLowerProp(String key, String defCsv) {
            String v = System.getProperty(key, defCsv);
            Set<String> set = new LinkedHashSet<>();
            for (String part : v.split(",")) {
                String p = part.trim().toLowerCase(Locale.ROOT);
                if (!p.isEmpty()) set.add(p);
            }
            return set;
        }
    }

    // ======================================================================
    // RuntimePool
    // ======================================================================

    static final class RuntimePool {

        private final ScriptRuntime base; // "world"
        private final RuntimePolicy policy;

        private final Object lock = new Object();
        private final LinkedHashMap<String, Entry> map = new LinkedHashMap<>(16, 0.75f, true);

        RuntimePool(ScriptRuntime base, RuntimePolicy policy) {
            this.base = Objects.requireNonNull(base, "base runtime");
            this.policy = Objects.requireNonNull(policy, "policy");
            map.put("world", new Entry(base, true, System.nanoTime()));
        }

        ScriptRuntime get(String requestedProfile) {
            final String profile = policy.resolveProfile(requestedProfile, RequestOrigin.JAVA_PROVIDER);
            if ("world".equals(profile)) return base;

            final long now = System.nanoTime();
            synchronized (lock) {
                Entry e = map.get(profile);
                if (e != null) {
                    e.lastAccessNanos = now;
                    return e.runtime;
                }

                ScriptRuntime rt = forkOrFallback(profile);
                boolean pinned = policy.isPinned(profile);

                map.put(profile, new Entry(rt, pinned, now));
                enforceLimitLRU(now);
                return rt;
            }
        }

        void evictIdle(long nowNanos) {
            if (policy.idleEvictMs <= 0) return;
            final long cutoff = nowNanos - TimeUnit.MILLISECONDS.toNanos(policy.idleEvictMs);

            synchronized (lock) {
                if (map.size() <= 1) return;

                Iterator<Map.Entry<String, Entry>> it = map.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Entry> me = it.next();
                    String k = me.getKey();
                    Entry e = me.getValue();

                    if ("world".equals(k)) continue;
                    if (e.pinned) continue;

                    if (e.lastAccessNanos < cutoff) {
                        if (policy.debugLogs) {
                            log.info("[runtimePool] idle-evict profile={} idleMs>{}", k, policy.idleEvictMs);
                        }
                        closeQuiet(e.runtime);
                        it.remove();
                    }
                }
            }
        }

        void closeAll() {
            synchronized (lock) {
                for (Map.Entry<String, Entry> me : map.entrySet()) {
                    String k = me.getKey();
                    Entry e = me.getValue();
                    if ("world".equals(k)) continue;
                    closeQuiet(e.runtime);
                }
                map.clear();
                map.put("world", new Entry(base, true, System.nanoTime()));
            }
        }

        private void enforceLimitLRU(long nowNanos) {
            final int maxIsolated = policy.maxIsolatedRuntimes;
            if (maxIsolated <= 0) {
                Iterator<Map.Entry<String, Entry>> it = map.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Entry> me = it.next();
                    String k = me.getKey();
                    if ("world".equals(k)) continue;
                    closeQuiet(me.getValue().runtime);
                    it.remove();
                }
                return;
            }

            while ((map.size() - 1) > maxIsolated) {
                Map.Entry<String, Entry> eldest = null;
                for (Map.Entry<String, Entry> me : map.entrySet()) {
                    if ("world".equals(me.getKey())) continue;
                    if (me.getValue().pinned) continue;
                    eldest = me;
                    break;
                }

                if (eldest == null) {
                    if (policy.debugLogs) {
                        log.warn("[runtimePool] over limit but all isolated profiles are pinned; limit={} pinned={}",
                                maxIsolated, policy.pinnedProfiles);
                    }
                    break;
                }

                String key = eldest.getKey();
                Entry e = eldest.getValue();
                if (policy.debugLogs) {
                    log.info("[runtimePool] lru-evict profile={} limit={} size={}", key, maxIsolated, map.size() - 1);
                }
                closeQuiet(e.runtime);
                map.remove(key);
            }
        }

        // -------- Optional invalidate helpers for HotReload --------

        int invalidateMany(Set<String> moduleIds) {
            ScriptRuntime rt = base;
            Object caches = tryCall0(rt, "caches");
            if (caches == null) return 0;
            Object inv = tryCall1(caches, "invalidateMany", Set.class, moduleIds);
            return (inv instanceof Integer i) ? i : 0;
        }

        int invalidateManyWithReason(Set<String> moduleIds, String reason) {
            ScriptRuntime rt = base;
            Object caches = tryCall0(rt, "caches");
            if (caches == null) return invalidateMany(moduleIds);

            try {
                Method m = caches.getClass().getMethod("invalidateManyWithReason", Set.class, String.class);
                Object r = m.invoke(caches, moduleIds, reason);
                return (r instanceof Integer i) ? i : invalidateMany(moduleIds);
            } catch (Throwable ignored) {
                return invalidateMany(moduleIds);
            }
        }

        // -------- Fork/close helpers (reflection to avoid hard deps) --------

        private ScriptRuntime forkOrFallback(String profile) {
            // Try: base.fork(profile) / base.fork()
            ScriptRuntime rt = tryCall1(base, "fork", String.class, profile);
            if (rt != null) return rt;

            rt = tryCall0(base, "fork");
            if (rt != null) return rt;

            // fallback to base (not ideal, but safe)
            if (policy.debugLogs) {
                log.warn("[runtimePool] Could not fork runtime for profile={}, using world runtime", profile);
            }
            return base;
        }

        private static void closeQuiet(ScriptRuntime rt) {
            if (rt == null) return;
            if (rt == rt) {
                // try close() / shutdown() but ignore if absent
                tryCallVoid(rt, "close");
                tryCallVoid(rt, "shutdown");
            }
        }

        private static ScriptRuntime tryCall0(Object target, String name) {
            try {
                Method m = target.getClass().getMethod(name);
                Object r = m.invoke(target);
                return (r instanceof ScriptRuntime gr) ? gr : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static ScriptRuntime tryCall1(Object target, String name, Class<?> p0, Object a0) {
            try {
                Method m = target.getClass().getMethod(name, p0);
                Object r = m.invoke(target, a0);
                return (r instanceof ScriptRuntime gr) ? gr : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static boolean tryCallVoid(Object target, String name) {
            try {
                Method m = target.getClass().getMethod(name);
                m.invoke(target);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        static final class Entry {
            final ScriptRuntime runtime;
            final boolean pinned;
            long lastAccessNanos;

            Entry(ScriptRuntime runtime, boolean pinned, long lastAccessNanos) {
                this.runtime = runtime;
                this.pinned = pinned;
                this.lastAccessNanos = lastAccessNanos;
            }
        }
    }
}