// FILE: org/foxesworld/kalitech/engine/modules/physics/util/LongContactMap.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Open-addressing primitive long -> value map (no boxing).
 *
 * <p>Uses {@code 0L} as EMPTY sentinel and {@code Long.MIN_VALUE} as DELETED sentinel.
 * Keys must never be {@code 0L} or {@code Long.MIN_VALUE}.</p>
 *
 * <p>Iteration is allocation-light: iterator reuses a single mutable {@link Entry} instance.</p>
 */
public final class LongContactMap<T> {

    private static final long EMPTY = 0L;
    private static final long DELETED = Long.MIN_VALUE;

    private static final float LOAD_FACTOR = 0.65f;
    private static final float TOMBSTONE_FACTOR = 0.20f;

    private long[] keys;
    private final EntriesIterable entriesIterable = new EntriesIterable();
    private Object[] values;
    private int size;       // live entries
    private int mask;
    private int resizeAt;
    private int used;       // live + tombstones

    public LongContactMap() {
        this(256);
    }

    public LongContactMap(int initialCapacity) {
        int cap = tableSizeFor(Math.max(16, initialCapacity));
        this.keys = new long[cap];
        this.values = new Object[cap];
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * LOAD_FACTOR);
    }

    public int size() {
        return size;
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

    public boolean contains(long key) {
        return findIndex(key) >= 0;
    }

    @SuppressWarnings("unchecked")
    public T get(long key) {
        int idx = findIndex(key);
        return idx >= 0 ? (T) values[idx] : null;
    }

    public void put(long key, T value) {
        requireValidKey(key);
        if (value == null) throw new NullPointerException("value");

        if (used >= resizeAt) {
            rehash(keys.length << 1);
        } else if (tombstonePressureHigh()) {
            rehash(keys.length);
        }

        int idx = findSlotForInsert(key);
        long k = keys[idx];

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

    @SuppressWarnings("unchecked")
    public T remove(long key) {
        requireValidKey(key);
        int idx = findIndex(key);
        if (idx < 0) return null;

        T old = (T) values[idx];
        keys[idx] = DELETED;
        values[idx] = null;

        size--;

        if (tombstonePressureHigh()) {
            rehash(keys.length);
        }

        return old;
    }

    public void clear() {
        int cap = keys.length;
        for (int i = 0; i < cap; i++) {
            keys[i] = EMPTY;
            values[i] = null;
        }
        size = 0;
        used = 0;
    }

    /**
     * Iterates over all live entries.
     * Iterator reuses a single mutable {@link Entry} instance.
     */
    public Iterable<Entry<T>> entries() {
        return entriesIterable;
    }

    /**
     * Removes entries for which {@code liveness.isAlive(value)} returns false.
     */
    public void sweep(ContactLiveness<T> liveness) {
        Objects.requireNonNull(liveness, "liveness");

        boolean removedAny = false;

        for (int i = 0; i < keys.length; i++) {
            long k = keys[i];
            if (k == EMPTY || k == DELETED) continue;

            @SuppressWarnings("unchecked")
            T v = (T) values[i];

            if (!liveness.isAlive(v)) {
                keys[i] = DELETED;
                values[i] = null;
                size--;
                removedAny = true;
            }
        }

        if (removedAny && tombstonePressureHigh()) {
            rehash(keys.length);
        }
    }

    private void requireValidKey(long key) {
        if (key == EMPTY || key == DELETED) {
            throw new IllegalArgumentException("Invalid key sentinel: " + key);
        }
    }

    // ----------------- internals -----------------

    private boolean tombstonePressureHigh() {
        int tomb = used - size;
        return tomb > (int) (keys.length * TOMBSTONE_FACTOR);
    }

    private int findIndex(long key) {
        requireValidKey(key);

        int idx = mix64to32(key) & mask;
        while (true) {
            long k = keys[idx];
            if (k == EMPTY) return -1;
            if (k == key) return idx;
            idx = (idx + 1) & mask;
        }
    }

    private int findSlotForInsert(long key) {
        int idx = mix64to32(key) & mask;
        int firstDeleted = -1;

        while (true) {
            long k = keys[idx];

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

        long[] oldK = this.keys;
        Object[] oldV = this.values;

        this.keys = new long[cap];
        this.values = new Object[cap];
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * LOAD_FACTOR);

        this.size = 0;
        this.used = 0;

        for (int i = 0; i < oldK.length; i++) {
            long k = oldK[i];
            if (k == EMPTY || k == DELETED) continue;

            @SuppressWarnings("unchecked")
            T v = (T) oldV[i];

            int idx = findSlotForInsertRehash(k);
            keys[idx] = k;
            values[idx] = v;
            size++;
            used++;
        }
    }

    private int findSlotForInsertRehash(long key) {
        int idx = mix64to32(key) & mask;
        while (true) {
            long k = keys[idx];
            if (k == EMPTY) return idx;
            idx = (idx + 1) & mask;
        }
    }

    @Override
    public String toString() {
        return "LongContactMap[size=" + size + ", cap=" + keys.length + ", used=" + used + "]";
    }

    private static int mix64to32(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) z;
    }

    public interface ContactLiveness<T> {
        boolean isAlive(T contact);
    }

    /**
     * Mutable entry view used by iterator.
     */
    public static final class Entry<T> {
        private long key;
        private T value;

        public long key() {
            return key;
        }

        public T value() {
            return value;
        }

        private void set(long key, T value) {
            this.key = key;
            this.value = value;
        }
    }

    private final class EntriesIterable implements Iterable<Entry<T>> {
        @Override
        public Iterator<Entry<T>> iterator() {
            return new EntryIterator();
        }
    }

    private final class EntryIterator implements Iterator<Entry<T>> {
        private final Entry<T> entry = new Entry<>();
        private int idx = -1;
        private int next = -1;

        EntryIterator() {
            advance();
        }

        @Override
        public boolean hasNext() {
            return next >= 0;
        }

        @Override
        public Entry<T> next() {
            if (next < 0) throw new NoSuchElementException();

            idx = next;

            @SuppressWarnings("unchecked")
            T v = (T) values[idx];

            entry.set(keys[idx], v);
            advance();
            return entry;
        }

        private void advance() {
            int i = idx + 1;
            while (i < keys.length) {
                long k = keys[i];
                if (k != EMPTY && k != DELETED) {
                    next = i;
                    return;
                }
                i++;
            }
            next = -1;
        }
    }
}