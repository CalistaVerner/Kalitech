// FILE: org/foxesworld/kalitech/engine/modules/physics/CollisionPairKey.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.collision;

/**
 * Compact collision pair key helpers.
 * key packs (minId,maxId) into one long.
 */
public final class CollisionPairKey {

    private CollisionPairKey() {
    }

    public static long pairKey(int a, int b) {
        if (a <= 0 || b <= 0) return 0L;
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return ((long) min << 32) | (max & 0xFFFFFFFFL);
    }

    public static int keyA(long k) {
        return (int) (k >>> 32);
    }

    public static int keyB(long k) {
        return (int) (k & 0xFFFFFFFFL);
    }
}
