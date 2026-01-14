// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/SplitHysteresisFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import org.foxesworld.kalitech.engine.modules.render.shadows.SplitHysteresisManager;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

public final class SplitHysteresisFilter implements ShadowFilter {

    private SplitHysteresisManager hysteresis = new SplitHysteresisManager();

    /**
     * Resets internal history by recreating manager instance.
     * Works even if SplitHysteresisManager has no reset/clear API.
     */
    public void resetHistory() {
        hysteresis = new SplitHysteresisManager();
    }

    @Override
    public void beforeSplits(ShadowFrameContext ctx) {
        if (ctx == null || ctx.splitFarsWanted == null || ctx.splitFarsFinal == null) return;
        float[] stable = hysteresis.stabilize(ctx.splitFarsWanted, ctx.cameraSpeed);
        System.arraycopy(stable, 0, ctx.splitFarsFinal, 0, ctx.cascades);
    }
}