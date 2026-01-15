// FILE: org/foxesworld/kalitech/engine/modules/physics/PhysicsMath.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.math.Vector3f;

public final class PhysicsMath {

    private PhysicsMath() {
    }

    public static float clampPositive(float v, float min) {
        return (Float.isFinite(v) && v > min) ? v : min;
    }

    public static boolean isFinite(float v) {
        return Float.isFinite(v);
    }

    /**
     * Build perpendicular basis U,V for direction dirN.
     * dirN is assumed normalized (or at least non-zero).
     */
    public static void buildPerpBasis(Vector3f dirN, Vector3f outU, Vector3f outV) {
        Vector3f a = (Math.abs(dirN.y) < 0.9f) ? Vector3f.UNIT_Y : Vector3f.UNIT_X;
        outU.set(dirN).crossLocal(a);
        float ul = outU.length();
        if (!(ul > 1e-8f)) {
            a = Vector3f.UNIT_Z;
            outU.set(dirN).crossLocal(a);
            ul = outU.length();
            if (!(ul > 1e-8f)) outU.set(1, 0, 0);
        }
        outU.normalizeLocal();
        outV.set(dirN).crossLocal(outU).normalizeLocal();
    }
}