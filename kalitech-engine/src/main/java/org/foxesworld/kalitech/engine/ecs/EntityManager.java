package org.foxesworld.kalitech.engine.ecs;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

public final class EntityManager {

    private final AtomicInteger nextId = new AtomicInteger(1);
    private final BitSet alive = new BitSet();

    public int create() {
        int id = nextId.getAndIncrement();
        alive.set(id);
        return id;
    }

    public boolean isAlive(int id) {
        return id > 0 && alive.get(id);
    }

    public void destroy(int id) {
        if (id > 0) alive.clear(id);
    }
}