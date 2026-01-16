// FILE: org/foxesworld/kalitech/engine/modules/physics/util/LongContactMap.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.physics.ContactAgg;

import java.util.Arrays;

/**
 * Open-addressing long -> {@link ContactAgg} map (no boxing).
 * Uses 0 as EMPTY sentinel in keys table.
 *
 * <p>Intended for per-tick accumulation. Call {@link #clear()} after consumption.</p>
 */
public final class LongContactMap {

    private static final Logger log = LogManager.getLogger(LongContactMap.class);

    private static final long EMPTY = 0L;

    private long[] keys;
    private ContactAgg[] values;
    private int size;
    private int mask;
    private int resizeAt;

    // Debug / stats (cheap, optional)
    private volatile boolean dbg;
    private volatile int dbgEvery = 0; // 0 = off, otherwise log once per N puts/ops

    private long puts;
    private long rehashes;
    private long compactions;
    private int maxProbe;
    private long probeSum;
    private long probeCount;

    public LongContactMap(int initialCapacityPow2) {
        int cap = 1;
        while (cap < initialCapacityPow2) cap <<= 1;
        if (cap < 16) cap = 16;

        this.keys = new long[cap];
        this.values = new ContactAgg[cap];
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * 0.65f);
        this.size = 0;
    }

    /**
     * Enables or disables debug logging. When enabled, logs are rate-limited by {@link #setDebug(boolean, int)}.
     */
    public void setDebug(boolean enabled) {
        this.dbg = enabled;
        this.dbgEvery = enabled ? 1024 : 0;
    }

    /**
     * Enables or disables debug logging with custom rate limit.
     *
     * @param enabled  enable debug logging
     * @param everyOps log once per N operations (put/getOrCreate/rehash/compact related paths)
     */
    public void setDebug(boolean enabled, int everyOps) {
        this.dbg = enabled;
        this.dbgEvery = enabled ? Math.max(1, everyOps) : 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns current capacity (keys table length).
     */
    public int capacity() {
        return keys.length;
    }

    /**
     * Returns load factor (size / capacity).
     */
    public float loadFactor() {
        int cap = keys.length;
        return cap == 0 ? 0f : (float) size / (float) cap;
    }

    public long puts() {
        return puts;
    }

    public long rehashes() {
        return rehashes;
    }

    public long compactions() {
        return compactions;
    }

    public int maxProbe() {
        return maxProbe;
    }

    public float avgProbe() {
        return probeCount == 0 ? 0f : (float) probeSum / (float) probeCount;
    }

    private static int mix64to32(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) z;
    }

    public void clear() {
        if (size == 0) {
            Arrays.fill(keys, EMPTY);
            return;
        }

        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != EMPTY) {
                ContactAgg a = values[i];
                if (a != null) a.clear();
                values[i] = null;
            }
        }
        Arrays.fill(keys, EMPTY);
        size = 0;

        if (dbg && log.isDebugEnabled()) {
            log.debug("[physics][contacts] clear cap={} lf={}", keys.length, loadFactor());
        }
    }

    public ContactAgg getOrCreate(long k) {
        if (k == EMPTY) {
            if (dbg && log.isWarnEnabled()) log.warn("[physics][contacts] getOrCreate called with EMPTY key");
            return null;
        }

        if (size >= resizeAt) rehash(keys.length << 1);

        int i = mix64to32(k) & mask;
        int probe = 0;

        while (true) {
            long kk = keys[i];

            if (kk == EMPTY) {
                keys[i] = k;
                ContactAgg a = values[i];
                if (a == null) values[i] = (a = new ContactAgg());
                a.clear();
                size++;

                recordProbe(probe);
                maybeLogOp("insert", k, probe);

                return a;
            }

            if (kk == k) {
                ContactAgg a = values[i];
                if (a == null) values[i] = (a = new ContactAgg());

                recordProbe(probe);
                maybeLogOp("hit", k, probe);

                return a;
            }

            i = (i + 1) & mask;
            probe++;

            if (probe > 256) {
                // This indicates heavy clustering; still works but should be investigated.
                if (dbg && log.isWarnEnabled()) {
                    log.warn("[physics][contacts] long probe chain probe={} cap={} size={} lf={}",
                            probe, keys.length, size, loadFactor());
                }
            }
        }
    }

    public ContactAgg get(long k) {
        if (k == EMPTY) return null;

        int i = mix64to32(k) & mask;
        int probe = 0;

        while (true) {
            long kk = keys[i];
            if (kk == EMPTY) {
                recordProbe(probe);
                return null;
            }
            if (kk == k) {
                recordProbe(probe);
                return values[i];
            }
            i = (i + 1) & mask;
            probe++;
            if (probe > 256) {
                if (dbg && log.isWarnEnabled()) {
                    log.warn("[physics][contacts] get long probe chain probe={} cap={} size={} lf={}",
                            probe, keys.length, size, loadFactor());
                }
            }
        }
    }

    public void put(long k, float impulse, Vector3f point) {
        put(k, impulse, point, null);
    }

    public void put(long k, float impulse, Vector3f point, Vector3f normal) {
        if (k == EMPTY) {
            if (dbg && log.isWarnEnabled()) log.warn("[physics][contacts] put called with EMPTY key");
            return;
        }

        puts++;

        if (!Float.isFinite(impulse) && dbg && log.isWarnEnabled()) {
            log.warn("[physics][contacts] non-finite impulse={} key={}", impulse, k);
        }

        ContactAgg a = getOrCreate(k);
        if (a == null) return;
        a.add(impulse, point, normal);

        if (dbgEvery > 0 && dbg && log.isDebugEnabled() && (puts % dbgEvery) == 0) {
            log.debug("[physics][contacts] put#{} cap={} size={} lf={} maxProbe={} avgProbe={}",
                    puts, keys.length, size, loadFactor(), maxProbe, avgProbe());
        }
    }

    public void compact() {
        int cap = keys.length;
        if (cap <= 16) return;

        if (size <= (cap * 0.25f)) {
            int newCap = cap;
            while (newCap > 16 && size <= (newCap * 0.25f)) newCap >>= 1;

            if (newCap < cap) {
                if (dbg && log.isDebugEnabled()) {
                    log.debug("[physics][contacts] compact cap={} -> {} size={} lf={}",
                            cap, newCap, size, (cap == 0 ? 0f : (float) size / (float) cap));
                }
                compactions++;
                rehash(newCap);
            }
        }
    }

    private void rehash(int newCap) {
        int oldCap = keys.length;

        if (newCap < 16) newCap = 16;
        if ((newCap & (newCap - 1)) != 0) {
            int p = 1;
            while (p < newCap) p <<= 1;
            newCap = p;
        }

        long[] ok = keys;
        ContactAgg[] ov = values;

        long[] nk = new long[newCap];
        ContactAgg[] nv = new ContactAgg[newCap];
        int nm = newCap - 1;

        for (int i = 0; i < ok.length; i++) {
            long k = ok[i];
            if (k == EMPTY) continue;

            int idx = mix64to32(k) & nm;
            int probe = 0;
            while (nk[idx] != EMPTY) {
                idx = (idx + 1) & nm;
                probe++;
            }
            nk[idx] = k;
            nv[idx] = ov[i];
            recordProbe(probe);
        }

        keys = nk;
        values = nv;
        mask = nm;
        resizeAt = (int) (newCap * 0.65f);

        rehashes++;

        if (dbg && log.isDebugEnabled()) {
            log.debug("[physics][contacts] rehash#{} cap={} -> {} size={} lf={} resizeAt={}",
                    rehashes, oldCap, newCap, size, loadFactor(), resizeAt);
        }
    }

    private void recordProbe(int probe) {
        if (probe < 0) return;
        if (probe > maxProbe) maxProbe = probe;
        probeSum += probe;
        probeCount++;
    }

    private void maybeLogOp(String kind, long key, int probe) {
        if (!dbg || dbgEvery <= 0 || !log.isTraceEnabled()) return;
        long n = puts == 0 ? probeCount : puts;
        if (n % dbgEvery != 0) return;

        log.trace("[physics][contacts] {} key={} probe={} cap={} size={} lf={}",
                kind, key, probe, keys.length, size, loadFactor());
    }
}