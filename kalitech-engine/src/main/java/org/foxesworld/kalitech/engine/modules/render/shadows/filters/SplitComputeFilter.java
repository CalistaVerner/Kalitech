// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/SplitComputeFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.math.FastMath;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

import java.util.Arrays;

public final class SplitComputeFilter implements ShadowFilter {

    private final Cfg cfg = new Cfg();

    private static void clampLast(ShadowFrameContext ctx) {
        int last = ctx.cascades - 1;
        if (last < 0) return;
        if (ctx.viewFar > 0f && ctx.splitFarsFinal[last] > ctx.viewFar) ctx.splitFarsFinal[last] = ctx.viewFar;
        enforceMonotonic(ctx.splitFarsFinal, 0.001f);
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
    public String id() {
        return "SplitCompute";
    }

    public Cfg cfg() {
        return cfg;
    }

    public void setFixedSplitDistances(float... distances) {
        if (distances == null || distances.length == 0) {
            cfg.fixedSplitDistances = null;
            return;
        }
        cfg.fixedSplitDistances = distances.clone();
        Arrays.sort(cfg.fixedSplitDistances);
    }

    @Override
    public void beforeSplits(ShadowFrameContext ctx) {
        if (!cfg.enabled) return;
        if (ctx == null) return;
        if (ctx.cascades <= 0) return;

        float near = ctx.viewNear;
        float far = ctx.viewFar;
        if (!(far > near) || !(near > 0f)) return;

        float N = Math.max(cfg.minNear, near);
        float F = Math.max(N + cfg.minSplitGap, far);

        if (cfg.fixedSplitDistances != null && cfg.fixedSplitDistances.length >= ctx.cascades) {
            for (int i = 0; i < ctx.cascades; i++) {
                float v = Math.max(N, cfg.fixedSplitDistances[i]);
                ctx.splitFarsWanted[i] = v;
                ctx.splitFarsFinal[i] = v;
            }
            enforceMonotonic(ctx.splitFarsFinal, cfg.minSplitGap);
            if (cfg.clampLastToViewFar) clampLast(ctx);
            return;
        }

        float lambda = FastMath.clamp(cfg.lambda, 0f, 1f);

        float range = F - N;
        float ratio = F / N;

        for (int i = 0; i < ctx.cascades; i++) {
            float p = (i + 1f) / (float) ctx.cascades;
            float log = N * (float) Math.pow(ratio, p);
            float lin = N + range * p;
            float split = FastMath.interpolateLinear(lambda, lin, log);
            ctx.splitFarsWanted[i] = split;
            ctx.splitFarsFinal[i] = split;
        }

        enforceMonotonic(ctx.splitFarsFinal, cfg.minSplitGap);
        if (cfg.clampLastToViewFar) clampLast(ctx);
    }

    public static final class Cfg {
        public boolean enabled = true;

        public float lambda = 0.65f;

        public float minNear = 1.0f;
        public float minSplitGap = 0.001f;

        public float[] fixedSplitDistances = null;

        public boolean clampLastToViewFar = true;
    }
}