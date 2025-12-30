package org.foxesworld.kalitech.audio;

import com.jme3.math.Vector3f;

final class StereoMath {
    private StereoMath() {}

    static boolean isFinite(Vector3f v) {
        if (v == null) return false;
        return isFinite(v.x) && isFinite(v.y) && isFinite(v.z);
    }

    static boolean isFinite(float f) {
        return !Float.isNaN(f) && !Float.isInfinite(f);
    }
}