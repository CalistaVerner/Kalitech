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
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

import java.util.Objects;

/**
 * Runs current KWorld and allows hot-swap world via setWorld().
 */
public final class WorldAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(WorldAppState.class);

    private final ScriptEventBus bus;
    private final EcsWorld ecs;
    private final GraalScriptRuntime runtime;
    private final EngineApi api;

    private KWorld world;
    private SystemContext ctx;

    private boolean running = false;

    public WorldAppState(ScriptEventBus bus, EcsWorld ecs, GraalScriptRuntime runtime, EngineApi api) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.api = Objects.requireNonNull(api, "api");
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

        if (world != null && running) {
            try {
                world.update(ctx, tpf);
            } catch (Exception e) {
                log.error("World update failed", e);
            }
        }

        try {
            bus.pump();
        } catch (Exception e) {
            log.error("Event bus pump failed", e);
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