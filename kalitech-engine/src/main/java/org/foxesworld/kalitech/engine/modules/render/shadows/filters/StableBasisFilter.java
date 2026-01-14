// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/StableBasisFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.math.Matrix3f;
import org.foxesworld.kalitech.engine.modules.render.shadows.StableLightBasis;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowPipeline;

import java.util.Objects;

/**
 * Computes deterministic stable light basis (anti-flip/anti-roll).
 *
 * <p>Must run per-cascade before fitting / camera placement so that all subsequent
 * filters use a stable orientation. Presence in pipeline == enabled.</p>
 */
public final class StableBasisFilter implements ShadowFilter {

    private final StableLightBasis.Config cfg;
    private StableLightBasis stableBasis;

    private int lastCascades = -1;
    private final Matrix3f tmp = new Matrix3f();

    public StableBasisFilter() {
        this(new StableLightBasis.Config());
    }

    public StableBasisFilter(StableLightBasis.Config cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    public StableLightBasis.Config cfg() {
        return cfg;
    }

    @Override
    public void onAdded(ShadowPipeline pipeline) {
        stableBasis = null;
        lastCascades = -1;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        // Ensure basis instance matches current cascade count
        int cascades = Math.max(1, ctx.cascades);
        if (stableBasis == null || lastCascades != cascades) {
            stableBasis = new StableLightBasis(cascades, cfg);
            lastCascades = cascades;
        }
    }

    @Override
    public void beforeCascade(ShadowFrameContext ctx, int cascade) {
        // Compute per-cascade stable basis and expose it through ctx.basis for downstream filters
        stableBasis.computeBasis(cascade, ctx.lightDir, ctx.dt, tmp);
        ctx.basis.set(tmp);
    }
}