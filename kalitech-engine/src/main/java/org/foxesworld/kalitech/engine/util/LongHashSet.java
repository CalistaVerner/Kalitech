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
 *  - EMPTY key is 0L (reserved, cannot be added or contained)
 *
 * Implementation:
 *  - Open addressing + Robin Hood linear probing
 *  - Early-exit contains() using probe-distance ordering
 *  - Deletion via backward shift (no tombstones) preserves the Robin Hood invariant
 */
public final class LongHashSet implements Iterable<Long> {

    private static final long EMPTY = 0L;
    /**
     * Default load factor for open-addressing hash tables.
     *
     * <p>Open addressing suffers dramatic performance degradation when the table becomes nearly full.
     * Authoritative sources on hash tables recommend keeping the load factor well below 1.  In fact,
     * Wikipedia notes that for open addressing, the acceptable maximum load factor should be around
     * <em>0.6 to 0.75</em>【159325070506012†L340-L351】, and other research on Robin Hood hashing recommends
     * avoiding high load factors in favour of approximately 75% occupancy for consistently high
     * performance【906753401552687†L840-L844】.  Accordingly, this implementation uses a default
     * load factor of {@code 0.75f}, which strikes a balance between space usage and probing cost.
     */
    private static final float DEFAULT_LOAD = 0.75f;

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
     * Creates a new set containing all of the given keys.  This is a convenience factory that
     * pre-allocates the internal table to avoid intermediate resizes.
     *
     * @param keys keys to insert into the new set.  Keys equal to {@code 0L} are ignored because 0 is
     *             reserved as the empty sentinel.
     * @return a new {@code LongHashSet} containing all non-zero keys.
     */
    public static LongHashSet from(long... keys) {
        if (keys == null || keys.length == 0) {
            return new LongHashSet(16);
        }
        // count non-zero keys
        int needed = 0;
        for (long k : keys) {
            if (k != EMPTY) needed++;
        }
        int cap = 1;
        double required = needed / DEFAULT_LOAD;
        while (cap < required) cap <<= 1;
        LongHashSet set = new LongHashSet(cap, DEFAULT_LOAD);
        for (long k : keys) {
            if (k != EMPTY) {
                set.add(k);
            }
        }
        return set;
    }

