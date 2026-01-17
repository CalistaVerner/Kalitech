// FILE: org/foxesworld/kalitech/engine/modules/physics/util/PhysicsMath.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

/**
 * Deterministic and safe math helpers for physics hot paths.
 */
public final class PhysicsMath {

    public static final float EPS = 1e-6f;

    private PhysicsMath() {
    }

    public static float clampPositive(float v, float min) {
        return (Float.isFinite(v) && v > min) ? v : min;
    }

    public static boolean isFinite(float v) {
        return Float.isFinite(v);
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static float safeInv(float v) {
        return FastMath.abs(v) > EPS ? 1f / v : 0f;
    }

    public static float safeLength(Vector3f v) {
        float len2 = v.lengthSquared();
        return len2 > EPS ? FastMath.sqrt(len2) : 0f;
    }

    public static Vector3f safeNormalize(Vector3f v, Vector3f store) {
        float len2 = v.lengthSquared();
        if (len2 < EPS) return store.set(Vector3f.ZERO);
        return store.set(v).multLocal(FastMath.invSqrt(len2));
    }

    /**
     * Build perpendicular basis U,V for direction dirN.
     * dirN is assumed normalized (or at least non-zero).
     */
    public static void buildPerpBasis(Vector3f dirN, Vector3f outU, Vector3f outV) {
        Vector3f a = (FastMath.abs(dirN.y) < 0.9f) ? Vector3f.UNIT_Y : Vector3f.UNIT_X;

        outU.set(dirN).crossLocal(a);
        float ul2 = outU.lengthSquared();

        if (!(ul2 > 1e-12f)) {
            a = Vector3f.UNIT_Z;
            outU.set(dirN).crossLocal(a);
            ul2 = outU.lengthSquared();
            if (!(ul2 > 1e-12f)) outU.set(1, 0, 0);
        }

        safeNormalize(outU, outU);
        outV.set(dirN).crossLocal(outU);
        safeNormalize(outV, outV);
    }
}