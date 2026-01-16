// FILE: org/foxesworld/kalitech/engine/modules/particles/timeline/CurveColor.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.timeline;

import com.jme3.math.ColorRGBA;

import java.util.Arrays;

/**
 * Piecewise-linear color curve sampled by normalized life t in [0..1].
 * Format: [t, r, g, b, a,  t, r, g, b, a, ...]
 */
public final class CurveColor {

    private final float[] keys;
    private final int count;

    public CurveColor(float[] keys) {
        if (keys == null || keys.length < 10 || (keys.length % 5) != 0) {
            throw new IllegalArgumentException("keys must be [t,r,g,b,a] blocks with at least 2 points");
        }
        this.keys = Arrays.copyOf(keys, keys.length);
        this.count = keys.length / 5;
    }

    public ColorRGBA sample(float t, ColorRGBA out) {
        if (out == null) out = new ColorRGBA();
        if (!(t >= 0f)) t = 0f;
        if (t > 1f) t = 1f;

        int i0 = 0;
        float t0 = keys[i0];
        if (t <= t0) {
            out.set(keys[i0 + 1], keys[i0 + 2], keys[i0 + 3], keys[i0 + 4]);
            return out;
        }

        int last = (count - 1) * 5;
        float tn = keys[last];
        if (t >= tn) {
            out.set(keys[last + 1], keys[last + 2], keys[last + 3], keys[last + 4]);
            return out;
        }

        for (int i = 0; i < count - 1; i++) {
            int a = i * 5;
            int b = a + 5;
            float ta = keys[a];
            float tb = keys[b];
            if (t >= ta && t <= tb) {
                float span = tb - ta;
                if (!(span > 1e-9f)) {
                    out.set(keys[b + 1], keys[b + 2], keys[b + 3], keys[b + 4]);
                    return out;
                }
                float u = (t - ta) / span;

                float ar = keys[a + 1], ag = keys[a + 2], ab = keys[a + 3], aa = keys[a + 4];
                float br = keys[b + 1], bg = keys[b + 2], bb = keys[b + 3], ba = keys[b + 4];

                out.set(
                        ar + (br - ar) * u,
                        ag + (bg - ag) * u,
                        ab + (bb - ab) * u,
                        aa + (ba - aa) * u
                );
                return out;
            }
        }

        out.set(keys[last + 1], keys[last + 2], keys[last + 3], keys[last + 4]);
        return out;
    }
}