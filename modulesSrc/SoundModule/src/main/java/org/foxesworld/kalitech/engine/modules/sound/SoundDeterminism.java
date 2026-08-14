/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.sound;

public final class SoundDeterminism {
    private static final long MIX64_CONST = -7046029254386353131L;

    private SoundDeterminism() {
    }

    public static long mix64(long z) {
        z += -7046029254386353131L;
        z = (z ^ z >>> 30) * -4658895280553007687L;
        z = (z ^ z >>> 27) * -7723592293110705685L;
        return z ^ z >>> 31;
    }

    public static long hashStringFNV1a64(String s) {
        if (s == null) {
            return 0L;
        }
        long h = -3750763034362895579L;
        for (int i = 0; i < s.length(); ++i) {
            h ^= (long)s.charAt(i);
            h *= 1099511628211L;
        }
        return h;
    }

    public static long seedForEvent(long baseSeed, String eventKey, long ctxA, long ctxB, long ctxC) {
        long h = baseSeed;
        h ^= SoundDeterminism.hashStringFNV1a64(eventKey);
        h ^= ctxA * -2960836687051489901L;
        h ^= ctxB * -6511265916475787933L;
        return SoundDeterminism.mix64(h ^= ctxC * -6939452855193903323L);
    }

    public static int chooseIndex(long seed, int size) {
        if (size <= 1) {
            return 0;
        }
        long x = SoundDeterminism.mix64(seed);
        int v = (int)(x & Integer.MAX_VALUE);
        return v % size;
    }

    public static float nextFloat01(long seed, int salt) {
        long x = SoundDeterminism.mix64(seed ^ (long)salt * 7146057691288625177L);
        int bits = (int)(x >>> 40);
        return (float)bits / 1.6777216E7f;
    }

    public static enum Mode {
        DETERMINISTIC,
        NON_DETERMINISTIC;

    }
}

