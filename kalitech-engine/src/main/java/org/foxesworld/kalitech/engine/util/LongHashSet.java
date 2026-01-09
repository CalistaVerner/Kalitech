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

    /**
     * @return number of stored keys
     */
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * @return backing table length (power-of-two)
     */
    public int capacity() {
        return table.length;
    }

    public float loadFactor() {
        return loadFactor;
    }

    /**
     * Clears set. O(n) fill, no allocations.
     * Use clear() for predictable behavior.
     */
    public void clear() {
        Arrays.fill(table, EMPTY);
        size = 0;
    }

    /**
     * Legacy contains semantics: contains(0) == true.
     * (This matches your original behavior.)
     */
    public boolean contains(long k) {
        if (k == EMPTY) return true;
        return containsStrict(k);
    }

    /**
     * Sane contains semantics: containsStrict(0) == false.
     */
    public boolean containsStrict(long k) {
        if (k == EMPTY) return false;

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

    /**
     * @return true if added (was not present)
     */
    public boolean add(long k) {
        if (k == EMPTY) return false; // reserved sentinel
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
     * Remove key if present.
     * Uses back-shift deletion (no tombstones).
     *
     * @return true if removed
     */
    public boolean remove(long k) {
        if (k == EMPTY) return false;

        long[] t = table;
        int m = mask;

        int i = mix64to32(k) & m;
        while (true) {
            long v = t[i];
            if (v == EMPTY) return false;
            if (v == k) {
                deleteAndShift(i);
                size--;
                return true;
            }
            i = (i + 1) & m;
        }
    }

    /**
     * Ensure the set can add at least {@code additional} new distinct keys without rehash.
     */
    public void ensureCapacity(int additional) {
        if (additional <= 0) return;
        int need = size + additional;
        if (need < resizeAt) return;

        int cap = table.length;
        while (need >= (int) (cap * loadFactor)) cap <<= 1;
        if (cap != table.length) rehash(cap);
    }

    /**
     * Add all keys from another LongHashSet.
     * No allocations.
     */
    public void addAll(LongHashSet other) {
        if (other == null || other.size == 0) return;
        ensureCapacity(other.size);

        long[] ot = other.table;
        for (int i = 0; i < ot.length; i++) {
            long v = ot[i];
            if (v != EMPTY) add(v);
        }
    }

    /**
     * Add all keys from an array.
     */
    public void addAll(long[] keys) {
        if (keys == null || keys.length == 0) return;
        ensureCapacity(keys.length);
        for (long k : keys) add(k);
    }

    /**
     * Copy keys into a new array.
     */
    public long[] toArray() {
        long[] out = new long[size];
        copyTo(out, 0);
        return out;
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
     * Copy keys into target array starting at offset.
     *
     * @return number of copied keys
     */
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

    /**
     * Swap all internals with another set (O(1)).
     * Useful for ping-pong (curr/prev) structures in tight loops.
     */
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
     * Rehash into newCap table (power of two).
     */
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

            int idx = mix64to32(k) & nm;
            while (nt[idx] != EMPTY) idx = (idx + 1) & nm;
            nt[idx] = k;
        }

        this.table = nt;
        this.mask = nm;
        this.resizeAt = (int) (cap * loadFactor);
        // size unchanged
    }

    /**
     * Back-shift deletion for linear probing.
     * Keeps cluster intact without tombstones.
     */
    private void deleteAndShift(int deleteIndex) {
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

            // ideal slot for v
            int ideal = mix64to32(v) & m;

            // If v is in a probe sequence that would have visited i, shift it back.
            // This condition is the standard "is ideal in (i, j] circular interval?" negation.
            if (distance(ideal, j, m) >= distance(ideal, i, m)) {
                t[i] = v;
                i = j;
            }

            j = (j + 1) & m;
        }
    }

    @FunctionalInterface
    public interface LongConsumer {
        void accept(long value);
    }
}