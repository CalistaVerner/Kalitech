// FILE: org/foxesworld/kalitech/engine/util/LongHashMap.java
package org.foxesworld.kalitech.engine.util;

import java.util.Arrays;

/**
 * Allocation-light open-addressing hash map for long keys to object values.
 *
 * <p>Design goals:
 * <ul>
 *   <li>No boxing (Long)</li>
 *   <li>O(1) average get/put/remove</li>
 *   <li>Low allocation footprint</li>
 * </ul>
 *
 * <p>Sentinel policy:
 * <ul>
 *   <li>EMPTY key is 0L (reserved, cannot be used)</li>
 * </ul>
 *
 * <p>Not thread-safe.
 */
public final class LongHashMap<V> {

    private static final long EMPTY = 0L;
    private static final float DEFAULT_LOAD = 0.75f;
    private final float loadFactor;
    private long[] keys;
    private Object[] values;
    private int size;
    private int mask;
    private int resizeAt;

    public LongHashMap(int initialCapacityPow2) {
        this(initialCapacityPow2, DEFAULT_LOAD);
    }

    public LongHashMap(int initialCapacityPow2, float loadFactor) {
        if (!(loadFactor > 0.20f && loadFactor < 0.90f)) {
            throw new IllegalArgumentException("loadFactor must be in (0.20, 0.90)");
        }
        this.loadFactor = loadFactor;

        int cap = 1;
        while (cap < initialCapacityPow2) cap <<= 1;
        if (cap < 16) cap = 16;

        this.keys = new long[cap];
        this.values = new Object[cap];
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * loadFactor);
        this.size = 0;
    }

    private static int mix64to32(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) z;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        Arrays.fill(keys, EMPTY);
        Arrays.fill(values, null);
        size = 0;
    }

    /**
     * Returns the value for the given key, or null if not present.
     */
    @SuppressWarnings("unchecked")
    public V get(long key) {
        if (key == EMPTY) return null;

        long[] ks = keys;
        int m = mask;

        int i = mix64to32(key) & m;
        while (true) {
            long k = ks[i];
            if (k == EMPTY) return null;
            if (k == key) return (V) values[i];
            i = (i + 1) & m;
        }
    }

    /**
     * Inserts or replaces a value. Returns the previous value or null if absent.
     */
    @SuppressWarnings("unchecked")
    public V put(long key, V value) {
        if (key == EMPTY) throw new IllegalArgumentException("key=0 is reserved");
        if (size >= resizeAt) rehash(keys.length << 1);

        long[] ks = keys;
        Object[] vs = values;
        int m = mask;

        int i = mix64to32(key) & m;
        while (true) {
            long k = ks[i];
            if (k == EMPTY) {
                ks[i] = key;
                vs[i] = value;
                size++;
                return null;
            }
            if (k == key) {
                V prev = (V) vs[i];
                vs[i] = value;
                return prev;
            }
            i = (i + 1) & m;
        }
    }

    /**
     * Removes a key and returns its value, or null if absent.
     */
    @SuppressWarnings("unchecked")
    public V remove(long key) {
        if (key == EMPTY) return null;

        long[] ks = keys;
        Object[] vs = values;
        int m = mask;

        int i = mix64to32(key) & m;
        while (true) {
            long k = ks[i];
            if (k == EMPTY) return null;

            if (k == key) {
                V prev = (V) vs[i];
                deleteAndShift(i);
                size--;
                return prev;
            }

            i = (i + 1) & m;
        }
    }

    private void deleteAndShift(int deleteIndex) {
        long[] ks = keys;
        Object[] vs = values;
        int m = mask;

        int i = deleteIndex;
        int j = (i + 1) & m;

        while (true) {
            long k = ks[j];
            if (k == EMPTY) {
                ks[i] = EMPTY;
                vs[i] = null;
                return;
            }

            int home = mix64to32(k) & m;
            int dist = (j - home) & m;

            if (dist == 0) {
                ks[i] = EMPTY;
                vs[i] = null;
                return;
            }

            ks[i] = k;
            vs[i] = vs[j];

            i = j;
            j = (j + 1) & m;
        }
    }

    private void rehash(int newCap) {
        long[] oldK = keys;
        Object[] oldV = values;

        keys = new long[newCap];
        values = new Object[newCap];
        mask = newCap - 1;
        resizeAt = (int) (newCap * loadFactor);

        size = 0;

        for (int i = 0; i < oldK.length; i++) {
            long k = oldK[i];
            if (k != EMPTY) {
                @SuppressWarnings("unchecked")
                V v = (V) oldV[i];
                put(k, v);
            }
        }
    }
}