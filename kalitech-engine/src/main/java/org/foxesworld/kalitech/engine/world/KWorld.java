package org.foxesworld.kalitech.engine.world;

import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class KWorld {

    private final String name;

    private final List<Entry> systems = new ArrayList<>();
    private boolean started = false;

    public KWorld(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void addSystem(KSystem system) {
        addSystem(system, 0);
    }

    public void addSystem(KSystem system, int order) {
        systems.add(new Entry(system, order));
        if (started) {
            // Если мир уже стартовал и ты добавляешь систему на лету — можно решить:
            // либо запрещать, либо стартовать сразу. Я бы запрещал.
            throw new IllegalStateException("Cannot add system after world started");
        }
    }

    public void start(SystemContext ctx) {
        if (started) return;
        systems.sort(Comparator.comparingInt(e -> e.order));
        for (Entry e : systems) e.system.onStart(ctx);
        started = true;
    }

    public void update(SystemContext ctx, float tpf) {
        if (!started) start(ctx);
        for (Entry e : systems) e.system.onUpdate(ctx, tpf);
    }

    public void stop(SystemContext ctx) {
        if (!started) return;
        for (int i = systems.size() - 1; i >= 0; i--) {
            systems.get(i).system.onStop(ctx);
        }
        started = false;
    }

    private static final class Entry {
        final KSystem system;
        final int order;
        Entry(KSystem system, int order) {
            this.system = system;
            this.order = order;
        }
    }
}