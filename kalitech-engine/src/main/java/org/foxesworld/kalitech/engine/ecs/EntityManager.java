package org.foxesworld.kalitech.engine.ecs;

import java.util.BitSet;

public final class EntityManager {

    private int nextId = 1;
    private final BitSet alive = new BitSet();

    // free-list without boxing
    private int[] free = new int[256];
    private int freeSize = 0;

    public int create() {
        final int id;
        if (freeSize > 0) {
            id = free[--freeSize];
        } else {
            id = nextId++;
        }
        alive.set(id);
        return id;
    }

    public boolean isAlive(int id) {
        return id > 0 && alive.get(id);
    }

    public void destroy(int id) {
        if (id <= 0) return;
        if (!alive.get(id)) return;

        alive.clear(id);

        // push into free-list
        if (freeSize == free.length) {
            int[] n = new int[free.length << 1];
            System.arraycopy(free, 0, n, 0, free.length);
            free = n;
        }
        free[freeSize++] = id;
    }

    /** Full reset for hot-reload rebuilds. */
    public void reset() {
        alive.clear();
        nextId = 1;
        freeSize = 0;
    }
}