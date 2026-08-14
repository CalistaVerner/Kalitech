/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.physics.collision;

public final class CollisionPairKey {
    public static final long EMPTY = 0L;

    private CollisionPairKey() {
    }

    public static long pairKey(int a, int b) {
        if (a <= 0 || b <= 0) {
            return 0L;
        }
        int min = a < b ? a : b;
        int max = a < b ? b : a;
        return (long)min << 32 | (long)max & 0xFFFFFFFFL;
    }

    public static boolean isEmpty(long key) {
        return key == 0L;
    }

    public static int keyA(long key) {
        return (int)(key >>> 32);
    }

    public static int keyB(long key) {
        return (int)key;
    }
}

