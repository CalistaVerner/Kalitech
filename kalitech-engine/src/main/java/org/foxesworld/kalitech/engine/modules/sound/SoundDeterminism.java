// FILE: org/foxesworld/kalitech/engine/modules/sound/SoundDeterminism.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.sound;

/**
 * Deterministic selection and sampling helpers for audio playback.
 *
 * <p>Goal: same (seed + eventKey + context) must lead to the same sound variant and the same sampled params.</p>
 */
public final class SoundDeterminism {

    private static final long MIX64_CONST = 0x9E3779B97F4A7C15L;

    private SoundDeterminism() {
    }

    public static long mix64(long z) {
        z += MIX64_CONST;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long hashStringFNV1a64(String s) {
        if (s == null) return 0L;
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    public static long seedForEvent(long baseSeed, String eventKey, long ctxA, long ctxB, long ctxC) {
        long h = baseSeed;
        h ^= hashStringFNV1a64(eventKey);
        h ^= ctxA * 0xD6E8FEB86659FD93L;
        h ^= ctxB * 0xA5A35625AA5A3563L;
        h ^= ctxC * 0x9FB21C651E98DF25L;
        return mix64(h);
    }

    public static int chooseIndex(long seed, int size) {
        if (size <= 1) return 0;
        long x = mix64(seed);
        int v = (int) (x & 0x7fffffff);
        return v % size;
    }

    public static float nextFloat01(long seed, int salt) {
        long x = mix64(seed ^ (salt * 0x632BE59BD9B4E019L));
        int bits = (int) (x >>> 40);
        return bits / (float) (1 << 24);
    }

    public enum Mode {
        DETERMINISTIC,
        NON_DETERMINISTIC
    }
}
