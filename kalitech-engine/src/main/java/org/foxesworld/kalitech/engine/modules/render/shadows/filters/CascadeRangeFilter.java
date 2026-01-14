// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/CascadeRangeFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

public final class CascadeRangeFilter implements ShadowFilter {

    private final Cfg cfg = new Cfg();

    @Override
    public String id() {
        return "CascadeRange";
    }

    public Cfg cfg() {
        return cfg;
    }

    @Override
    public void beforeCascade(ShadowFrameContext ctx, int cascade) {
        if (!cfg.enabled) return;
        if (ctx == null) return;
        if (ctx.splitFarsFinal == null) return;
        if (cascade < 0 || cascade >= ctx.cascades) return;

        float vNear = ctx.viewNear;
        float cNear = (cascade == 0) ? vNear : ctx.splitFarsFinal[cascade - 1];
        float cFar = ctx.splitFarsFinal[cascade];

        if (cNear < cfg.minNear) cNear = cfg.minNear;
        if (cFar <= cNear + cfg.minGap) cFar = cNear + cfg.minGap;

        ShadowFrameContext.CascadeData cd = ctx.c[cascade];
        cd.rangeNear = cNear;
        cd.rangeFar = cFar;
    }

    public static final class Cfg {
        public boolean enabled = true;
        public float minNear = 1.0f;
        public float minGap = 0.001f;
    }
}