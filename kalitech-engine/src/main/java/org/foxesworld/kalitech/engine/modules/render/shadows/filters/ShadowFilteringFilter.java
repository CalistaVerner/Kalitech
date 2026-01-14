// ShadowFilteringFilter.java
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.math.FastMath;
import org.foxesworld.kalitech.engine.modules.render.shadows.ShadowFilteringSystem;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

public final class ShadowFilteringFilter implements ShadowFilter {

    private final ShadowFilteringSystem filtering;
    private float lastRenderMs = 1.0f;
    // CDPR-style filtering parameters
    public float minFilterRadius = 1.0f;
    public float maxFilterRadius = 4.0f;
    public float motionScaleFactor = 2.0f;
    public float distanceFalloff = 0.5f;
    public boolean temporalFiltering = true;
    private float qualityScale = 1.0f;
    private float performanceScale = 1.0f;
    private boolean adaptiveFiltering = true;

    public ShadowFilteringFilter(int cascades) {
        this(cascades, null);
    }

    public ShadowFilteringFilter(int cascades, ShadowFilteringSystem.FilteringConfig cfg) {
        this.filtering = new ShadowFilteringSystem(Math.max(1, cascades), cfg);
    }

    @Override
    public String id() {
        return "ShadowFiltering_CDPR";
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (adaptiveFiltering) {
            updateAdaptiveParameters(ctx);
        }
    }

    @Override
    public void endFrame(ShadowFrameContext ctx) {
        // Apply adaptive filtering based on performance and motion
        float adaptiveDt = ctx.dt * performanceScale;
        float adaptiveRenderMs = lastRenderMs * qualityScale;

        filtering.update(adaptiveDt, adaptiveRenderMs, ctx.splitFarsFinal, ctx.cameraSpeed);

        // Apply CDPR-style per-cascade adjustments
        for (int i = 0; i < ctx.cascades; i++) {
            ShadowFilteringSystem.ShaderConfig config = filtering.getShaderConfig(i);
            if (config != null) {
                adjustCascadeFiltering(config, i, ctx);
            }
        }
    }

    private void updateAdaptiveParameters(ShadowFrameContext ctx) {
        // Adjust quality based on performance
        float targetFPS = 60.0f;
        float currentFPS = 1000.0f / Math.max(0.1f, lastRenderMs);
        performanceScale = FastMath.clamp(currentFPS / targetFPS, 0.5f, 2.0f);

        // Adjust quality based on camera motion
        float motionFactor = FastMath.clamp(ctx.cameraSpeed / 50.0f, 0.0f, 1.0f);
        qualityScale = 1.0f - motionFactor * 0.3f; // Reduce quality when moving fast

        // Apply distance-based falloff
        qualityScale *= (1.0f - distanceFalloff * 0.5f);
    }

    private void adjustCascadeFiltering(ShadowFilteringSystem.ShaderConfig config,
                                        int cascade, ShadowFrameContext ctx) {
        // CDPR-style: larger filter radius for distant cascades
        float cascadeFactor = (float) cascade / ctx.cascades;
        float distanceScale = 1.0f + cascadeFactor * 2.0f;

        // Adjust based on camera motion
        float motionScale = 1.0f + ctx.cameraSpeed * motionScaleFactor * 0.01f;

        // Combine factors
        float effectiveRadius = FastMath.clamp(
                config.filterRadius * distanceScale * motionScale,
                minFilterRadius,
                maxFilterRadius
        );

        // Apply temporal stability for higher quality
        if (temporalFiltering && cascade > 0) {
            effectiveRadius = FastMath.interpolateLinear(0.3f, config.filterRadius, effectiveRadius);
        }

        config.filterRadius = effectiveRadius;
    }

    public void setLastRenderMs(float ms) {
        this.lastRenderMs = Math.max(0.01f, ms);
    }

    public void setAdaptiveFiltering(boolean enabled) {
        this.adaptiveFiltering = enabled;
    }

    public void setQualityScale(float scale) {
        this.qualityScale = FastMath.clamp(scale, 0.1f, 2.0f);
    }

    public ShadowFilteringSystem system() {
        return filtering;
    }
}