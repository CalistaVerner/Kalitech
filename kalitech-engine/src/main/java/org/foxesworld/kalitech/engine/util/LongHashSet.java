// FILE: LongHashSet.java
package org.foxesworld.kalitech.engine.util;

import java.util.Arrays;

/**
 * Allocation-light open-addressing hash set for long keys.
 * - No boxing (Long)
 * - No Iterator allocations
 * - O(1) average add/contains
 *
 * Not thread-safe.
 */
public final class LongHashSet {

    private static final long EMPTY = 0L;

    private long[] table;
    private int size;
    private int mask;
    private int resizeAt;

    public LongHashSet(int initialCapacityPow2) {
        int cap = 1;
        while (cap < initialCapacityPow2) cap <<= 1;
        if (cap < 16) cap = 16;

        this.table = new long[cap];
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * 0.65f);
        this.size = 0;
    }

    public int size() { return size; }

    public void clear() {
        // O(n) but no allocations
        Arrays.fill(table, EMPTY);
        size = 0;
    }

    public boolean contains(long k) {
        if (k == EMPTY) return true; // we allow EMPTY as "always present" in contains semantics
        long[] t = table;
        int m = mask;
        int i = mix64to32(k) & m;

        while (true) {
            long v = t[i];
            if (v == EMPTY) return false;
            if (v == k) return true;
            i = (i + 1) & m;
        }
    }

    /** @return true if added (was not present) */
    public boolean add(long k) {
        if (k == EMPTY) return false; // reserved sentinel; our pairKey never becomes 0 if we build it right
        if (size >= resizeAt) rehash(table.length << 1);

        long[] t = table;
        int m = mask;
        int i = mix64to32(k) & m;

        while (true) {
            long v = t[i];
            if (v == EMPTY) {
                t[i] = k;
                size++;
                return true;
            }
            if (v == k) return false;
            i = (i + 1) & m;
        }
    }

    /**
     * Call consumer for each key. No iterator objects.
     */
    public void forEach(LongConsumer consumer) {
        long[] t = table;
        for (int i = 0; i < t.length; i++) {
            long v = t[i];
            if (v != EMPTY) consumer.accept(v);
        }
    }

    /**
     * Rehash into larger table.
     */
    private void rehash(int newCap) {
        long[] old = this.table;
        long[] nt = new long[newCap];
        int nm = newCap - 1;

        for (int i = 0; i < old.length; i++) {
            long k = old[i];
            if (k == EMPTY) continue;

            int idx = mix64to32(k) & nm;
            while (nt[idx] != EMPTY) idx = (idx + 1) & nm;
            nt[idx] = k;
        }

        this.table = nt;
        this.mask = nm;
        this.resizeAt = (int) (newCap * 0.65f);
        // size unchanged
    }

    /**
     * Fast mixing: 64 -> 32 bits.
     */
    private static int mix64to32(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) z;
    }

    @FunctionalInterface
    public interface LongConsumer {
        void accept(long value);
    }
}