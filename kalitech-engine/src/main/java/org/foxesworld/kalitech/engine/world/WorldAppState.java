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

import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.Set;

/**
 * Runs current KWorld and allows hot-swap world via setWorld().
 *
 * Author: Calista Verner
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

    /** When hot reload invalidates something, we restart world deterministically next update. */
    private boolean restartRequested = false;

    public WorldAppState(ScriptEventBus bus, EcsWorld ecs, GraalScriptRuntime runtime, EngineApi api) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.api = Objects.requireNonNull(api, "api");
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

        this.ctx = new SystemContext(sa, sa.getAssetManager(), bus, ecs, runtime, api);

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

        // 1) Drain queued main-thread jobs (if your ScriptJobQueue supports draining).
        // NOTE: adjust method name if your ScriptJobQueue differs.
        try {
            if (jobDrainBudget > 0) {
                // Common naming variants: drain(budget), drainToMainThread(budget), run(budget), pump(budget)
                // Replace "drain" below with the real one in your ScriptJobQueue.
                runtime.jobs().drain(jobDrainBudget);
            }
        } catch (NoSuchMethodError nsme) {
            // If ScriptJobQueue doesn't have drain(int) yet, we keep engine running.
            // Add it later, or rename the call above.
            // log.debug("ScriptJobQueue.drain(int) not available yet");
        } catch (Exception e) {
            log.error("Script job drain failed", e);
        }

        // 2) Hot reload poll (optional) -> invalidate -> request deterministic restart
        try {
            HotReloadWatcher hr = this.hotReload;
            if (hr != null) {
                Set<String> changed = hr.pollChanged();
                if (changed != null && !changed.isEmpty()) {
                    int removed = runtime.invalidateManyWithReason(changed, "hotReload");
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
                // Stop the current world cleanly to drop subscriptions/state
                tryStopWorld();

                // Re-install globals (in case scripts rely on stable bindings across reload cycles)
                installJsGlobals(this.ctx, this.api);

                // Start again (world.start should bootstrap and require() entrypoints)
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
            Value bindings = runtime.ctx().getBindings("js");

            bindings.putMember("ctx", sysCtx);
            bindings.putMember("api", engineApi);

            // old habit: engine === api
            bindings.putMember("engine", engineApi);

            // IMPORTANT: render must be RenderApi, not EngineApiImpl
            // Adjust getter name to your real API: render(), renderApi(), renderer(), etc.
            try {
                bindings.putMember("render", engineApi.render());
            } catch (Throwable t) {
                // fallback: keep engine (but then render.* calls will still fail)
                bindings.putMember("render", engineApi);
                log.warn("Failed to bind render as EngineApi.render(); scripts may call missing render.* methods", t);
            }

            // optional:
            // bindings.putMember("events", engineApi.events());
            // bindings.putMember("ecs", engineApi.ecs());
        } catch (Exception e) {
            log.error("Failed to install JS globals", e);
        }
    }


    private static void tryPut(Value bindings, String name, Object value) {
        try {
            bindings.putMember(name, value);
        } catch (Throwable ignored) {
            // Keep it silent: missing exports / host access restrictions are expected sometimes.
        }
    }
}