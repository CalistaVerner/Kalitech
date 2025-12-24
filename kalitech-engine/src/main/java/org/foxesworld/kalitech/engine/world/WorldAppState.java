package org.foxesworld.kalitech.engine.world;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

import java.util.Objects;
import java.util.Set;

/**
 * Runs current KWorld and allows hot-swap world via setWorld().
 */
public final class WorldAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(WorldAppState.class);

    private final ScriptEventBus bus;
    private final EcsWorld ecs;
    private final GraalScriptRuntime runtime;
    private final EngineApi api;

    /** Optional watcher (can be null). */
    private HotReloadWatcher hotReload;

    /** Drain budget per frame for ScriptJobQueue. */
    private int jobDrainBudget = 256;

    private KWorld world;
    private SystemContext ctx;

    private boolean running = false;

    public WorldAppState(ScriptEventBus bus, EcsWorld ecs, GraalScriptRuntime runtime, EngineApi api) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.api = Objects.requireNonNull(api, "api");
    }

    /**
     * Optional hot reload watcher hookup.
     * You can call this before/after initialize. If after initialize, it starts working next update().
     */
    public void setHotReloadWatcher(HotReloadWatcher watcher) {
        this.hotReload = watcher;
    }

    public HotReloadWatcher getHotReloadWatcher() {
        return hotReload;
    }

    /**
     * Budget limiter: max jobs drained from ScriptJobQueue per frame.
     * Keep it bounded to avoid long stalls on big reloads.
     */
    public void setJobDrainBudget(int budget) {
        this.jobDrainBudget = Math.max(0, budget);
    }

    public int getJobDrainBudget() {
        return jobDrainBudget;
    }

    @Override
    protected void initialize(Application app) {
        SimpleApplication sa = (SimpleApplication) app;
        this.ctx = new SystemContext(sa, sa.getAssetManager(), bus, ecs, runtime, api);

        // JS-first globals
        runtime.bindGlobals(this.ctx, this.api);

        tryStartWorld();
    }

    public void setWorld(KWorld newWorld) {
        if (this.world == newWorld) return;
        tryStopWorld();
        this.world = newWorld;
        tryStartWorld();
    }

    public KWorld getWorld() { return world; }

    /** For JS bootstrap(main.bootstrap(ctx)) we pass SystemContext itself. */
    public SystemContext getContextForJs() { return ctx; }

    @Override
    public void update(float tpf) {
        if (!isEnabled() || ctx == null) return;

        // ==========================================================
        // Diamond pipeline order (deterministic):
        // 1) drain ScriptJobQueue (commands from background threads)
        // 2) hot reload poll -> invalidate+dispose(reason)
        // 3) auto rebind globals (after invalidation)
        // 4) pump event bus
        // 5) world update
        // ==========================================================

        // 1) Drain queued main-thread jobs
        try {
            if (jobDrainBudget > 0) {
                runtime.drainJobs(jobDrainBudget);
            }
        } catch (Exception e) {
            log.error("Script job drain failed", e);
        }

        // 2) Hot reload poll (optional)
        try {
            HotReloadWatcher hr = this.hotReload;
            if (hr != null) {
                Set<String> changed = hr.pollChanged();
                if (changed != null && !changed.isEmpty()) {
                    // IMPORTANT: use the new "reason" API (dispose isolation)
                    int removed = runtime.invalidateManyWithReason(changed, "hotReload");
                    if (removed > 0) {
                        log.info("HotReload invalidated modules: {}", removed);
                    }
                }
            }
        } catch (Exception e) {
            log.error("HotReload poll/invalidate failed", e);
        }

        // 3) Auto rebind (after reload/reset)
        try {
            if (runtime.consumeRebindRequested()) {
                runtime.bindGlobals(this.ctx, this.api);
                log.debug("GraalScriptRuntime globals rebound after reload");
            }
        } catch (Exception e) {
            log.error("Rebind globals failed", e);
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
}