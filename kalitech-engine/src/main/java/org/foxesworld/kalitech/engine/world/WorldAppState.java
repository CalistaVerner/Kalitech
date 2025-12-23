package org.foxesworld.kalitech.engine.world;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

public final class WorldAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(WorldAppState.class);

    private KWorld world;
    private SystemContext ctx;

    @Override
    protected void initialize(Application app) {
        if (!(app instanceof SimpleApplication sa)) {
            throw new IllegalStateException("WorldAppState requires SimpleApplication");
        }

        ScriptEventBus bus = new ScriptEventBus();
        this.ctx = new SystemContext(sa, sa.getAssetManager(), bus);

        // Создаём мир (позже можно сделать загрузку мира из json/yaml/js)
        this.world = new KWorld("main");

        log.info("World created: {}", world.name());
    }

    public void setWorld(KWorld world) {
        if (isInitialized()) {
            // стоп старого и старт нового
            if (this.world != null) this.world.stop(ctx);
            this.world = world;
            if (this.world != null) this.world.start(ctx);
        } else {
            this.world = world;
        }
    }

    public KWorld getWorld() {
        return world;
    }

    @Override
    public void update(float tpf) {
        if (world != null) {
            world.update(ctx, tpf);
        }
        // Пампим события после апдейта систем
        ctx.events().pump();
    }

    @Override
    protected void cleanup(Application app) {
        if (world != null) {
            world.stop(ctx);
        }
        log.info("WorldAppState cleaned up");
    }

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}
}