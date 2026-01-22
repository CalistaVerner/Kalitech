// FILE: org/foxesworld/kalitech/engine/modules/physics/util/IntObjectMap.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Open-addressing primitive int -> Object map (no boxing).
 *
 * <p>Uses 0 as EMPTY sentinel and Integer.MIN_VALUE as DELETED sentinel.
 * Keys must be positive (> 0).</p>
 *
 * <p>Iteration is allocation-light: iterator reuses a single mutable {@link Entry} instance.</p>
 *
 * <p>Not thread-safe.</p>
 */
public final class IntObjectMap<T> {

    private static final int EMPTY = 0;
    private static final int DELETED = Integer.MIN_VALUE;

    private static final float LOAD_FACTOR = 0.65f;
    private static final float TOMBSTONE_FACTOR = 0.20f;

    // Must not be initialized before keys/values are created (field init order matters).
    private EntriesIterable entriesIterable;

    private int[] keys;
    private Object[] values;
    private int size;
    private int used;
    private int mask;
    private int resizeAt;

    public IntObjectMap() {
        this(256);
    }

    public IntObjectMap(int initialCapacity) {
        int cap = tableSizeFor(Math.max(16, initialCapacity));
        this.keys = new int[cap];
        this.values = new Object[cap];
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * LOAD_FACTOR);
        this.entriesIterable = null;
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

    public boolean contains(int key) {
        return findIndex(key) >= 0;
    }

    @SuppressWarnings("unchecked")
    public T get(int key) {
        int idx = findIndex(key);
        return idx >= 0 ? (T) values[idx] : null;
    }

    public void put(int key, T value) {
        requireValidKey(key);
        Objects.requireNonNull(value, "value");

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

    @SuppressWarnings("unchecked")
    public T remove(int key) {
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
        for (int i = 0; i < keys.length; i++) {
            keys[i] = EMPTY;
            values[i] = null;
        }
        size = 0;
        used = 0;
    }

    /**
     * Iterates over all live entries.
     * Iterator instance is reused (no per-iteration allocation).
     */
    public Iterable<Entry<T>> entries() {
        EntriesIterable it = entriesIterable;
        if (it == null) {
            it = new EntriesIterable();
            entriesIterable = it;
        }
        return it;
    }

    public void sweep(Liveness<T> liveness) {
        Objects.requireNonNull(liveness, "liveness");
        boolean removedAny = false;

        for (int i = 0; i < keys.length; i++) {
            int k = keys[i];
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

    // ----------------- internals -----------------

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
        Object[] oldV = this.values;

        this.keys = new int[cap];
        this.values = new Object[cap];
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * LOAD_FACTOR);

        this.size = 0;
        this.used = 0;

        for (int i = 0; i < oldK.length; i++) {
            int k = oldK[i];
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

    private int findSlotForInsertRehash(int key) {
        int idx = mix32(key) & mask;
        while (true) {
            int k = keys[idx];
            if (k == EMPTY) return idx;
            idx = (idx + 1) & mask;
        }
    }

    public interface Liveness<T> {
        boolean isAlive(T value);
    }

    /**
     * Mutable entry view used by iterator.
     */
    public static final class Entry<T> {
        private int key;
        private T value;

        public int key() {
            return key;
        }

        public T value() {
            return value;
        }

        private void set(int key, T value) {
            this.key = key;
            this.value = value;
        }
    }

    private final class EntriesIterable implements Iterable<Entry<T>> {

        private final EntryIterator it = new EntryIterator();

        @Override
        public Iterator<Entry<T>> iterator() {
            it.reset();
            return it;
        }
    }

    private final class EntryIterator implements Iterator<Entry<T>> {
        private final Entry<T> entry = new Entry<>();
        private int idx;
        private int next;

        private EntryIterator() {
            this.idx = -1;
            this.next = -1;
        }

        private void reset() {
            this.idx = -1;
            this.next = -1;
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
            final int[] ks = keys;
            while (i < ks.length) {
                int k = ks[i];
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