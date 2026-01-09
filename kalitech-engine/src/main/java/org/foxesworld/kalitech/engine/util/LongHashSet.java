package org.foxesworld.kalitech.engine.util;

import java.util.Arrays;

/**
 * Allocation-light open-addressing hash set for long keys.
 * - No boxing (Long)
 * - No Iterator allocations
 * - O(1) average add/contains/remove
 *
 * Not thread-safe.
 *
 * Sentinel policy:
 *  - EMPTY key is 0L (reserved, cannot be added)
 *  - For backwards compatibility, contains(0) returns true (legacy behavior).
 *    Use containsStrict(k) if you want sane semantics for 0.
 *
 * Implementation:
 *  - Open addressing + Robin Hood linear probing
 *  - Early-exit contains() using probe-distance ordering
 *  - Deletion via backward shift (no tombstones) compatible with Robin Hood invariant
 */
public final class LongHashSet {

    private static final long EMPTY = 0L;
    private static final float DEFAULT_LOAD = 0.65f;

    private long[] table;
    private int size;
    private int mask;
    private int resizeAt;
    private final float loadFactor;

    public LongHashSet(int initialCapacityPow2) {
        this(initialCapacityPow2, DEFAULT_LOAD);
    }

    public LongHashSet(int initialCapacityPow2, float loadFactor) {
        if (!(loadFactor > 0.20f && loadFactor < 0.90f)) {
            throw new IllegalArgumentException("loadFactor must be in (0.20, 0.90)");
        }
        this.loadFactor = loadFactor;

        int cap = 1;
        while (cap < initialCapacityPow2) cap <<= 1;
        if (cap < 16) cap = 16;

        this.table = new long[cap];
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * loadFactor);
        this.size = 0;
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

