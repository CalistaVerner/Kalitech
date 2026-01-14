// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/MaterialDefaultsFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

public final class MaterialDefaultsFilter implements ShadowFilter {

    private final Cfg cfg = new Cfg();

    @Override
    public String id() {
        return "MaterialDefaults";
    }

    public Cfg cfg() {
        return cfg;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (!cfg.enabled) return;
        if (ctx == null) return;

        ctx.material.shadowBias = cfg.shadowBias;
        ctx.material.shadowSlopeBias = cfg.shadowSlopeBias;
        ctx.material.shadowNormalOffset = cfg.shadowNormalOffset;

        ctx.material.cascadeBlendEnabled = cfg.cascadeBlendEnabled;
        ctx.material.cascadeBlendLen = cfg.cascadeBlendLen;
    }

    public static final class Cfg {
        public boolean enabled = true;

        public float shadowBias = 0.0008f;
        public float shadowSlopeBias = 2.0f;
        public float shadowNormalOffset = 0.0f;

        public boolean cascadeBlendEnabled = true;
        public float cascadeBlendLen = 1.5f;
    }
}