    /**
     * Ensures that the internal table can accommodate {@code expectedSize} elements without
     * rehashing.  If the current capacity is already sufficient, this method does nothing.
     *
     * <p>Unlike {@link #ensureCapacity(int)} which grows relative to the current size,
     * this variant takes an absolute expected element count.  This is useful when constructing
     * a set from a known collection of values.
     *
     * @param expectedSize the total number of elements this set should be able to hold
     * @throws IllegalArgumentException if {@code expectedSize} is negative
     */
    public void ensureCapacityExact(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be >= 0");
        }
        int needed = (int) Math.ceil(expectedSize / loadFactor);
        int cap = table.length;
        while (cap < needed) {
            cap <<= 1;
        }
        if (cap > table.length) {
            rehash(cap);
        }
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
     * Robin Hood early-exit:
     *  - If current slot's resident has probeDistance < our probeDistance, key is not present.
     */
    public boolean contains(long k) {
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

    /**
     * Removes all keys present in {@code other} from this set.
     *
     * @param other the keys to remove
     * @return {@code true} if this set changed as a result
     */
    public boolean removeAll(LongHashSet other) {
        if (other == null || other.size == 0 || this.size == 0) return false;
        boolean modified = false;
        long[] ot = other.table;
        for (int i = 0; i < ot.length; i++) {
            long v = ot[i];
            if (v != EMPTY && remove(v)) {
                modified = true;
            }
        }
        return modified;
    }

    /**
     * Retains only those keys that are also present in {@code other}.
     *
     * <p>This operation is equivalent to computing the intersection between this set and the other
     * set.  It is implemented by building a new set of the surviving keys and swapping it with
     * {@code this} for optimal performance.
     *
     * @param other the set of keys to retain
     * @return {@code true} if this set changed as a result
     */
    public boolean retainAll(LongHashSet other) {
        if (this.size == 0) {
            return false;
        }
        if (other == null || other.size == 0) {
            boolean changed = size != 0;
            if (changed) {
                clear();
            }
            return changed;
        }
        LongHashSet result = new LongHashSet(1, this.loadFactor);
        result.ensureCapacity(this.size);
        long[] t = this.table;
        for (long v : t) {
            if (v != EMPTY && other.contains(v)) {
                result.add(v);
            }
        }
        if (result.size != this.size) {
            this.swapWith(result);
            return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if this set contains every element of the supplied set.
     *
     * @param other the set of keys to check for containment
     * @return {@code true} if {@code other} is a subset of this set, {@code false} otherwise
     */
    public boolean containsAll(LongHashSet other) {
        if (other == null || other.size == 0) return true;
        if (other.size > this.size) return false;
        long[] ot = other.table;
        for (long v : ot) {
            if (v != EMPTY && !contains(v)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if this set and {@code other} share at least one key.
     *
     * @param other the other set to test
     * @return {@code true} if there is a common element, otherwise {@code false}
     */
    public boolean containsAny(LongHashSet other) {
        if (other == null || other.size == 0 || this.size == 0) return false;
        long[] ot = other.table;
        for (long v : ot) {
            if (v != EMPTY && contains(v)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes all keys satisfying the given predicate.
     *
     * @param predicate the predicate to test keys
     * @return the number of keys removed
     */
    public int removeIf(java.util.function.LongPredicate predicate) {
        if (predicate == null) throw new NullPointerException("predicate");
        int removed = 0;
        for (int idx = 0; idx < table.length; ) {
            long v = table[idx];
            if (v != EMPTY && predicate.test(v)) {
                deleteAndShiftRobinHood(idx);
                size--;
                removed++;
                continue;
            }
            idx++;
        }
        return removed;
    }

    public long[] toArray() {
        long[] out = new long[size];
        copyTo(out, 0);
        return out;
    }

    public void forEach(LongConsumer consumer) {
        long[] t = table;
        for (long v : t) {
            if (v != EMPTY) consumer.accept(v);
        }
    }

    /**
     * Performs the given action for each key of this set using JDK {@link java.util.function.LongConsumer}.
     *
     * <p>This overload exists to avoid ambiguity when using lambdas that match both this class's
     * internal {@link LongConsumer} and {@link java.util.function.LongConsumer}.  Use this method
     * when you want to interoperate with JDK functional interfaces.
     *
     * @param consumer the action to be performed for each key
     */
    public void forEachLong(java.util.function.LongConsumer consumer) {
        if (consumer == null) throw new NullPointerException("consumer");
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
        for (long v : t) {
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
        int previousSize = this.size;
        int tm = this.mask;
        int tr = this.resizeAt;

        this.table = other.table;
        this.size = other.size;
        this.mask = other.mask;
        this.resizeAt = other.resizeAt;

        other.table = tt;
        other.size = previousSize;
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

    /**
     * Returns an iterator over the elements in this set.  The returned iterator does not allocate
     * any additional state and iterates over the internal table directly.  Removal through the
     * iterator is supported and will remove the last returned element from the set.
     *
     * <p>The order of iteration is unspecified and will correspond to the probing order in the
     * underlying hash table.  No guarantees are made as to ordering between iterations.
     *
     * @return an {@link java.util.PrimitiveIterator.OfLong} over the elements of the set
     */
    @Override
    public java.util.PrimitiveIterator.OfLong iterator() {
        return new Itr();
    }

    /**
     * Returns a {@link java.util.Spliterator.OfLong} over the elements in this set.  The
     * spliterator reports {@link java.util.Spliterator#DISTINCT}, {@link java.util.Spliterator#NONNULL}
     * and {@link java.util.Spliterator#SIZED} characteristics.  The returned spliterator is
     * fail-fast only in the sense that structural modifications made through the set's APIs
     * after obtaining the spliterator may or may not be reflected; however, since this set is
     * not thread-safe, concurrent modifications from other threads will produce unpredictable results.
     *
     * <p>The spliterator traverses the backing table directly and does not allocate auxiliary
     * collections.
     *
     * @return a spliterator over the elements of this set
     */
    @Override
    public java.util.Spliterator.OfLong spliterator() {
        return new Split(0, table.length);
    }

    /**
     * Returns a string representation of this set.  The representation consists of a comma-
     * separated list of the set's elements enclosed in square brackets.  The elements are
     * returned in the order encountered during iteration, which is unspecified.
     *
     * @return a string representation of this set
     */
    @Override
    public String toString() {
        if (size == 0) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        long[] t = table;
        for (long v : t) {
            if (v != EMPTY) {
                if (!first) sb.append(',').append(' ');
                sb.append(v);
                first = false;
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Compares the specified object with this set for equality.  Returns {@code true} if and
     * only if the specified object is also a {@code LongHashSet}, both sets have the same
     * size, and each element of the specified set is contained in this set.
     *
     * @param obj object to be compared for equality with this set
     * @return {@code true} if the specified object is equal to this set
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LongHashSet)) return false;
        LongHashSet other = (LongHashSet) obj;
        if (this.size != other.size) return false;
        return this.containsAll(other);
    }

    /**
     * Returns the hash code value for this set.  The hash code of a set is defined to be the
     * sum of the hash codes of the elements in the set, where the hash code of a long value is
     * computed as {@link java.lang.Long#hashCode(long)}.  This ensures that {@code x.equals(y)}
     * implies {@code x.hashCode() == y.hashCode()} for any two sets {@code x} and {@code y}.
     *
     * @return the hash code value for this set
     */
    @Override
    public int hashCode() {
        int h = 0;
        long[] t = table;
        for (int i = 0; i < t.length; i++) {
            long v = t[i];
            if (v != EMPTY) h += Long.hashCode(v);
        }
        return h;
    }

    // -----------------------------------------------------------------------
    // Iterator and Spliterator implementations
    // -----------------------------------------------------------------------

    /**
     * Iterator over the set that directly traverses the backing array.  Supports element
     * removal via the iterator's {@link java.util.Iterator#remove()} method.
     */
    private final class Itr implements java.util.PrimitiveIterator.OfLong {
        private int index;
        private long next;
        private boolean hasNext;
        private long current;
        private boolean canRemove;

        Itr() {
            this.index = -1;
            advance();
        }

        /**
         * Advances to the next non-empty slot and updates next/hasNext.
         */
        private void advance() {
            long[] t = table;
            int len = t.length;
            while (++index < len) {
                long v = t[index];
                if (v != EMPTY) {
                    next = v;
                    hasNext = true;
                    return;
                }
            }
            hasNext = false;
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public long nextLong() {
            if (!hasNext) throw new java.util.NoSuchElementException();
            current = next;
            canRemove = true;
            advance();
            return current;
        }

        @Override
        public Long next() {
            return nextLong();
        }

        @Override
        public void remove() {
            if (!canRemove) throw new IllegalStateException();
            // Remove via hash-based removal to maintain Robin Hood invariant.
            LongHashSet.this.remove(current);
            // reset iterator state: we removed current, so start searching from previous index
            // (decrement index to revisit shifted entries).
            index -= 1;
            canRemove = false;
            // Recompute next; but note that remove calls may shrink size and shift elements.
            advance();
        }
    }

    /**
     * Spliterator over the elements of the set.  Splits roughly in half by dividing the
     * underlying array range.  The spliterator is designed for use in parallel streams and
     * supports the {@link java.util.Spliterator#DISTINCT}, {@link java.util.Spliterator#NONNULL},
     * and {@link java.util.Spliterator#SIZED} characteristics.
     */
    private final class Split implements java.util.Spliterator.OfLong {
        private final int fence;
        private int index;
        private int est;

        Split(int origin, int fence) {
            this.index = origin;
            this.fence = fence;
            this.est = LongHashSet.this.size;
        }

        @Override
        public java.util.Spliterator.OfLong trySplit() {
            int lo = index;
            int mid = (lo + fence) >>> 1;
            if (lo >= mid) return null;
            // Move mid forward to avoid splitting inside a cluster of EMPTYs; this is heuristic
            int i = mid;
            long[] t = table;
            while (i < fence && t[i] == EMPTY) {
                i++;
            }
            int splitPos = (i < fence) ? i : mid;
            Split split = new Split(lo, splitPos);
            this.index = splitPos;
            return split;
        }

        @Override
        public boolean tryAdvance(java.util.function.LongConsumer action) {
            if (action == null) throw new NullPointerException();
            long[] t = table;
            while (index < fence) {
                long v = t[index++];
                if (v != EMPTY) {
                    action.accept(v);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void forEachRemaining(java.util.function.LongConsumer action) {
            if (action == null) throw new NullPointerException();
            long[] t = table;
            for (int i = index; i < fence; i++) {
                long v = t[i];
                if (v != EMPTY) action.accept(v);
            }
            index = fence;
        }

        @Override
        public long estimateSize() {
            return est < 0 ? LongHashSet.this.size : est;
        }

        @Override
        public int characteristics() {
            return java.util.Spliterator.DISTINCT | java.util.Spliterator.NONNULL | java.util.Spliterator.SIZED;
        }
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
     * Robin Hood backward-shift deletion.
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