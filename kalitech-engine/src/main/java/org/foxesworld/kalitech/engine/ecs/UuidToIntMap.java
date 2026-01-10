package org.foxesworld.kalitech.engine.ecs;

import java.util.Arrays;

import static org.foxesworld.kalitech.engine.ecs.EntityId.NULL;

final class UuidToIntMap {

    private static final float DEFAULT_LOAD = 0.75f;
    private final float loadFactor;

    private long[] msb;
    private long[] lsb;
    private int[] val;

    private int size;
    private int mask;
    private int resizeAt;

    UuidToIntMap(int initialCapacityPow2) {
        this(initialCapacityPow2, DEFAULT_LOAD);
    }

    UuidToIntMap(int initialCapacityPow2, float loadFactor) {
        if (!(loadFactor > 0.20f && loadFactor < 0.90f)) {
            throw new IllegalArgumentException("loadFactor must be in (0.20, 0.90)");
        }
        this.loadFactor = loadFactor;

        int cap = 1;
        while (cap < initialCapacityPow2) cap <<= 1;
        if (cap < 16) cap = 16;

        this.msb = new long[cap];
        this.lsb = new long[cap];
        this.val = new int[cap];

        this.mask = cap - 1;
        this.resizeAt = (int) (cap * loadFactor);
        this.size = 0;
    }

    private static int homeIndex(long m, long l, int mask) {
        long z = mix128to64(m, l);
        return mix64to32(z) & mask;
    }

    private static long mix128to64(long msb, long lsb) {
        long z = msb ^ (lsb * 0x9E3779B97F4A7C15L);
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }

    private static int mix64to32(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) z;
    }

    private static int distance(int start, int pos, int mask) {
        return (pos - start) & mask;
    }

    void clear() {
        Arrays.fill(msb, 0L);
        Arrays.fill(lsb, 0L);
        size = 0;
    }

    boolean contains(long m, long l) {
        return get(m, l) != NULL;
    }

    int get(long m, long l) {
        if ((m | l) == 0L) return NULL;

        long[] M = msb;
        long[] L = lsb;
        int[] V = val;
        int mk = mask;

        int home = homeIndex(m, l, mk);
        int i = home;
        int pd = 0;

        while (true) {
            long cm = M[i];
            long cl = L[i];

            if ((cm | cl) == 0L) return NULL;
            if (cm == m && cl == l) return V[i];

            int vHome = homeIndex(cm, cl, mk);
            int vPd = distance(vHome, i, mk);
            if (vPd < pd) return NULL;

            i = (i + 1) & mk;
            pd++;
        }
    }

    void put(long m, long l, int entityId) {
        if ((m | l) == 0L) throw new IllegalArgumentException("UUID 0/0 is reserved");
        if (entityId == NULL) throw new IllegalArgumentException("entityId NULL is not allowed");

        if (size >= resizeAt) rehash(msb.length << 1);

        long[] M = msb;
        long[] L = lsb;
        int[] V = val;
        int mk = mask;

        int home = homeIndex(m, l, mk);
        int i = home;
        int pd = 0;

        long curM = m;
        long curL = l;
        int curV = entityId;

        while (true) {
            long vm = M[i];
            long vl = L[i];

            if ((vm | vl) == 0L) {
                M[i] = curM;
                L[i] = curL;
                V[i] = curV;
                size++;
                return;
            }

            if (vm == curM && vl == curL) {
                V[i] = curV;
                return;
            }

            int vHome = homeIndex(vm, vl, mk);
            int vPd = distance(vHome, i, mk);

            if (vPd < pd) {
                long tmpM = M[i];
                long tmpL = L[i];
                int tmpV = V[i];

                M[i] = curM;
                L[i] = curL;
                V[i] = curV;

                curM = tmpM;
                curL = tmpL;
                curV = tmpV;

                pd = vPd;
            }

            i = (i + 1) & mk;
            pd++;
        }
    }

    boolean remove(long m, long l) {
        if ((m | l) == 0L) return false;

        long[] M = msb;
        long[] L = lsb;
        int mk = mask;

        int home = homeIndex(m, l, mk);
        int i = home;
        int pd = 0;

        while (true) {
            long cm = M[i];
            long cl = L[i];

            if ((cm | cl) == 0L) return false;
            if (cm == m && cl == l) {
                deleteAndShiftRobinHood(i);
                size--;
                return true;
            }

            int vHome = homeIndex(cm, cl, mk);
            int vPd = distance(vHome, i, mk);
            if (vPd < pd) return false;

            i = (i + 1) & mk;
            pd++;
        }
    }

    private void rehash(int newCap) {
        int cap = 1;
        while (cap < newCap) cap <<= 1;
        if (cap < 16) cap = 16;

        long[] oldM = msb;
        long[] oldL = lsb;
        int[] oldV = val;

        msb = new long[cap];
        lsb = new long[cap];
        val = new int[cap];

        mask = cap - 1;
        resizeAt = (int) (cap * loadFactor);

        int oldSize = size;
        size = 0;

        for (int i = 0; i < oldM.length; i++) {
            long m = oldM[i];
            long l = oldL[i];
            if ((m | l) == 0L) continue;
            put(m, l, oldV[i]);
        }

        if (size != oldSize) {
            throw new IllegalStateException("rehash size mismatch: " + size + " != " + oldSize);
        }
    }

    private void deleteAndShiftRobinHood(int deleteIndex) {
        long[] M = msb;
        long[] L = lsb;
        int[] V = val;
        int mk = mask;

        int i = deleteIndex;
        int j = (i + 1) & mk;

        while (true) {
            long jm = M[j];
            long jl = L[j];

            if ((jm | jl) == 0L) {
                M[i] = 0L;
                L[i] = 0L;
                return;
            }

            int jHome = homeIndex(jm, jl, mk);
            int jPd = distance(jHome, j, mk);

            if (jPd == 0) {
                M[i] = 0L;
                L[i] = 0L;
                return;
            }

            M[i] = jm;
            L[i] = jl;
            V[i] = V[j];

            i = j;
            j = (j + 1) & mk;
        }
    }
}