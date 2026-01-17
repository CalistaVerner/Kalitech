// FILE: org/foxesworld/kalitech/engine/modules/physics/util/IdentityObjectIntMap.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

import java.util.Objects;

/**
 * Open-addressing identity Object -> int map (no boxing).
 *
 * <p>Keys are compared by reference (==). Null keys are not allowed.
 * Value 0 represents "missing".</p>
 */
public final class IdentityObjectIntMap {

    private static final Object EMPTY = null;
    private static final Object DELETED = new Object();

    private static final float LOAD_FACTOR = 0.65f;
    private static final float TOMBSTONE_FACTOR = 0.20f;

    private Object[] keys;
    private int[] values;

    private int size;
    private int used;
    private int mask;
    private int resizeAt;

    public IdentityObjectIntMap() {
        this(1024);
    }

    public IdentityObjectIntMap(int initialCapacity) {
        int cap = tableSizeFor(Math.max(16, initialCapacity));
        this.keys = new Object[cap];
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

    public int getOrZero(Object key) {
        int idx = findIndex(key);
        return idx >= 0 ? values[idx] : 0;
    }

    public void put(Object key, int value) {
        Objects.requireNonNull(key, "key");

        if (used >= resizeAt) {
            rehash(keys.length << 1);
        } else if (tombstonePressureHigh()) {
            rehash(keys.length);
        }

        int idx = findSlotForInsert(key);
        Object k = keys[idx];

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

    // ----------------- internals -----------------

    public boolean removeIfEquals(Object key, int expectedValue) {
        Objects.requireNonNull(key, "key");
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

    public void clear() {
        for (int i = 0; i < keys.length; i++) {
            keys[i] = EMPTY;
            values[i] = 0;
        }
        size = 0;
        used = 0;
    }

    private boolean tombstonePressureHigh() {
        int tomb = used - size;
        return tomb > (int) (keys.length * TOMBSTONE_FACTOR);
    }

    private int findIndex(Object key) {
        Objects.requireNonNull(key, "key");

        int idx = mix32(System.identityHashCode(key)) & mask;
        while (true) {
            Object k = keys[idx];
            if (k == EMPTY) return -1;
            if (k == key) return idx;
            idx = (idx + 1) & mask;
        }
    }

    private int findSlotForInsert(Object key) {
        int idx = mix32(System.identityHashCode(key)) & mask;
        int firstDeleted = -1;

        while (true) {
            Object k = keys[idx];

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

        Object[] oldK = this.keys;
        int[] oldV = this.values;

        this.keys = new Object[cap];
        this.values = new int[cap];
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * LOAD_FACTOR);

        this.size = 0;
        this.used = 0;

        for (int i = 0; i < oldK.length; i++) {
            Object k = oldK[i];
            if (k == EMPTY || k == DELETED) continue;

            int v = oldV[i];
            int idx = findSlotForInsertRehash(k);

            keys[idx] = k;
            values[idx] = v;

            size++;
            used++;
        }
    }

    private int findSlotForInsertRehash(Object key) {
        int idx = mix32(System.identityHashCode(key)) & mask;
        while (true) {
            Object k = keys[idx];
            if (k == EMPTY) return idx;
            idx = (idx + 1) & mask;
        }
    }
}