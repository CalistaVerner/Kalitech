// FILE: org/foxesworld/kalitech/engine/modules/particles/timeline/CurveVec3.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.timeline;

import com.jme3.math.Vector3f;

import java.util.Arrays;

/**
 * Piecewise-linear vec3 curve sampled by normalized life t in [0..1].
 * Format: [t, x, y, z,  t, x, y, z, ...]
 */
public final class CurveVec3 {

    private final float[] keys;
    private final int count;

    public CurveVec3(float[] keys) {
        if (keys == null || keys.length < 8 || (keys.length % 4) != 0) {
            throw new IllegalArgumentException("keys must be [t,x,y,z] blocks with at least 2 points");
        }
        this.keys = Arrays.copyOf(keys, keys.length);
        this.count = keys.length / 4;
    }

    public Vector3f sample(float t, Vector3f out) {
        if (out == null) out = new Vector3f();
        if (!(t >= 0f)) t = 0f;
        if (t > 1f) t = 1f;

        int i0 = 0;
        float t0 = keys[i0];
        if (t <= t0) {
            out.set(keys[i0 + 1], keys[i0 + 2], keys[i0 + 3]);
            return out;
        }

        int last = (count - 1) * 4;
        float tn = keys[last];
        if (t >= tn) {
            out.set(keys[last + 1], keys[last + 2], keys[last + 3]);
            return out;
        }

        for (int i = 0; i < count - 1; i++) {
            int a = i * 4;
            int b = a + 4;
            float ta = keys[a];
            float tb = keys[b];
            if (t >= ta && t <= tb) {
                float span = tb - ta;
                if (!(span > 1e-9f)) {
                    out.set(keys[b + 1], keys[b + 2], keys[b + 3]);
                    return out;
                }
                float u = (t - ta) / span;

                float ax = keys[a + 1], ay = keys[a + 2], az = keys[a + 3];
                float bx = keys[b + 1], by = keys[b + 2], bz = keys[b + 3];

                out.set(
                        ax + (bx - ax) * u,
                        ay + (by - ay) * u,
                        az + (bz - az) * u
                );
                return out;
            }
        }

        out.set(keys[last + 1], keys[last + 2], keys[last + 3]);
        return out;
    }
}