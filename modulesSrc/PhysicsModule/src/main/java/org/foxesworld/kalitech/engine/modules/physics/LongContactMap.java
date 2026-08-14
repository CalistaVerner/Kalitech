/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.physics;

import java.util.Arrays;
import org.foxesworld.kalitech.engine.modules.physics.ContactAgg;

public final class LongContactMap {
    private static final long EMPTY = 0L;
    private long[] keys;
    private ContactAgg[] values;
    private int size;
    private int mask;
    private int resizeAt;

    public LongContactMap(int initialCapacityPow2) {
        int cap;
        for (cap = 1; cap < initialCapacityPow2; cap <<= 1) {
        }
        if (cap < 16) {
            cap = 16;
        }
        this.keys = new long[cap];
        this.values = new ContactAgg[cap];
        this.mask = cap - 1;
        this.resizeAt = (int)((float)cap * 0.65f);
        this.size = 0;
    }

    public void clear() {
        Arrays.fill(this.keys, 0L);
        this.size = 0;
    }

    private static int mix64to32(long z) {
        z ^= z >>> 33;
        z *= -49064778989728563L;
        z ^= z >>> 33;
        z *= -4265267296055464877L;
        z ^= z >>> 33;
        return (int)z;
    }

    public int size() {
        return this.size;
    }

    public void forEach(EntryConsumer c) {
        if (c == null) {
            return;
        }
        long[] ks = this.keys;
        ContactAgg[] vs = this.values;
        for (int i = 0; i < ks.length; ++i) {
            ContactAgg v;
            long k = ks[i];
            if (k == 0L || (v = vs[i]) == null) continue;
            c.accept(k, v);
        }
    }

    public ContactAgg getOrCreate(long k) {
        if (k == 0L) {
            return null;
        }
        if (this.size >= this.resizeAt) {
            this.rehash(this.keys.length << 1);
        }
        int i = LongContactMap.mix64to32(k) & this.mask;
        while (true) {
            long kk;
            if ((kk = this.keys[i]) == 0L) {
                this.keys[i] = k;
                ContactAgg a = this.values[i];
                if (a == null || a.getPairKey() != k) {
                    this.values[i] = a = new ContactAgg(k);
                } else {
                    a.clear();
                }
                ++this.size;
                return a;
            }
            if (kk == k) {
                ContactAgg a = this.values[i];
                if (a == null || a.getPairKey() != k) {
                    this.values[i] = a = new ContactAgg(k);
                }
                return a;
            }
            i = i + 1 & this.mask;
        }
    }

    public ContactAgg get(long k) {
        if (k == 0L) {
            return null;
        }
        int i = LongContactMap.mix64to32(k) & this.mask;
        long kk;
        while ((kk = this.keys[i]) != 0L) {
            if (kk == k) {
                return this.values[i];
            }
            i = i + 1 & this.mask;
        }
        return null;
    }

    private void rehash(int newCap) {
        long[] ok = this.keys;
        ContactAgg[] ov = this.values;
        long[] nk = new long[newCap];
        ContactAgg[] nv = new ContactAgg[newCap];
        int nm = newCap - 1;
        for (int i = 0; i < ok.length; ++i) {
            long k = ok[i];
            if (k == 0L) continue;
            int idx = LongContactMap.mix64to32(k) & nm;
            while (nk[idx] != 0L) {
                idx = idx + 1 & nm;
            }
            nk[idx] = k;
            nv[idx] = ov[i];
        }
        this.keys = nk;
        this.values = nv;
        this.mask = nm;
        this.resizeAt = (int)((float)newCap * 0.65f);
    }

    @FunctionalInterface
    public static interface EntryConsumer {
        public void accept(long var1, ContactAgg var3);
    }
}

