// FILE: org/foxesworld/kalitech/engine/modules/physics/collision/CollisionPairKey.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.collision;

/**
 * Compact unordered collision pair key helpers.
 * Packs (minId,maxId) into one long.
 */
public final class CollisionPairKey {

    public static final long EMPTY = 0L;

    private CollisionPairKey() {
    }

    /**
     * Creates an unordered pair key. Returns {@link #EMPTY} if either id is not positive.
     */
    public static long pairKey(int a, int b) {
        if (a <= 0 || b <= 0) return EMPTY;
        final int min = (a < b) ? a : b;
        final int max = (a < b) ? b : a;
        return (((long) min) << 32) | (max & 0xFFFFFFFFL);
    }

    public static boolean isEmpty(long key) {
        return key == EMPTY;
    }

    public static int keyA(long key) {
        return (int) (key >>> 32);
    }

    public static int keyB(long key) {
        return (int) key;
    }
}
