/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.physics.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class IntObjectMap<T> {
    private static final int EMPTY = 0;
    private static final int DELETED = Integer.MIN_VALUE;
    private static final float LOAD_FACTOR = 0.65f;
    private static final float TOMBSTONE_FACTOR = 0.2f;
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
        int cap = IntObjectMap.tableSizeFor(Math.max(16, initialCapacity));
        this.keys = new int[cap];
        this.values = new Object[cap];
        this.mask = cap - 1;
        this.resizeAt = (int)((float)cap * 0.65f);
        this.entriesIterable = null;
    }

    private static int mix32(int x) {
        x ^= x >>> 16;
        x *= 2146121005;
        x ^= x >>> 15;
        x *= -2073254261;
        x ^= x >>> 16;
        return x;
    }

    private static int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        return (n |= n >>> 16) < 16 ? 16 : n + 1;
    }

    public int size() {
        return this.size;
    }

    public boolean contains(int key) {
        return this.findIndex(key) >= 0;
    }

    public T get(int key) {
        int idx = this.findIndex(key);
        return (T)(idx >= 0 ? this.values[idx] : null);
    }

    public void put(int key, T value) {
        this.requireValidKey(key);
        Objects.requireNonNull(value, "value");
        if (this.used >= this.resizeAt) {
            this.rehash(this.keys.length << 1);
        } else if (this.tombstonePressureHigh()) {
            this.rehash(this.keys.length);
        }
        int idx = this.findSlotForInsert(key);
        int k = this.keys[idx];
        if (k == key) {
            this.values[idx] = value;
            return;
        }
        if (k == 0) {
            ++this.used;
        }
        this.keys[idx] = key;
        this.values[idx] = value;
        ++this.size;
    }

    public T remove(int key) {
        this.requireValidKey(key);
        int idx = this.findIndex(key);
        if (idx < 0) {
            return null;
        }
        Object old = this.values[idx];
        this.keys[idx] = Integer.MIN_VALUE;
        this.values[idx] = null;
        --this.size;
        if (this.tombstonePressureHigh()) {
            this.rehash(this.keys.length);
        }
        return (T)old;
    }

    public void clear() {
        for (int i = 0; i < this.keys.length; ++i) {
            this.keys[i] = 0;
            this.values[i] = null;
        }
        this.size = 0;
        this.used = 0;
    }

    public Iterable<Entry<T>> entries() {
        EntriesIterable it = this.entriesIterable;
        if (it == null) {
            this.entriesIterable = it = new EntriesIterable();
        }
        return it;
    }

    public void sweep(Liveness<T> liveness) {
        Objects.requireNonNull(liveness, "liveness");
        boolean removedAny = false;
        for (int i = 0; i < this.keys.length; ++i) {
            T v;
            int k = this.keys[i];
            if (k == 0 || k == Integer.MIN_VALUE || liveness.isAlive(v = (T) this.values[i])) continue;
            this.keys[i] = Integer.MIN_VALUE;
            this.values[i] = null;
            --this.size;
            removedAny = true;
        }
        if (removedAny && this.tombstonePressureHigh()) {
            this.rehash(this.keys.length);
        }
    }

    private void requireValidKey(int key) {
        if (key <= 0 || key == Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
    }

    private boolean tombstonePressureHigh() {
        int tomb = this.used - this.size;
        return tomb > (int)((float)this.keys.length * 0.2f);
    }

    private int findIndex(int key) {
        this.requireValidKey(key);
        int idx = IntObjectMap.mix32(key) & this.mask;
        int k;
        while ((k = this.keys[idx]) != 0) {
            if (k == key) {
                return idx;
            }
            idx = idx + 1 & this.mask;
        }
        return -1;
    }

    private int findSlotForInsert(int key) {
        int idx = IntObjectMap.mix32(key) & this.mask;
        int firstDeleted = -1;
        int k;
        while ((k = this.keys[idx]) != 0) {
            if (k == key) {
                return idx;
            }
            if (k == Integer.MIN_VALUE && firstDeleted < 0) {
                firstDeleted = idx;
            }
            idx = idx + 1 & this.mask;
        }
        return firstDeleted >= 0 ? firstDeleted : idx;
    }

    private void rehash(int newCapacity) {
        int cap = IntObjectMap.tableSizeFor(newCapacity);
        int[] oldK = this.keys;
        Object[] oldV = this.values;
        this.keys = new int[cap];
        this.values = new Object[cap];
        this.mask = cap - 1;
        this.resizeAt = (int)((float)cap * 0.65f);
        this.size = 0;
        this.used = 0;
        for (int i = 0; i < oldK.length; ++i) {
            int k = oldK[i];
            if (k == 0 || k == Integer.MIN_VALUE) continue;
            Object v = oldV[i];
            int idx = this.findSlotForInsertRehash(k);
            this.keys[idx] = k;
            this.values[idx] = v;
            ++this.size;
            ++this.used;
        }
    }

    private int findSlotForInsertRehash(int key) {
        int idx = IntObjectMap.mix32(key) & this.mask;
        int k;
        while ((k = this.keys[idx]) != 0) {
            idx = idx + 1 & this.mask;
        }
        return idx;
    }

    private final class EntriesIterable
    implements Iterable<Entry<T>> {
        private final EntryIterator it;

        private EntriesIterable() {
            this.it = new EntryIterator();
        }

        @Override
        public Iterator<Entry<T>> iterator() {
            this.it.reset();
            return this.it;
        }
    }

    public static interface Liveness<T> {
        public boolean isAlive(T var1);
    }

    private final class EntryIterator
    implements Iterator<Entry<T>> {
        private final Entry<T> entry = new Entry();
        private int idx = -1;
        private int next = -1;

        private EntryIterator() {
        }

        private void reset() {
            this.idx = -1;
            this.next = -1;
            this.advance();
        }

        @Override
        public boolean hasNext() {
            return this.next >= 0;
        }

        @Override
        public Entry<T> next() {
            if (this.next < 0) {
                throw new NoSuchElementException();
            }
            this.idx = this.next;
            T v = (T) IntObjectMap.this.values[this.idx];
            this.entry.set(IntObjectMap.this.keys[this.idx], v);
            this.advance();
            return this.entry;
        }

        private void advance() {
            int[] ks = IntObjectMap.this.keys;
            for (int i = this.idx + 1; i < ks.length; ++i) {
                int k = ks[i];
                if (k == 0 || k == Integer.MIN_VALUE) continue;
                this.next = i;
                return;
            }
            this.next = -1;
        }
    }

    public static final class Entry<T> {
        private int key;
        private T value;

        public int key() {
            return this.key;
        }

        public T value() {
            return this.value;
        }

        private void set(int key, T value) {
            this.key = key;
            this.value = value;
        }
    }
}

