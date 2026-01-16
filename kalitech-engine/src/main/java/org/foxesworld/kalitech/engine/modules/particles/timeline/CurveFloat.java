// FILE: org/foxesworld/kalitech/engine/modules/particles/timeline/CurveFloat.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.timeline;

import java.util.Arrays;

/**
 * Piecewise-linear curve sampled by normalized life t in [0..1].
 */
public final class CurveFloat {

    private final float[] keys;   // t0, v0, t1, v1, ...
    private final int count;

    public CurveFloat(float[] keys) {
        if (keys == null || keys.length < 4 || (keys.length & 1) != 0) {
            throw new IllegalArgumentException("keys must be [t,v] pairs with at least 2 points");
        }
        this.keys = Arrays.copyOf(keys, keys.length);
        this.count = keys.length / 2;
    }

    public float sample(float t) {
        if (!(t >= 0f)) t = 0f;
        if (t > 1f) t = 1f;

        float t0 = keys[0];
        float v0 = keys[1];
        if (t <= t0) return v0;

        int last = (count - 1) * 2;
        float tn = keys[last];
        float vn = keys[last + 1];
        if (t >= tn) return vn;

        for (int i = 0; i < count - 1; i++) {
            int a = i * 2;
            int b = a + 2;
            float ta = keys[a];
            float va = keys[a + 1];
            float tb = keys[b];
            float vb = keys[b + 1];
            if (t >= ta && t <= tb) {
                float span = tb - ta;
                if (!(span > 1e-9f)) return vb;
                float u = (t - ta) / span;
                return va + (vb - va) * u;
            }
        }
        return vn;
    }
}