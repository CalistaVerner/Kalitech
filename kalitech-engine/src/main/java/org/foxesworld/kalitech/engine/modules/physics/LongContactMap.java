// FILE: org/foxesworld/kalitech/engine/modules/physics/LongContactMap.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import java.util.Arrays;

/**
 * Open-addressing long->ContactAgg map (no boxing).
 * Uses 0 as EMPTY sentinel in keys table.
 */
public final class LongContactMap {
    private static final long EMPTY = 0L;

    private long[] keys;
    private ContactAgg[] values;
    private int size;
    private int mask;
    private int resizeAt;

    public LongContactMap(int initialCapacityPow2) {
        int cap = 1;
        while (cap < initialCapacityPow2) cap <<= 1;
        if (cap < 16) cap = 16;

        keys = new long[cap];
        values = new ContactAgg[cap];
        mask = cap - 1;
        resizeAt = (int) (cap * 0.65f);
        size = 0;
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

    public void clear() {
        Arrays.fill(keys, EMPTY);
        // keep values array to reuse ContactAgg instances
        size = 0;
    }

    public ContactAgg getOrCreate(long k) {
        if (k == EMPTY) return null;
        if (size >= resizeAt) rehash(keys.length << 1);

        int i = mix64to32(k) & mask;
        while (true) {
            long kk = keys[i];
            if (kk == EMPTY) {
                keys[i] = k;

                ContactAgg a = values[i];
                if (a == null || a.getPairKey() != k) {
                    a = new ContactAgg(k);
                    values[i] = a;
                } else {
                    a.clear();
                }

                size++;
                return a;
            }
            if (kk == k) {
                ContactAgg a = values[i];
                if (a == null || a.getPairKey() != k) {
                    a = new ContactAgg(k);
                    values[i] = a;
                }
                return a;
            }
            i = (i + 1) & mask;
        }
    }

    public ContactAgg get(long k) {
        if (k == EMPTY) return null;
        int i = mix64to32(k) & mask;
        while (true) {
            long kk = keys[i];
            if (kk == EMPTY) return null;
            if (kk == k) return values[i];
            i = (i + 1) & mask;
        }
    }

    private void rehash(int newCap) {
        long[] ok = keys;
        ContactAgg[] ov = values;

        long[] nk = new long[newCap];
        ContactAgg[] nv = new ContactAgg[newCap];
        int nm = newCap - 1;

        for (int i = 0; i < ok.length; i++) {
            long k = ok[i];
            if (k == EMPTY) continue;

            int idx = mix64to32(k) & nm;
            while (nk[idx] != EMPTY) idx = (idx + 1) & nm;
            nk[idx] = k;
            nv[idx] = ov[i];
        }

        keys = nk;
        values = nv;
        mask = nm;
        resizeAt = (int) (newCap * 0.65f);
        // size unchanged
    }
}