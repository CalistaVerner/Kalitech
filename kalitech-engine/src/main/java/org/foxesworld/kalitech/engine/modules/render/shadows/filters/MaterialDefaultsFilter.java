// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/MaterialDefaultsFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

import java.util.Objects;

/**
 * Writes receiver-material defaults to the shared frame context.
 * <p>
 * Presence in pipeline == enabled. Do not add runtime "enabled" switches.
 */
public final class MaterialDefaultsFilter implements ShadowFilter {

    public static final class Cfg {

        public float shadowBias = 0.0008f;
        public float shadowSlopeBias = 2.0f;
        public float shadowNormalOffset = 0.0f;

        public boolean cascadeBlendEnabled = true;
        public float cascadeBlendLen = 1.5f;
    }

    public final Cfg cfg;

    public MaterialDefaultsFilter() {
        this(new Cfg());
    }

    public MaterialDefaultsFilter(Cfg cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        ShadowFrameContext.MaterialState m = ctx.material;

        m.shadowBias = cfg.shadowBias;
        m.shadowSlopeBias = cfg.shadowSlopeBias;
        m.shadowNormalOffset = cfg.shadowNormalOffset;

        m.cascadeBlendEnabled = cfg.cascadeBlendEnabled;
        m.cascadeBlendLen = cfg.cascadeBlendLen;
    }
}
