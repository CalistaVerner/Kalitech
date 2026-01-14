// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/CascadeRangeFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

import java.util.Objects;

/**
 * Validates and clamps cascade ranges (near/far) to keep them monotonic and sane.
 */
public final class CascadeRangeFilter implements ShadowFilter {

    public static final class Cfg {
        public float minNear = 1.0f;
        public float minGap = 0.001f;
    }

    public final Cfg cfg;

    public CascadeRangeFilter() {
        this(new Cfg());
    }

    public CascadeRangeFilter(Cfg cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    @Override
    public void afterSplits(ShadowFrameContext ctx) {
        int n = ctx.cascades;
        if (n <= 0) return;

        float near = Math.max(cfg.minNear, ctx.viewNear);
        float gap = Math.max(1e-6f, cfg.minGap);

        float prevFar = near;
        for (int i = 0; i < n; i++) {
            ShadowFrameContext.CascadeData cd = ctx.c[i];

            float sFar = ctx.splitFarsFinal[i];
            if (sFar <= prevFar + gap) sFar = prevFar + gap;
            if (ctx.viewFar > 0f && sFar > ctx.viewFar) sFar = ctx.viewFar;

            cd.rangeNear = (i == 0) ? near : prevFar;
            cd.rangeFar = sFar;

            prevFar = sFar;
        }
    }
}
