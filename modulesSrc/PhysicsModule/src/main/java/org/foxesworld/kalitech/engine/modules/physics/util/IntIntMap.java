/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.physics.util;

public final class IntIntMap {
    private static final int EMPTY = 0;
    private static final int DELETED = Integer.MIN_VALUE;
    private static final float LOAD_FACTOR = 0.65f;
    private static final float TOMBSTONE_FACTOR = 0.2f;
    private int[] keys;
    private int[] values;
    private int size;
    private int used;
    private int mask;
    private int resizeAt;

    public IntIntMap() {
        this(256);
    }

    public IntIntMap(int initialCapacity) {
        int cap = IntIntMap.tableSizeFor(Math.max(16, initialCapacity));
        this.keys = new int[cap];
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

    public int getOrZero(int key) {
        int idx = this.findIndex(key);
        return idx >= 0 ? this.values[idx] : 0;
    }

    public void put(int key, int value) {
        this.requireValidKey(key);
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

    public boolean removeIfEquals(int key, int expectedValue) {
        this.requireValidKey(key);
        int idx = this.findIndex(key);
        if (idx < 0) {
            return false;
        }
        if (this.values[idx] != expectedValue) {
            return false;
        }
        this.keys[idx] = Integer.MIN_VALUE;
        this.values[idx] = 0;
        --this.size;
        if (this.tombstonePressureHigh()) {
            this.rehash(this.keys.length);
        }
        return true;
    }

    public void remove(int key) {
        this.requireValidKey(key);
        int idx = this.findIndex(key);
        if (idx < 0) {
            return;
        }
        this.keys[idx] = Integer.MIN_VALUE;
        this.values[idx] = 0;
        --this.size;
        if (this.tombstonePressureHigh()) {
            this.rehash(this.keys.length);
        }
    }

    public void clear() {
        for (int i = 0; i < this.keys.length; ++i) {
            this.keys[i] = 0;
            this.values[i] = 0;
        }
        this.size = 0;
        this.used = 0;
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
        int idx = IntIntMap.mix32(key) & this.mask;
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
        int idx = IntIntMap.mix32(key) & this.mask;
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
        int cap = IntIntMap.tableSizeFor(newCapacity);
        int[] oldK = this.keys;
        int[] oldV = this.values;
        this.keys = new int[cap];
        this.values = new int[cap];
        this.mask = cap - 1;
        this.resizeAt = (int)((float)cap * 0.65f);
        this.size = 0;
        this.used = 0;
        for (int i = 0; i < oldK.length; ++i) {
            int k = oldK[i];
            if (k == 0 || k == Integer.MIN_VALUE) continue;
            int v = oldV[i];
            int idx = this.findSlotForInsertRehash(k);
            this.keys[idx] = k;
            this.values[idx] = v;
            ++this.size;
            ++this.used;
        }
    }

    private int findSlotForInsertRehash(int key) {
        int idx = IntIntMap.mix32(key) & this.mask;
        int k;
        while ((k = this.keys[idx]) != 0) {
            idx = idx + 1 & this.mask;
        }
        return idx;
    }
}

