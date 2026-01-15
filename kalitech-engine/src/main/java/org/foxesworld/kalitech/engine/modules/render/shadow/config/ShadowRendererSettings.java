// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/config/ShadowRendererSettings.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import java.util.Objects;

/**
 * High-level shadow renderer settings.
 * <p>
 * This is the centralized configuration bundle for shadow rendering. It separates
 * "structural" settings (shadow map size, number of cascades) from the pipeline
 * behavior (stability, snapping, fitting, etc.).
 * <p>
 * Structural settings may require recreating the underlying renderer instance.
 */
public final class ShadowRendererSettings {

    private final ShadowRenderConfig pipeline = new ShadowRenderConfig();
    private int shadowMapSize = 2048;
    private int cascades = 4;
    private float lambda = 0.65f;
    private float intensity = 1.0f;
    /**
     * If non-zero, overrides the shadow view far plane used by jME.
     */
    private float zFarOverride = 0.0f;

    /**
     * Creates a CDPR-like preset focused on maximum stability.
     * <p>
     * This is a convenience factory; you may freely modify the returned object.
     */
    public static ShadowRendererSettings cdpr8192() {
        ShadowRendererSettings s = new ShadowRendererSettings();
        s.shadowMapSize = 8192;
        s.cascades = 4;
        s.lambda = 0.65f;
        s.intensity = 1.0f;
        ShadowRenderConfig preset = ShadowRenderConfig.cdpr8192();
        copyInto(s.pipeline, preset);
        return s;
    }

    private static void copyInto(ShadowRenderConfig dst, ShadowRenderConfig src) {
        Objects.requireNonNull(dst, "dst");
        Objects.requireNonNull(src, "src");

        dst.cascades()
                .setHysteresisEnabled(src.cascades().isHysteresisEnabled())
                .setSplitHysteresis(src.cascades().getSplitHysteresis())
                .setSplitSmoothing(src.cascades().getSplitSmoothing())
                .setFixedSplits(src.cascades().getFixedSplits());

        dst.fitting()
                .setMode(src.fitting().getMode())
                .setMinNear(src.fitting().getMinNear())
                .setCasterBackBase(src.fitting().getCasterBackBase())
                .setCasterBackCascadeMul(src.fitting().getCasterBackCascadeMul())
                .setReceiverFrontBase(src.fitting().getReceiverFrontBase())
                .setXyPadding(src.fitting().getXyPadding())
                .setForceSquare(src.fitting().isForceSquare())
                .setSizeQuantizeTexels(src.fitting().getSizeQuantizeTexels())
                .setLockNearCascadeSize(src.fitting().isLockNearCascadeSize())
                .setNearTierTexels(src.fitting().getNearTierTexels())
                .setNearShrinkHysteresisTiers(src.fitting().getNearShrinkHysteresisTiers());

        dst.snapping()
                .setEnabled(src.snapping().isEnabled())
                .setSnapFirstCascades(src.snapping().getSnapFirstCascades());

        dst.snapping().temporalGate()
                .setEnabled(src.snapping().temporalGate().isEnabled())
                .setMinMoveTexels(src.snapping().temporalGate().getMinMoveTexels())
                .setMinRotateDeg(src.snapping().temporalGate().getMinRotateDeg())
                .setGatedFirstCascades(src.snapping().temporalGate().getGatedFirstCascades());

        dst.debug()
                .setTraceEnabled(src.debug().isTraceEnabled())
                .setTraceEveryFrames(src.debug().getTraceEveryFrames());
    }

    private static long mix(long h, int v) {
        h ^= (v & 0xffffffffL);
        h *= 1099511628211L;
        return h;
    }

    public int getShadowMapSize() {
        return shadowMapSize;
    }

    public ShadowRendererSettings setShadowMapSize(int shadowMapSize) {
        this.shadowMapSize = shadowMapSize;
        return this;
    }

    public int getCascades() {
        return cascades;
    }

    public ShadowRendererSettings setCascades(int cascades) {
        this.cascades = cascades;
        return this;
    }

    public float getLambda() {
        return lambda;
    }

    public ShadowRendererSettings setLambda(float lambda) {
        this.lambda = lambda;
        return this;
    }

    public float getIntensity() {
        return intensity;
    }

    public ShadowRendererSettings setIntensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    public float getZFarOverride() {
        return zFarOverride;
    }

    public ShadowRendererSettings setZFarOverride(float zFarOverride) {
        this.zFarOverride = zFarOverride;
        return this;
    }

    public ShadowRenderConfig pipeline() {
        return pipeline;
    }

    /**
     * Computes a deterministic signature for change detection.
     */
    public long signature() {
        long h = 1469598103934665603L;
        h = mix(h, shadowMapSize);
        h = mix(h, cascades);
        h = mix(h, Float.floatToIntBits(lambda));
        h = mix(h, Float.floatToIntBits(intensity));
        h = mix(h, Float.floatToIntBits(zFarOverride));
        h = mix(h, (int) (pipeline.signature() ^ (pipeline.signature() >>> 32)));
        return h;
    }

    /**
     * Validates and normalizes values.
     * <p>
     * This method clamps values into safe ranges and throws for invalid structural values.
     */
    public ShadowRendererSettings validate() {
        if (shadowMapSize <= 0) {
            throw new IllegalArgumentException("shadowMapSize must be > 0");
        }
        if (cascades < 1 || cascades > 8) {
            throw new IllegalArgumentException("cascades must be in [1..8]");
        }
        if (lambda < 0.0f) lambda = 0.0f;
        if (lambda > 1.0f) lambda = 1.0f;
        if (intensity < 0.0f) intensity = 0.0f;
        if (zFarOverride < 0.0f) zFarOverride = 0.0f;
        return this;
    }
}