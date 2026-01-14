// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/CascadeHysteresisFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;

/**
 * Stabilizes split distances using hysteresis to prevent cascade popping.
 */
public final class CascadeHysteresisFilter implements ShadowFilter {

    /**
     * World/view space distance hysteresis. If delta is smaller than this value,
     * keep previous split distance.
     */
    public float hysteresis = 6.0f;

    /**
     * Optional smoothing (0..1). 0 means hard hysteresis only.
     */
    public float smoothing = 0.15f;

    private float[] prev;

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    @Override
    public int order() {
        return -2000;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        float[] s = ctx.splits;
        if (s == null || s.length < 2) return;

        if (prev == null || prev.length != s.length) {
            prev = s.clone();
            return;
        }

        // Keep split[0] (near) and split[last] (far) as provided.
        for (int i = 1; i < s.length - 1; i++) {
            float target = s[i];
            float old = prev[i];

            float delta = target - old;
            if (delta < 0f) delta = -delta;

            if (delta < hysteresis) {
                s[i] = old;
            } else if (smoothing > 0f) {
                float a = clamp01(smoothing);
                s[i] = old + (target - old) * a;
            }

            prev[i] = s[i];
        }

        prev[0] = s[0];
        prev[s.length - 1] = s[s.length - 1];
    }

    public void setHysteresis(float hysteresis) {
        this.hysteresis = hysteresis;
    }

    public void setPrev(float[] prev) {
        this.prev = prev;
    }

    public void setSmoothing(float smoothing) {
        this.smoothing = smoothing;
    }
}