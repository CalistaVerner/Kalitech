package org.foxesworld.kalitech.engine.ecs;

import java.util.BitSet;

/**
 * Dense entity id allocator with liveness tracking.
 *
 * <p>Ids are small positive ints (1...). Id reuse is supported via a free list.
 * A monotonically increasing generation counter per id is maintained to enable
 * stale-reference checks in internal systems.
 */
public final class EntityManager {

    private int nextId = 1;
    private final BitSet alive = new BitSet();

    // free-list without boxing
    private int[] free = new int[256];
    private int freeSize = 0;

    // generation per entityId (0 by default)
    private int[] gen = new int[0];

    private static int nextPow2(int v) {
        int x = 1;
        while (x < v) x <<= 1;
        return x;
    }

    public boolean isAlive(int id) {
        return id > 0 && alive.get(id);
    }

    int create() {
        final int id;
        if (freeSize > 0) {
            id = free[--freeSize];
        } else {
            id = nextId++;
        }
        ensureGenCapacity(id);
        alive.set(id);
        return id;
    }

    /**
     * Returns the current generation for the entity id.
     *
     * <p>Generation is an internal-only concept. Use it only for stale-reference checks.
     */
    public int generationOf(int id) {
        if (id <= 0 || id >= gen.length) return 0;
        return gen[id];
    }

    /**
     * Returns true if the entity is alive and the generation matches.
     */
    public boolean isAlive(int id, int generation) {
        return isAlive(id) && generationOf(id) == generation;
    }

    void destroy(int id) {
        if (id <= 0) throw new IllegalArgumentException("entityId must be > 0");
        if (!alive.get(id)) throw new IllegalStateException("entityId is not alive: " + id);

        alive.clear(id);

        ensureGenCapacity(id);
        gen[id] = gen[id] + 1;
        if (gen[id] == 0) gen[id] = 1; // avoid wrap to 0 on overflow

        if (freeSize == free.length) {
            int[] n = new int[free.length << 1];
            System.arraycopy(free, 0, n, 0, free.length);
            free = n;
        }
        free[freeSize++] = id;
    }

    /**
     * Full reset for hot-reload rebuilds.
     */
    void reset() {
        alive.clear();
        nextId = 1;
        freeSize = 0;
        gen = new int[0];
    }

    private void ensureGenCapacity(int id) {
        if (id < gen.length) return;
        int newCap = Math.max(16, nextPow2(id + 1));
        int[] n = new int[newCap];
        System.arraycopy(gen, 0, n, 0, gen.length);
        gen = n;
    }
}