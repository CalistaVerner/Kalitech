/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.physics.util;

import java.util.Objects;

public final class IdentityObjectIntMap {
    private static final Object EMPTY = null;
    private static final Object DELETED = new Object();
    private static final float LOAD_FACTOR = 0.65f;
    private static final float TOMBSTONE_FACTOR = 0.2f;
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
        int cap = IdentityObjectIntMap.tableSizeFor(Math.max(16, initialCapacity));
        this.keys = new Object[cap];
        this.values = new int[cap];
        this.mask = cap - 1;
        this.resizeAt = (int)((float)cap * 0.65f);
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

    public int getOrZero(Object key) {
        int idx = this.findIndex(key);
        return idx >= 0 ? this.values[idx] : 0;
    }

    public void put(Object key, int value) {
        Objects.requireNonNull(key, "key");
        if (this.used >= this.resizeAt) {
            this.rehash(this.keys.length << 1);
        } else if (this.tombstonePressureHigh()) {
            this.rehash(this.keys.length);
        }
        int idx = this.findSlotForInsert(key);
        Object k = this.keys[idx];
        if (k == key) {
            this.values[idx] = value;
            return;
        }
        if (k == EMPTY) {
            ++this.used;
        }
        this.keys[idx] = key;
        this.values[idx] = value;
        ++this.size;
    }

    public boolean removeIfEquals(Object key, int expectedValue) {
        Objects.requireNonNull(key, "key");
        int idx = this.findIndex(key);
        if (idx < 0) {
            return false;
        }
        if (this.values[idx] != expectedValue) {
            return false;
        }
        this.keys[idx] = DELETED;
        this.values[idx] = 0;
        --this.size;
        if (this.tombstonePressureHigh()) {
            this.rehash(this.keys.length);
        }
        return true;
    }

    public void clear() {
        for (int i = 0; i < this.keys.length; ++i) {
            this.keys[i] = EMPTY;
            this.values[i] = 0;
        }
        this.size = 0;
        this.used = 0;
    }

    private boolean tombstonePressureHigh() {
        int tomb = this.used - this.size;
        return tomb > (int)((float)this.keys.length * 0.2f);
    }

    private int findIndex(Object key) {
        Objects.requireNonNull(key, "key");
        int idx = IdentityObjectIntMap.mix32(System.identityHashCode(key)) & this.mask;
        Object k;
        while ((k = this.keys[idx]) != EMPTY) {
            if (k == key) {
                return idx;
            }
            idx = idx + 1 & this.mask;
        }
        return -1;
    }

    private int findSlotForInsert(Object key) {
        int idx = IdentityObjectIntMap.mix32(System.identityHashCode(key)) & this.mask;
        int firstDeleted = -1;
        Object k;
        while ((k = this.keys[idx]) != EMPTY) {
            if (k == key) {
                return idx;
            }
            if (k == DELETED && firstDeleted < 0) {
                firstDeleted = idx;
            }
            idx = idx + 1 & this.mask;
        }
        return firstDeleted >= 0 ? firstDeleted : idx;
    }

    private void rehash(int newCapacity) {
        int cap = IdentityObjectIntMap.tableSizeFor(newCapacity);
        Object[] oldK = this.keys;
        int[] oldV = this.values;
        this.keys = new Object[cap];
        this.values = new int[cap];
        this.mask = cap - 1;
        this.resizeAt = (int)((float)cap * 0.65f);
        this.size = 0;
        this.used = 0;
        for (int i = 0; i < oldK.length; ++i) {
            Object k = oldK[i];
            if (k == EMPTY || k == DELETED) continue;
            int v = oldV[i];
            int idx = this.findSlotForInsertRehash(k);
            this.keys[idx] = k;
            this.values[idx] = v;
            ++this.size;
            ++this.used;
        }
    }

    private int findSlotForInsertRehash(Object key) {
        int idx = IdentityObjectIntMap.mix32(System.identityHashCode(key)) & this.mask;
        Object k;
        while ((k = this.keys[idx]) != EMPTY) {
            idx = idx + 1 & this.mask;
        }
        return idx;
    }
}

