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
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * CDPR-style:
 * - 1 runtime per world by default (fast, shared cache)
 * - isolated runtimes on demand via RuntimePool + Policy
 * - strict contract: who can request profiles, sandbox permissions
 * - memory safety: max isolated runtimes + LRU eviction (+ optional idle eviction)
 */
public final class WorldAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(WorldAppState.class);

    private final ScriptEventBus bus;
    private final EcsWorld ecs;

    private final EngineApi api;
    private final PhysicsSpace physicsSpace;

    private final RuntimePolicy runtimePolicy;
    private final RuntimePool runtimePool;

    /** Optional watcher (can be null). */
    private HotReloadWatcher hotReload;

    /** Drain budget per frame for ScriptJobQueue. */
    private int jobDrainBudget = 256;

    private KWorld world;
    private SystemContext ctx;

    private boolean running = false;
    private boolean restartRequested = false;

    // pool housekeeping (avoid doing evict scans every frame)
    private long lastPoolMaintenanceNanos = 0L;

    public WorldAppState(RuntimeAppState runtimeAppState) {
        this.bus = runtimeAppState.getBus();
        this.ecs = runtimeAppState.getEcs();

        final GraalScriptRuntime base = runtimeAppState.getRuntime();
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

    public HotReloadWatcher getHotReloadWatcher() {
        return hotReload;
    }

    public void setJobDrainBudget(int budget) {
        this.jobDrainBudget = Math.max(0, budget);
    }

    public int getJobDrainBudget() {
        return jobDrainBudget;
    }

    @Override
    protected void initialize(Application app) {
        if (!(app instanceof SimpleApplication sa)) {
            throw new IllegalStateException("WorldAppState requires SimpleApplication (got " + app.getClass().getName() + ")");
        }

        this.ctx = new SystemContext(sa, this);

        // Bind globals into WORLD runtime only (main pipeline)
        installJsGlobals(this.ctx, this.api);

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

        // 1) Drain ScriptJobQueue on world runtime
        try {
            if (jobDrainBudget > 0) {
                runtimePool.get("world").jobs().drain(jobDrainBudget);
            }
        } catch (NoSuchMethodError ignored) {
        } catch (Exception e) {
            log.error("Script job drain failed", e);
        }

        // 2) Hot reload invalidation (optional)
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
        }

        // 3) Restart world deterministically after reload
        if (restartRequested) {
            restartRequested = false;
            try {
                tryStopWorld();
                installJsGlobals(this.ctx, this.api);
                tryStartWorld();
                log.info("World restarted after hot reload");
            } catch (Exception e) {
                log.error("World restart after hot reload failed", e);
            }
        }

        // 4) Pump events
        try {
            bus.pump();
        } catch (Exception e) {
            log.error("Event bus pump failed", e);
        }

        // 5) Update world
        if (world != null && running) {
            try {
                world.update(ctx, tpf);
            } catch (Exception e) {
                log.error("World update failed", e);
            }
        }

        // 6) RuntimePool maintenance (idle eviction) ~ once per second
        final long now = System.nanoTime();
        if (now - lastPoolMaintenanceNanos >= TimeUnit.SECONDS.toNanos(1)) {
            lastPoolMaintenanceNanos = now;
            runtimePool.evictIdle(now);
        }
    }

    @Override
    protected void cleanup(Application app) {
        tryStopWorld();
        try { runtimePool.closeAll(); } catch (Throwable ignored) {}
        ctx = null;
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
     * Bind globals into WORLD runtime only.
     * UI/tools/hotreload runtimes can bind their own stuff separately later if нужно.
     */
    private void installJsGlobals(SystemContext sysCtx, EngineApi engineApi) {
        try {
            Value bindings = runtimePool.get("world").ctx().getBindings("js");

            bindings.putMember("ctx", sysCtx);
            bindings.putMember("api", engineApi);

            // old habit: engine === api
            bindings.putMember("engine", engineApi);

            try {
                bindings.putMember("render", engineApi.render());
            } catch (Throwable t) {
                bindings.putMember("render", engineApi);
                log.warn("Failed to bind render as EngineApi.render(); scripts may call missing render.* methods", t);
            }
        } catch (Exception e) {
            log.error("Failed to install JS globals", e);
        }
    }

    public PhysicsSpace getPhysicsSpace() { return physicsSpace; }
    public EngineApi getApi() { return api; }
    public EcsWorld getEcs() { return ecs; }
    public ScriptEventBus getBus() { return bus; }

    /** Default world runtime (back-compat). */
    public GraalScriptRuntime getRuntime() {
        return runtimePool.get("world");
    }

    /** Runtime by profile (validated by policy). */
    public GraalScriptRuntime getRuntime(String profile) {
        return runtimePool.get(profile);
    }

    // ======================================================================
    // CDPR CONTRACT: RuntimePolicy
    // ======================================================================

    public enum RequestOrigin {
        JAVA_PROVIDER,
        SCRIPT_CONFIG
    }

    public static final class RuntimePolicy {

        // defaults = safe shipping posture
        public final boolean allowScriptProfileRequests;
        public final boolean allowScriptSandboxRequests;

        public final int maxIsolatedRuntimes;        // excludes "world"
        public final long idleEvictMs;               // 0 = disabled

        public final Set<String> allowedProfiles;    // normalized lowercase
        public final Set<String> pinnedProfiles;     // not evicted, normalized lowercase

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
            // VMOPTIONS:
            // -Dkalitech.runtime.allowScriptProfiles=true|false
            // -Dkalitech.runtime.allowScriptSandbox=true|false
            // -Dkalitech.runtime.maxIsolated=3
            // -Dkalitech.runtime.idleEvictMs=120000
            // -Dkalitech.runtime.allowed=world,ui,tools,hotreload,sandbox
            // -Dkalitech.runtime.pinned=ui,tools
            // -Dkalitech.runtime.debug=true

            boolean allowScriptProfiles = boolProp("kalitech.runtime.allowScriptProfiles", false);
            boolean allowScriptSandbox  = boolProp("kalitech.runtime.allowScriptSandbox", false);

            int maxIsolated = intProp("kalitech.runtime.maxIsolated", 3);
            long idleEvictMs = longProp("kalitech.runtime.idleEvictMs", 120_000L);

            Set<String> allowed = csvLowerProp(
                    "kalitech.runtime.allowed",
                    "world,ui,tools,hotreload,sandbox"
            );

            // pinned by default: ui/tools (world is implicit pinned)
            Set<String> pinned = csvLowerProp(
                    "kalitech.runtime.pinned",
                    "ui,tools"
            );

            // sanitize
            allowed.add("world");
            pinned.remove("world"); // world is implicit

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

        /** Resolve requested profile under contract, returns normalized final profile. */
        public String resolveProfile(String requested, RequestOrigin origin) {
            String p = norm(requested);
            if (p == null) return "world";
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
    // RuntimePool with LRU + idle eviction
    // ======================================================================

    static final class RuntimePool {

        private final GraalScriptRuntime base; // "world"
        private final RuntimePolicy policy;

        private final Object lock = new Object();

        // accessOrder=true => LRU iteration gives oldest-first
        private final LinkedHashMap<String, Entry> map = new LinkedHashMap<>(16, 0.75f, true);

        RuntimePool(GraalScriptRuntime base, RuntimePolicy policy) {
            this.base = Objects.requireNonNull(base, "base runtime");
            this.policy = Objects.requireNonNull(policy, "policy");
            map.put("world", new Entry(base, true, System.nanoTime()));
        }

        GraalScriptRuntime get(String requestedProfile) {
            final String profile = policy.resolveProfile(requestedProfile, RequestOrigin.JAVA_PROVIDER);
            if ("world".equals(profile)) return base;

            final long now = System.nanoTime();
            synchronized (lock) {
                Entry e = map.get(profile);
                if (e != null) {
                    e.lastAccessNanos = now;
                    return e.runtime;
                }

                // Need to create isolated runtime
                GraalScriptRuntime rt = forkOrFallback(profile);
                boolean pinned = policy.isPinned(profile);

                map.put(profile, new Entry(rt, pinned, now));

                // enforce size after insert
                enforceLimitLRU(now);
                return rt;
            }
        }

        /**
         * Same as get(), but resolves requested profile using script-origin rules.
         * Used by providers to honor contract.
         */
        GraalScriptRuntime getFromScriptRequest(String requestedProfile) {
            final String profile = policy.resolveProfile(requestedProfile, RequestOrigin.SCRIPT_CONFIG);
            return get(profile); // now treated as normalized java-provider request
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
                // evict everything except world (unless pinned, но pinned тоже не держим при max=0)
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

            // isolated count = total-1
            while ((map.size() - 1) > maxIsolated) {
                // eldest = first entry in iteration (because accessOrder=true)
                Map.Entry<String, Entry> eldest = null;
                for (Map.Entry<String, Entry> me : map.entrySet()) {
                    if ("world".equals(me.getKey())) continue;
                    if (me.getValue().pinned) continue;
                    eldest = me;
                    break;
                }

                // if all isolated are pinned -> we cannot evict without breaking contract
                if (eldest == null) {
                    if (policy.debugLogs) {
                        log.warn("[runtimePool] over limit but all isolated profiles are pinned; limit={} pinned={}",
                                maxIsolated, policy.pinnedProfiles);
                    }
                    break;
                }

                String k = eldest.getKey();
                Entry e = eldest.getValue();

                if (policy.debugLogs) {
                    log.info("[runtimePool] LRU-evict profile={} (limit={}, isolatedNow={})", k, maxIsolated, (map.size() - 1));
                }

                closeQuiet(e.runtime);
                map.remove(k);
            }
        }

        private GraalScriptRuntime forkOrFallback(String profile) {
            // Try: fork(), fork(String), createIsolated(String), child()
            GraalScriptRuntime rt = tryCall0(base, "fork");
            if (rt != null) {
                if (policy.debugLogs) log.info("[runtimePool] fork() -> profile={}", profile);
                return rt;
            }

            rt = tryCall1(base, "fork", String.class, profile);
            if (rt != null) {
                if (policy.debugLogs) log.info("[runtimePool] fork(String) -> profile={}", profile);
                return rt;
            }

            rt = tryCall1(base, "createIsolated", String.class, profile);
            if (rt != null) {
                if (policy.debugLogs) log.info("[runtimePool] createIsolated(String) -> profile={}", profile);
                return rt;
            }

            rt = tryCall0(base, "child");
            if (rt != null) {
                if (policy.debugLogs) log.info("[runtimePool] child() -> profile={}", profile);
                return rt;
            }

            log.warn("[runtimePool] no fork method found in GraalScriptRuntime; using base runtime for profile={}", profile);
            return base;
        }

        private static void closeQuiet(GraalScriptRuntime rt) {
            if (rt == null) return;
            boolean closed = tryCallVoid(rt, "close") || tryCallVoid(rt, "shutdown") || tryCallVoid(rt, "dispose");
            if (!closed) {
                // ok: base runtime or older API without close
            }
        }

        private static GraalScriptRuntime tryCall0(Object target, String name) {
            try {
                Method m = target.getClass().getMethod(name);
                Object r = m.invoke(target);
                return (r instanceof GraalScriptRuntime gr) ? gr : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static GraalScriptRuntime tryCall1(Object target, String name, Class<?> p0, Object a0) {
            try {
                Method m = target.getClass().getMethod(name, p0);
                Object r = m.invoke(target, a0);
                return (r instanceof GraalScriptRuntime gr) ? gr : null;
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
            final GraalScriptRuntime runtime;
            final boolean pinned;
            long lastAccessNanos;

            Entry(GraalScriptRuntime runtime, boolean pinned, long lastAccessNanos) {
                this.runtime = runtime;
                this.pinned = pinned;
                this.lastAccessNanos = lastAccessNanos;
            }
        }
    }
}