package org.foxesworld.kalitech.engine.world;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.bullet.BulletAppState;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs current KWorld and allows hot-swap world via setWorld().
 *
 * Author: Calista Verner
 */
public final class WorldAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(WorldAppState.class);

    private final ScriptEventBus bus;
    private final EcsWorld ecs;

    /**
     * Runtime pool:
     *  - "world" (default) => base runtime (fast, shared cache)
     *  - other profiles ("ui","tools","hotreload","sandbox") => isolated runtime if supported
     */
    private final RuntimePool runtimePool;

    private final EngineApi api;
    private final PhysicsSpace physicsSpace;

    /** Optional watcher (can be null). */
    private HotReloadWatcher hotReload;

    /** Drain budget per frame for ScriptJobQueue. */
    private int jobDrainBudget = 256;

    private KWorld world;
    private SystemContext ctx;
    private BulletAppState bullet;

    private boolean running = false;

    /** When hot reload invalidates something, we restart world deterministically next update. */
    private boolean restartRequested = false;

    public WorldAppState(RuntimeAppState runtimeAppState) {
        this.bus = runtimeAppState.getBus();
        this.ecs = runtimeAppState.getEcs();
        this.runtimePool = new RuntimePool(runtimeAppState.getRuntime());
        this.api = runtimeAppState.getEngineApi();
        this.physicsSpace = runtimeAppState.getSpace();
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

        // New model: bind JS environment via Graal bindings, not via runtime.bindGlobals()
        installJsGlobals(this.ctx, this.api);

        tryStartWorld();
    }

    public void setWorld(KWorld newWorld) {
        if (this.world == newWorld) return;
        tryStopWorld();
        this.world = newWorld;

        // World swap should be clean and deterministic.
        // Keep JS env stable; world scripts can require() modules as needed.
        tryStartWorld();
    }

    public KWorld getWorld() { return world; }

    /** For JS bootstrap(main.bootstrap(ctx)) we pass SystemContext itself. */
    public SystemContext getContextForJs() { return ctx; }

    @Override
    public void update(float tpf) {
        if (!isEnabled() || ctx == null) return;

        // ==========================================================
        // Deterministic pipeline:
        // 1) drain ScriptJobQueue (commands from background threads)
        // 2) hot reload poll -> invalidate -> request world restart
        // 3) if restart requested: stop -> (re)install globals -> start
        // 4) pump event bus
        // 5) world update
        // ==========================================================

        // World runtime (default profile)
        final GraalScriptRuntime worldRt = runtimePool.get("world");

        // 1) Drain queued main-thread jobs (if your ScriptJobQueue supports draining).
        try {
            if (jobDrainBudget > 0) {
                worldRt.jobs().drain(jobDrainBudget);
            }
        } catch (NoSuchMethodError nsme) {
            // job queue drain not available yet
        } catch (Exception e) {
            log.error("Script job drain failed", e);
        }

        // 2) Hot reload poll (optional) -> invalidate -> request deterministic restart
        try {
            HotReloadWatcher hr = this.hotReload;
            if (hr != null) {
                Set<String> changed = hr.pollChanged();
                if (changed != null && !changed.isEmpty()) {
                    int removed;
                    try {
                        removed = worldRt.invalidateManyWithReason(changed, "hotReload");
                    } catch (NoSuchMethodError e) {
                        removed = worldRt.invalidateMany(changed);
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

        // 3) Deterministic restart after reload (no magic auto-rebind flags)
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

        // 4) Pump event bus
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
    }

    @Override
    protected void cleanup(Application app) {
        tryStopWorld();
        try { runtimePool.closeAll(); } catch (Throwable ignored) {}
        ctx = null;
        log.info("WorldAppState cleaned up");
    }

    @Override
    protected void onEnable() { tryStartWorld(); }

    @Override
    protected void onDisable() { tryStopWorld(); }

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
     * New global binding strategy compatible with your current GraalScriptRuntime:
     * - require is already installed by runtime ctor
     * - we bind ctx/api and common aliases here, via JS bindings
     */
    private void installJsGlobals(SystemContext sysCtx, EngineApi engineApi) {
        try {
            // Bind into WORLD runtime only (main pipeline).
            Value bindings = runtimePool.get("world").ctx().getBindings("js");

            bindings.putMember("ctx", sysCtx);
            bindings.putMember("api", engineApi);

            // old habit: engine === api
            bindings.putMember("engine", engineApi);

            // IMPORTANT: render must be RenderApi, not EngineApiImpl
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

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }

    public EngineApi getApi() {
        return api;
    }

    /** Default world runtime (back-compat). */
    public GraalScriptRuntime getRuntime() {
        return runtimePool.get("world");
    }

    /** Runtime by profile: "world"(default), "ui", "tools", "hotreload", "sandbox", etc. */
    public GraalScriptRuntime getRuntime(String profile) {
        return runtimePool.get(profile);
    }

    public EcsWorld getEcs() {
        return ecs;
    }

    public ScriptEventBus getBus() {
        return bus;
    }

    private static void tryPut(Value bindings, String name, Object value) {
        try {
            bindings.putMember(name, value);
        } catch (Throwable ignored) {
            // Keep it silent: missing exports / host access restrictions are expected sometimes.
        }
    }

    // ----------------------------------------------------------------------
    // RuntimePool (lazy, per-world, isolation-on-demand, reflection-friendly)
    // ----------------------------------------------------------------------
    static final class RuntimePool {
        private final GraalScriptRuntime base;
        private final ConcurrentHashMap<String, GraalScriptRuntime> map = new ConcurrentHashMap<>();

        RuntimePool(GraalScriptRuntime base) {
            this.base = Objects.requireNonNull(base, "base runtime");
            map.put("world", base);
        }

        GraalScriptRuntime get(String profile) {
            final String key = (profile == null || profile.isBlank()) ? "world" : profile.trim();
            if ("world".equals(key)) return base;
            return map.computeIfAbsent(key, this::forkOrFallback);
        }

        private GraalScriptRuntime forkOrFallback(String profile) {
            // Try: fork(), fork(String), createIsolated(String), child()
            GraalScriptRuntime rt = tryCall0(base, "fork");
            if (rt != null) {
                log.info("[runtimePool] fork() -> profile={}", profile);
                return rt;
            }

            rt = tryCall1(base, "fork", String.class, profile);
            if (rt != null) {
                log.info("[runtimePool] fork(String) -> profile={}", profile);
                return rt;
            }

            rt = tryCall1(base, "createIsolated", String.class, profile);
            if (rt != null) {
                log.info("[runtimePool] createIsolated(String) -> profile={}", profile);
                return rt;
            }

            rt = tryCall0(base, "child");
            if (rt != null) {
                log.info("[runtimePool] child() -> profile={}", profile);
                return rt;
            }

            // Fallback: no isolation available yet
            log.warn("[runtimePool] no fork method found in GraalScriptRuntime; using base runtime for profile={}", profile);
            return base;
        }

        void closeAll() {
            for (var e : map.entrySet()) {
                final String k = e.getKey();
                final GraalScriptRuntime rt = e.getValue();
                if (rt == null || rt == base) continue;

                boolean closed = tryCallVoid(rt, "close")
                        || tryCallVoid(rt, "shutdown")
                        || tryCallVoid(rt, "dispose");

                log.info("[runtimePool] close profile={} closed={}", k, closed);
            }
            map.clear();
            map.put("world", base);
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
    }
}