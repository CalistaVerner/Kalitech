// FILE: org/foxesworld/kalitech/engine/modules/physics/util/IntIntMap.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

/**
 * Open-addressing primitive int -> int map (no boxing).
 *
 * <p>Uses 0 as EMPTY sentinel for keys and values. Keys must be positive (> 0).
 * Values may be 0 to represent "missing" semantics.</p>
 */
public final class IntIntMap {

    private static final int EMPTY = 0;
    private static final int DELETED = Integer.MIN_VALUE;

    private static final float LOAD_FACTOR = 0.65f;
    private static final float TOMBSTONE_FACTOR = 0.20f;

    private int[] keys;
    private int[] values;

    private int size;
    private int used;
    private int mask;
    private int resizeAt;

    public IntIntMap() {
        this(256);
    }

    public IntIntMap(int initialCapacity) {
        int cap = tableSizeFor(Math.max(16, initialCapacity));
        this.keys = new int[cap];
        this.values = new int[cap];
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * LOAD_FACTOR);
    }

    private static int mix32(int x) {
        x ^= (x >>> 16);
        x *= 0x7feb352d;
        x ^= (x >>> 15);
        x *= 0x846ca68b;
        x ^= (x >>> 16);
        return x;
    }

    private static int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 16) ? 16 : (n + 1);
    }

    public int size() {
        return size;
    }

    public int getOrZero(int key) {
        int idx = findIndex(key);
        return idx >= 0 ? values[idx] : 0;
    }

    public void put(int key, int value) {
        requireValidKey(key);

        if (used >= resizeAt) {
            rehash(keys.length << 1);
        } else if (tombstonePressureHigh()) {
            rehash(keys.length);
        }

        int idx = findSlotForInsert(key);
        int k = keys[idx];

        if (k == key) {
            values[idx] = value;
            return;
        }

        if (k == EMPTY) {
            used++;
        }

        keys[idx] = key;
        values[idx] = value;
        size++;
    }

    public boolean removeIfEquals(int key, int expectedValue) {
        requireValidKey(key);
        int idx = findIndex(key);
        if (idx < 0) return false;
        if (values[idx] != expectedValue) return false;

        keys[idx] = DELETED;
        values[idx] = 0;
        size--;

        if (tombstonePressureHigh()) {
            rehash(keys.length);
        }
        return true;
    }

    // ----------------- internals -----------------

    public void remove(int key) {
        requireValidKey(key);
        int idx = findIndex(key);
        if (idx < 0) return;

        keys[idx] = DELETED;
        values[idx] = 0;
        size--;

        if (tombstonePressureHigh()) {
            rehash(keys.length);
        }
    }

    public void clear() {
        for (int i = 0; i < keys.length; i++) {
            keys[i] = EMPTY;
            values[i] = 0;
        }
        size = 0;
        used = 0;
    }

    private void requireValidKey(int key) {
        if (key <= 0 || key == DELETED) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
    }

    private boolean tombstonePressureHigh() {
        int tomb = used - size;
        return tomb > (int) (keys.length * TOMBSTONE_FACTOR);
    }

    private int findIndex(int key) {
        requireValidKey(key);

        int idx = mix32(key) & mask;
        while (true) {
            int k = keys[idx];
            if (k == EMPTY) return -1;
            if (k == key) return idx;
            idx = (idx + 1) & mask;
        }
    }

    private int findSlotForInsert(int key) {
        int idx = mix32(key) & mask;
        int firstDeleted = -1;

        while (true) {
            int k = keys[idx];

            if (k == EMPTY) {
                return (firstDeleted >= 0) ? firstDeleted : idx;
            }
            if (k == key) {
                return idx;
            }
            if (k == DELETED && firstDeleted < 0) {
                firstDeleted = idx;
            }

            idx = (idx + 1) & mask;
        }
    }

    private void rehash(int newCapacity) {
        int cap = tableSizeFor(newCapacity);

        int[] oldK = this.keys;
        int[] oldV = this.values;

        this.keys = new int[cap];
        this.values = new int[cap];
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * LOAD_FACTOR);

        this.size = 0;
        this.used = 0;

        for (int i = 0; i < oldK.length; i++) {
            int k = oldK[i];
            if (k == EMPTY || k == DELETED) continue;

            int v = oldV[i];
            int idx = findSlotForInsertRehash(k);

            keys[idx] = k;
            values[idx] = v;

            size++;
            used++;
        }
    }

    private int findSlotForInsertRehash(int key) {
        int idx = mix32(key) & mask;
        while (true) {
            int k = keys[idx];
            if (k == EMPTY) return idx;
            idx = (idx + 1) & mask;
        }
    }
}