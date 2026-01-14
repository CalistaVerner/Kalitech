// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/StableCascadeFitterFilter.java
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import org.foxesworld.kalitech.engine.modules.render.shadows.StableCascadeFitter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

public final class StableCascadeFitterFilter implements ShadowFilter {

    private final StableCascadeFitter fitter = new StableCascadeFitter();
    private final StableCascadeFitter.FitOut out = new StableCascadeFitter.FitOut();
    public float extentsPadding = 1.10f;
    public float zPadding = 25f;
    public float minZSpan = 50f;
    public float quantTexels = 2.0f;

    @Override
    public void afterFit(ShadowFrameContext ctx, int i) {
        ShadowFrameContext.CascadeData c = ctx.c[i];

        StableCascadeFitter.FitCfg fc = fitter.cfg();
        fc.extentsPadding = Math.max(1.0f, extentsPadding);
        fc.zPadding = Math.max(0f, zPadding);
        fc.minZSpan = Math.max(1f, minZSpan);

        float paddedR = Math.max(0.001f, c.radius) * fc.extentsPadding;
        float texel = (2f * paddedR) / (float) Math.max(1, ctx.mapSize);
        c.texelWorldSize = texel;

        fc.radiusQuantStep = (quantTexels > 0f) ? (texel * quantTexels) : 0f;

        fitter.fitSphere(c.centerWS, c.radius, Float.NaN, Float.NaN, out);

        c.centerWS.set(out.centerWS);
        c.radius = out.radius;
        c.zNearRel = out.zNear;
        c.zFarRel = out.zFar;
        c.quantized = out.quantized;

        // Safety clamp
        if (!(c.zFarRel > c.zNearRel)) {
            c.zNearRel = -c.radius;
            c.zFarRel = +c.radius;
        }
        if ((c.zFarRel - c.zNearRel) < minZSpan) {
            float mid = 0.5f * (c.zNearRel + c.zFarRel);
            float half = 0.5f * minZSpan;
            c.zNearRel = mid - half;
            c.zFarRel = mid + half;
        }
    }
}