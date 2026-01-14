// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/StableCascadeFitterFilter.java
// Author: Calista Verner (K\u039bYL\u039b)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import org.foxesworld.kalitech.engine.modules.render.shadows.StableCascadeFitter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

import java.util.Objects;

/**
 * Quantizes and pads per-cascade sphere fit to improve temporal stability.
 * <p>
 * This filter implements the "stable cascade" trick:
 * radius is padded (guard band) and optionally quantized to an integer texel multiple,
 * which prevents sub-texel projection changes from causing shimmering.
 * <p>
 * Presence in pipeline == enabled.
 */
public final class StableCascadeFitterFilter implements ShadowFilter {

    public final Cfg cfg;
    private final StableCascadeFitter fitter = new StableCascadeFitter();
    private final StableCascadeFitter.FitOut out = new StableCascadeFitter.FitOut();
    public StableCascadeFitterFilter() {
        this(new Cfg());
    }

    public StableCascadeFitterFilter(Cfg cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    @Override
    public String id() {
        return "StableCascadeFitter";
    }

    @Override
    public void afterFit(ShadowFrameContext ctx, int cascade) {
        ShadowFrameContext.CascadeData c = ctx.c[cascade];

        StableCascadeFitter.FitCfg fc = fitter.cfg();
        fc.extentsPadding = Math.max(1.0f, cfg.extentsPadding);
        fc.zPadding = Math.max(0f, cfg.zPadding);
        fc.minZSpan = Math.max(1f, cfg.minZSpan);

        float paddedR = Math.max(0.001f, c.radius) * fc.extentsPadding;
        float texel = (2f * paddedR) / (float) Math.max(1, ctx.mapSize);
        c.texelWorldSize = texel;

        fc.radiusQuantStep = (cfg.quantTexels > 0f) ? (texel * cfg.quantTexels) : 0f;

        fitter.fitSphere(c.centerWS, c.radius, Float.NaN, Float.NaN, out);

        c.centerWS.set(out.centerWS);
        c.radius = out.radius;
        c.zNearRel = out.zNear;
        c.zFarRel = out.zFar;
        c.quantized = out.quantized;

        if (!(c.zFarRel > c.zNearRel)) {
            c.zNearRel = -c.radius;
            c.zFarRel = +c.radius;
        }

        float span = c.zFarRel - c.zNearRel;
        if (span < fc.minZSpan) {
            float mid = 0.5f * (c.zNearRel + c.zFarRel);
            float half = 0.5f * fc.minZSpan;
            c.zNearRel = mid - half;
            c.zFarRel = mid + half;
        }
    }

    public static final class Cfg {
        /**
         * Extra XY padding around the fitted sphere (>= 1).
         */
        public float extentsPadding = 1.10f;

        /**
         * Extra Z padding in light space (>= 0).
         */
        public float zPadding = 25f;

        /**
         * Minimum Z span to avoid near/far collapse (>= 1).
         */
        public float minZSpan = 50f;

        /**
         * Quantization in texels (>= 0). 0 disables quantization.
         * Typical: 1..4.
         */
        public float quantTexels = 2.0f;
    }
}