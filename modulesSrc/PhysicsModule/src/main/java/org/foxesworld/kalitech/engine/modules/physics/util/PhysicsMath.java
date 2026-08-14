/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.FastMath
 *  com.jme3.math.Vector3f
 */
package org.foxesworld.kalitech.engine.modules.physics.util;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

public final class PhysicsMath {
    public static final float EPS = 1.0E-6f;

    private PhysicsMath() {
    }

    public static float clampPositive(float v, float min) {
        return Float.isFinite(v) && v > min ? v : min;
    }

    public static boolean isFinite(float v) {
        return Float.isFinite(v);
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    public static float safeInv(float v) {
        return FastMath.abs((float)v) > 1.0E-6f ? 1.0f / v : 0.0f;
    }

    public static float safeLength(Vector3f v) {
        float len2 = v.lengthSquared();
        return len2 > 1.0E-6f ? FastMath.sqrt((float)len2) : 0.0f;
    }

    public static Vector3f safeNormalize(Vector3f v, Vector3f store) {
        float len2 = v.lengthSquared();
        if (len2 < 1.0E-6f) {
            return store.set(0.0f, 0.0f, 0.0f);
        }
        return store.set(v).multLocal(FastMath.invSqrt((float)len2));
    }

    public static float relativeSpeed(Vector3f va, Vector3f vb, Vector3f n) {
        float rvx = vb.x - va.x;
        float rvy = vb.y - va.y;
        float rvz = vb.z - va.z;
        return rvx * n.x + rvy * n.y + rvz * n.z;
    }

    public static float reducedMass(float ma, float mb) {
        if (!(ma > 0.0f) || !(mb > 0.0f)) {
            return 0.0f;
        }
        float s = ma + mb;
        if (!(s > 1.0E-6f)) {
            return 0.0f;
        }
        return ma * mb / s;
    }

    public static void buildPerpBasis(Vector3f dirN, Vector3f outU, Vector3f outV) {
        Vector3f a = FastMath.abs((float)dirN.y) < 0.9f ? Vector3f.UNIT_Y : Vector3f.UNIT_X;
        outU.set(dirN).crossLocal(a);
        float ul2 = outU.lengthSquared();
        if (!(ul2 > 1.0E-12f)) {
            a = Vector3f.UNIT_Z;
            outU.set(dirN).crossLocal(a);
            ul2 = outU.lengthSquared();
            if (!(ul2 > 1.0E-12f)) {
                outU.set(1.0f, 0.0f, 0.0f);
            }
        }
        PhysicsMath.safeNormalize(outU, outU);
        outV.set(dirN).crossLocal(outU);
        PhysicsMath.safeNormalize(outV, outV);
    }
}

