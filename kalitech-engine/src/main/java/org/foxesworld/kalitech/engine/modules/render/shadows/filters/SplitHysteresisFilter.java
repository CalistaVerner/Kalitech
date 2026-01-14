// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/SplitHysteresisFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import org.foxesworld.kalitech.engine.modules.render.shadows.SplitHysteresisManager;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowPipeline;

import java.util.Objects;

/**
 * Stabilizes cascade split distances using hysteresis and optional smoothing.
 * Prevents edge crawling and cascade popping during camera motion.
 *
 * <p>Presence in pipeline == enabled.</p>
 */
public final class SplitHysteresisFilter implements ShadowFilter {

    private final SplitHysteresisManager hysteresis;

    public SplitHysteresisFilter() {
        this(new SplitHysteresisManager());
    }

    public SplitHysteresisFilter(SplitHysteresisManager hysteresis) {
        this.hysteresis = Objects.requireNonNull(hysteresis, "hysteresis");
    }

    public SplitHysteresisManager.Cfg cfg() {
        return hysteresis.cfg();
    }

    @Override
    public void onAdded(ShadowPipeline pipeline) {
        hysteresis.reset();
    }

    @Override
    public void afterSplits(ShadowFrameContext ctx) {
        float[] splitFars = ctx.splitFarsFinal;
        if (splitFars == null || splitFars.length == 0) {
            return;
        }

        float[] stabilized = hysteresis.stabilize(splitFars, ctx.cameraSpeed, ctx.dt);

        // Preserve original array identity (downstream code may rely on it).
        if (stabilized != splitFars) {
            int n = Math.min(splitFars.length, stabilized.length);
            for (int i = 0; i < n; i++) {
                splitFars[i] = stabilized[i];
            }
        }
    }
}