    /**
     * Circular distance from start to pos in [0..mask].
     */
    private static int distance(int start, int pos, int mask) {
        return (pos - start) & mask;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int capacity() {
        return table.length;
    }

    public float loadFactor() {
        return loadFactor;
    }

    public void clear() {
        Arrays.fill(table, EMPTY);
        size = 0;
    }

    /**
     * Legacy contains semantics: contains(0) == true.
     */
    public boolean contains(long k) {
        if (k == EMPTY) return true;
        return containsStrict(k);
    }

    /**
     * Sane contains semantics: containsStrict(0) == false.
     *
     * Robin Hood early-exit:
     *  - If current slot's resident has probeDistance < our probeDistance, key is not present.
     */
    public boolean containsStrict(long k) {
        if (k == EMPTY) return false;

        long[] t = table;
        int m = mask;

        int home = mix64to32(k) & m;
        int i = home;
        int pd = 0;

        while (true) {
            long v = t[i];
            if (v == EMPTY) return false;
            if (v == k) return true;

            int vHome = mix64to32(v) & m;
            int vPd = distance(vHome, i, m);

            if (vPd < pd) return false;

            i = (i + 1) & m;
            pd++;
        }
    }

    public boolean add(long k) {
        if (k == EMPTY) return false;
        if (size >= resizeAt) rehash(table.length << 1);

        long[] t = table;
        int m = mask;

        int home = mix64to32(k) & m;
        int i = home;
        int pd = 0;

        long cur = k;
        int curHome = home;

        while (true) {
            long v = t[i];

            if (v == EMPTY) {
                t[i] = cur;
                size++;
                return true;
            }

            if (v == cur) return false;

            int vHome = mix64to32(v) & m;
            int vPd = distance(vHome, i, m);

            // Robin Hood: steal if we are "poorer" (have larger probe distance)
            if (vPd < pd) {
                t[i] = cur;
                cur = v;

                curHome = vHome;
                pd = vPd;
            }

            i = (i + 1) & m;
            pd++;
        }
    }

    public boolean remove(long k) {
        if (k == EMPTY) return false;

        long[] t = table;
        int m = mask;

        int home = mix64to32(k) & m;
        int i = home;
        int pd = 0;

        while (true) {
            long v = t[i];
            if (v == EMPTY) return false;
            if (v == k) {
                deleteAndShiftRobinHood(i);
                size--;
                return true;
            }

            int vHome = mix64to32(v) & m;
            int vPd = distance(vHome, i, m);

            if (vPd < pd) return false;

            i = (i + 1) & m;
            pd++;
        }
    }

    public void ensureCapacity(int additional) {
        if (additional <= 0) return;
        int need = size + additional;
        if (need < resizeAt) return;

        int cap = table.length;
        while (need >= (int) (cap * loadFactor)) cap <<= 1;
        if (cap != table.length) rehash(cap);
    }

    public void addAll(LongHashSet other) {
        if (other == null || other.size == 0) return;
        ensureCapacity(other.size);

        long[] ot = other.table;
        for (int i = 0; i < ot.length; i++) {
            long v = ot[i];
            if (v != EMPTY) add(v);
        }
    }

    public void addAll(long[] keys) {
        if (keys == null || keys.length == 0) return;
        ensureCapacity(keys.length);
        for (long k : keys) add(k);
    }

    public long[] toArray() {
        long[] out = new long[size];
        copyTo(out, 0);
        return out;
    }

    public void forEach(LongConsumer consumer) {
        long[] t = table;
        for (int i = 0; i < t.length; i++) {
            long v = t[i];
            if (v != EMPTY) consumer.accept(v);
        }
    }

    public int copyTo(long[] out, int offset) {
        if (out == null) throw new NullPointerException("out");
        if (offset < 0 || offset > out.length) throw new IndexOutOfBoundsException("offset");

        int p = offset;
        long[] t = table;
        for (int i = 0; i < t.length; i++) {
            long v = t[i];
            if (v != EMPTY) {
                if (p >= out.length) throw new IndexOutOfBoundsException("out too small");
                out[p++] = v;
            }
        }
        return p - offset;
    }

    public void swapWith(LongHashSet other) {
        if (other == null) throw new NullPointerException("other");

        long[] tt = this.table;
        int ts = this.size;
        int tm = this.mask;
        int tr = this.resizeAt;

        this.table = other.table;
        this.size = other.size;
        this.mask = other.mask;
        this.resizeAt = other.resizeAt;

        other.table = tt;
        other.size = ts;
        other.mask = tm;
        other.resizeAt = tr;
    }

    /**
     * Optional: shrink to minimal capacity that can hold current size under loadFactor.
     * Useful after big spikes.
     */
    public void trimToSize() {
        int minCap = 16;
        int need = (int) Math.ceil(size / loadFactor);
        int cap = 1;
        while (cap < need) cap <<= 1;
        if (cap < minCap) cap = minCap;
        if (cap < table.length) rehash(cap);
    }

    private void rehash(int newCap) {
        int cap = 1;
        while (cap < newCap) cap <<= 1;
        if (cap < 16) cap = 16;

        long[] old = this.table;
        long[] nt = new long[cap];
        int nm = cap - 1;

        for (int i = 0; i < old.length; i++) {
            long k = old[i];
            if (k == EMPTY) continue;

            // Insert using Robin Hood into the new table
            int home = mix64to32(k) & nm;
            int idx = home;
            int pd = 0;

            long cur = k;
            while (true) {
                long v = nt[idx];
                if (v == EMPTY) {
                    nt[idx] = cur;
                    break;
                }
                if (v == cur) break;

                int vHome = mix64to32(v) & nm;
                int vPd = distance(vHome, idx, nm);

                if (vPd < pd) {
                    nt[idx] = cur;
                    cur = v;
                    pd = vPd;
                }

                idx = (idx + 1) & nm;
                pd++;
            }
        }

        this.table = nt;
        this.mask = nm;
        this.resizeAt = (int) (cap * loadFactor);
    }

    /**
     * Robin Hood compatible backward shift deletion.
     * After clearing a slot, shift subsequent entries left while their probe distance > 0.
     */
    private void deleteAndShiftRobinHood(int deleteIndex) {
        long[] t = table;
        int m = mask;

        int i = deleteIndex;
        int j = (i + 1) & m;

        while (true) {
            long v = t[j];
            if (v == EMPTY) {
                t[i] = EMPTY;
                return;
            }

            int vHome = mix64to32(v) & m;
            int vPd = distance(vHome, j, m);

            // If resident is at its home (pd == 0), cluster boundary -> stop.
            if (vPd == 0) {
                t[i] = EMPTY;
                return;
            }

            // shift left
            t[i] = v;
            i = j;
            j = (j + 1) & m;
        }
    }

    @FunctionalInterface
    public interface LongConsumer {
        void accept(long value);
    }
}