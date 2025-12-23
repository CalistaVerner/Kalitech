package org.foxesworld.kalitech.engine.world;

import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

import java.util.ArrayList;
import java.util.List;

public final class KWorld {

    private final String name;
    private final List<KSystem> systems = new ArrayList<>();
    private boolean started = false;

    public KWorld(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void addSystem(KSystem system) {
        systems.add(system);
    }

    public void start(SystemContext ctx) {
        if (started) return;
        for (KSystem s : systems) s.onStart(ctx);
        started = true;
    }

    public void update(SystemContext ctx, float tpf) {
        if (!started) start(ctx);
        for (KSystem s : systems) s.onUpdate(ctx, tpf);
    }

    public void stop(SystemContext ctx) {
        if (!started) return;
        for (int i = systems.size() - 1; i >= 0; i--) {
            systems.get(i).onStop(ctx);
        }
        started = false;
    }
}