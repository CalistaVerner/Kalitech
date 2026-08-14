/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;

public final class CascadeHysteresisFilter
implements ShadowFilter {
    public float hysteresis = 0.15f;
    public float smoothing = 0.0f;
    private float[] prev;

    @Override
    public int order() {
        return -900;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        float[] s = ctx.splits;
        if (s == null || s.length < 2) {
            return;
        }
        this.ensurePrev(s.length);
        for (int i = 1; i < s.length - 1; ++i) {
            float cur = s[i];
            float last = this.prev[i];
            if (last > 0.0f && this.hysteresis > 0.0f && Math.abs(cur - last) < this.hysteresis) {
                s[i] = last;
            } else if (last > 0.0f && this.smoothing > 0.0f) {
                float k = this.smoothing;
                if (k < 0.0f) {
                    k = 0.0f;
                }
                if (k > 1.0f) {
                    k = 1.0f;
                }
                s[i] = last + (cur - last) * k;
            }
            this.prev[i] = s[i];
        }
        this.prev[0] = s[0];
        this.prev[s.length - 1] = s[s.length - 1];
    }

    private void ensurePrev(int n) {
        if (this.prev != null && this.prev.length == n) {
            return;
        }
        this.prev = new float[n];
    }

    public void setHysteresis(float hysteresis) {
        this.hysteresis = hysteresis;
    }

    public void setSmoothing(float smoothing) {
        this.smoothing = smoothing;
    }
}

