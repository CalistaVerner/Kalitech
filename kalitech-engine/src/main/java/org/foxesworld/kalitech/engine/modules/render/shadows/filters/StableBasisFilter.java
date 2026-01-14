// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/StableBasisFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.math.Matrix3f;
import org.foxesworld.kalitech.engine.modules.render.shadows.StableLightBasis;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

public final class StableBasisFilter implements ShadowFilter {

    private final Cfg cfg = new Cfg();
    private final StableLightBasis stableBasis;
    private final Matrix3f tmp = new Matrix3f();
    public StableBasisFilter(int cascades, StableLightBasis.Config basisCfg) {
        this.stableBasis = new StableLightBasis(Math.max(1, cascades), basisCfg);
    }

    @Override
    public String id() {
        return "StableBasis";
    }

    public Cfg cfg() {
        return cfg;
    }

    @Override
    public void afterFit(ShadowFrameContext ctx, int cascade) {
        if (!cfg.enabled) return;
        if (ctx == null) return;

        stableBasis.computeBasis(cascade, ctx.lightDir, ctx.dt, tmp);
        ctx.basis.set(tmp);
    }

    public static final class Cfg {
        public boolean enabled = true;
    }
}