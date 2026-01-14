// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/ShadowFilteringFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import org.foxesworld.kalitech.engine.modules.render.shadows.ShadowFilteringSystem;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

public final class ShadowFilteringFilter implements ShadowFilter {

    private final ShadowFilteringSystem filtering;

    private float lastRenderMs = 1.0f;

    public ShadowFilteringFilter(int cascades) {
        this(cascades, null);
    }

    public ShadowFilteringFilter(int cascades, ShadowFilteringSystem.FilteringConfig cfg) {
        this.filtering = new ShadowFilteringSystem(Math.max(1, cascades), cfg);
    }

    /**
     * optional: feed timing from your renderer profiler
     */
    public void setLastRenderMs(float ms) {
        this.lastRenderMs = Math.max(0.01f, ms);
    }

    @Override
    public void endFrame(ShadowFrameContext ctx) {
        filtering.update(ctx.dt, lastRenderMs, ctx.splitFarsFinal, ctx.cameraSpeed);
        // Реальную установку uniform’ов делайте в setMaterialParameters рендера,
        // читая filtering.getShaderConfig(cascade) и выставляя параметры.
    }

    public ShadowFilteringSystem system() {
        return filtering;
    }
}