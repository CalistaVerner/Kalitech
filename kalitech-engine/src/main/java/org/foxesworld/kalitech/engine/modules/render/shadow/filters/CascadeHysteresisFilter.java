// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/CascadeHysteresisFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;

/**
 * Stabilizes cascade split distances using hysteresis and optional smoothing to prevent popping.
 */
public final class CascadeHysteresisFilter implements ShadowFilter {

    /**
     * World distance hysteresis. If delta is smaller than this value, keep previous split distance.
     */
    public float hysteresis = 0.15f;

    /**
     * Smoothing factor in [0..1]. 0 disables smoothing.
     */
    public float smoothing = 0.0f;

    private float[] prev;

    @Override
    public int order() {
        return -900;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        float[] s = ctx.splits;
        if (s == null || s.length < 2) return;

        ensurePrev(s.length);

        for (int i = 1; i < s.length - 1; i++) {
            float cur = s[i];
            float last = prev[i];

            if (last > 0f && hysteresis > 0f && Math.abs(cur - last) < hysteresis) {
                s[i] = last;
            } else if (last > 0f && smoothing > 0f) {
                float k = smoothing;
                if (k < 0f) k = 0f;
                if (k > 1f) k = 1f;
                s[i] = last + (cur - last) * k;
            }

            prev[i] = s[i];
        }

        prev[0] = s[0];
        prev[s.length - 1] = s[s.length - 1];
    }

    private void ensurePrev(int n) {
        if (prev != null && prev.length == n) return;
        prev = new float[n];
    }

    public void setHysteresis(float hysteresis) {
        this.hysteresis = hysteresis;
    }

    public void setSmoothing(float smoothing) {
        this.smoothing = smoothing;
    }
}