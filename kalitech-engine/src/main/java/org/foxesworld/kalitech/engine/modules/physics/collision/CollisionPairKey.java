// FILE: org/foxesworld/kalitech/engine/modules/physics/CollisionPairKey.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.collision;

import java.util.Objects;

/**
 * Canonical unordered collision pair key.
 * Guarantees stable identity for (A,B) == (B,A).
 *
 * <p>Main fast-path in the engine should use the packed {@code long} key via {@link #pack(int, int)}
 * to avoid allocations. This class remains available for readability/debug.</p>
 */
public final class CollisionPairKey {

    private final int a;
    private final int b;

    private CollisionPairKey(int a, int b) {
        this.a = a;
        this.b = b;
    }

    /**
     * Creates a canonical unordered pair key (allocation path).
     */
    public static CollisionPairKey of(int idA, int idB) {
        return (idA <= idB) ? new CollisionPairKey(idA, idB) : new CollisionPairKey(idB, idA);
    }

    /**
     * Creates a canonical unordered pair key (allocation path).
     * <p>Values are cast to int to match packed key semantics used by the engine.</p>
     */
    public static CollisionPairKey of(long idA, long idB) {
        int a = (int) idA;
        int b = (int) idB;
        return of(a, b);
    }

    /**
     * Packs two ids into one canonical unordered {@code long} key without allocations.
     *
     * <p>Layout: {@code [min:int32 | max:int32]}.</p>
     */
    public static long pack(int idA, int idB) {
        int lo = idA;
        int hi = idB;
        if (lo > hi) {
            int t = lo;
            lo = hi;
            hi = t;
        }
        return (((long) lo) << 32) | (hi & 0xFFFF_FFFFL);
    }

    /**
     * Extracts A (min id) from packed {@code long} key.
     */
    public static int keyA(long pairKey) {
        return (int) (pairKey >> 32);
    }

    /**
     * Extracts B (max id) from packed {@code long} key.
     */
    public static int keyB(long pairKey) {
        return (int) pairKey;
    }

    public int a() {
        return a;
    }

    public int b() {
        return b;
    }

    /**
     * Returns packed canonical key (no collision for int32 ids).
     */
    public long toLongKey() {
        return pack(a, b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollisionPairKey other)) return false;
        return a == other.a && b == other.b;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b);
    }

    @Override
    public String toString() {
        return "CollisionPairKey[" + a + "," + b + "]";
    }
}