// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/SplitComputeFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.math.FastMath;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

import java.util.Objects;

/**
 * Computes cascade split distances (practical split scheme, CDPR-friendly).
 * <p>
 * If {@link Cfg#fixedSplitDistances} is provided, it overrides the computed distances.
 */
public final class SplitComputeFilter implements ShadowFilter {

    public final Cfg cfg;

    public SplitComputeFilter() {
        this(new Cfg());
    }

    public SplitComputeFilter(Cfg cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    private static void enforceMonotonic(float[] a, float gap) {
        if (a == null || a.length < 2) return;
        float g = Math.max(1e-6f, gap);
        for (int i = 1; i < a.length; i++) {
            float prev = a[i - 1];
            if (a[i] <= prev + g) a[i] = prev + g;
        }
    }

    @Override
    public void beforeSplits(ShadowFrameContext ctx) {
        int n = ctx.cascades;
        if (n <= 0) return;

        float N = Math.max(0.0001f, ctx.viewNear);
        float F = Math.max(N + 0.001f, ctx.viewFar);

        float[] out = ctx.splitFarsFinal;

        float lambda = FastMath.clamp(cfg.lambda, 0f, 1f);

        for (int i = 0; i < n; i++) {
            float t = (float) (i + 1) / (float) n;
            float log = N * FastMath.pow(F / N, t);
            float uni = N + (F - N) * t;
            float split = FastMath.interpolateLinear(lambda, uni, log);

            float[] fixed = cfg.fixedSplitDistances;
            if (fixed != null && i < fixed.length) {
                float v = fixed[i];
                if (!Float.isNaN(v)) split = Math.max(N, v);
            }

            out[i] = split;
        }

        int last = n - 1;
        if (ctx.viewFar > 0f && out[last] > ctx.viewFar) out[last] = ctx.viewFar;

        enforceMonotonic(out, 0.001f);
    }

    public static final class Cfg {

        public float lambda = 0.65f;

        /**
         * Optional fixed split distances (view-space far planes, in world units).
         * Must be sorted ascending. If shorter than cascades, remaining splits are computed.
         */
        public float[] fixedSplitDistances = null;
    }
}