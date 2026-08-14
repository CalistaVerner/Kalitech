/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.config.ShadowRenderConfig;

public final class ShadowRendererSettings {
    private static final Logger log = LogManager.getLogger(ShadowRendererSettings.class);
    private final ShadowRenderConfig pipeline = new ShadowRenderConfig();
    private int shadowMapSize = 2048;
    private int cascades = 4;
    private float lambda = 0.65f;
    private float intensity = 1.0f;
    private float zFarOverride = 0.0f;

    public static ShadowRendererSettings cdpr8192() {
        ShadowRendererSettings s = new ShadowRendererSettings();
        s.shadowMapSize = 8192;
        s.cascades = 4;
        s.lambda = 0.65f;
        s.intensity = 1.0f;
        ShadowRenderConfig preset = ShadowRenderConfig.cdpr8192();
        ShadowRendererSettings.copyInto(s.pipeline, preset);
        log.debug("[shadow][cfg] preset=cdpr8192 map={} cascades={}", (Object)s.shadowMapSize, (Object)s.cascades);
        return s;
    }

    private static void copyInto(ShadowRenderConfig dst, ShadowRenderConfig src) {
        Objects.requireNonNull(dst, "dst");
        Objects.requireNonNull(src, "src");
        dst.cascades().setHysteresisEnabled(src.cascades().isHysteresisEnabled()).setSplitHysteresis(src.cascades().getSplitHysteresis()).setSplitSmoothing(src.cascades().getSplitSmoothing()).setFixedSplits(src.cascades().getFixedSplits());
        dst.fitting().setMode(src.fitting().getMode()).setMinNear(src.fitting().getMinNear()).setCasterBackBase(src.fitting().getCasterBackBase()).setCasterBackCascadeMul(src.fitting().getCasterBackCascadeMul()).setReceiverFrontBase(src.fitting().getReceiverFrontBase()).setXyPadding(src.fitting().getXyPadding()).setForceSquare(src.fitting().isForceSquare()).setSizeQuantizeTexels(src.fitting().getSizeQuantizeTexels()).setLockNearCascadeSize(src.fitting().isLockNearCascadeSize()).setNearTierTexels(src.fitting().getNearTierTexels()).setNearShrinkHysteresisTiers(src.fitting().getNearShrinkHysteresisTiers());
        dst.snapping().setEnabled(src.snapping().isEnabled()).setSnapFirstCascades(src.snapping().getSnapFirstCascades());
        dst.snapping().temporalGate().setEnabled(src.snapping().temporalGate().isEnabled()).setMinMoveTexels(src.snapping().temporalGate().getMinMoveTexels()).setMinRotateDeg(src.snapping().temporalGate().getMinRotateDeg()).setGatedFirstCascades(src.snapping().temporalGate().getGatedFirstCascades());
        dst.debug().setTraceEnabled(src.debug().isTraceEnabled()).setTraceEveryFrames(src.debug().getTraceEveryFrames());
    }

    private static long mix(long h, int v) {
        h ^= (long)v & 0xFFFFFFFFL;
        return h *= 1099511628211L;
    }

    public int getShadowMapSize() {
        return this.shadowMapSize;
    }

    public ShadowRendererSettings setShadowMapSize(int shadowMapSize) {
        this.shadowMapSize = shadowMapSize;
        return this;
    }

    public int getCascades() {
        return this.cascades;
    }

    public ShadowRendererSettings setCascades(int cascades) {
        this.cascades = cascades;
        return this;
    }

    public float getLambda() {
        return this.lambda;
    }

    public ShadowRendererSettings setLambda(float lambda) {
        this.lambda = lambda;
        return this;
    }

    public float getIntensity() {
        return this.intensity;
    }

    public ShadowRendererSettings setIntensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    public float getZFarOverride() {
        return this.zFarOverride;
    }

    public ShadowRendererSettings setZFarOverride(float zFarOverride) {
        this.zFarOverride = zFarOverride;
        return this;
    }

    public ShadowRenderConfig pipeline() {
        return this.pipeline;
    }

    public long signature() {
        long h = 1469598103934665603L;
        long a = this.structuralSignature();
        long b = this.dynamicSignature();
        long c = this.pipelineSignature();
        h = ShadowRendererSettings.mix(h, Long.hashCode(a));
        h = ShadowRendererSettings.mix(h, Long.hashCode(b));
        h = ShadowRendererSettings.mix(h, Long.hashCode(c));
        return h;
    }

    public long structuralSignature() {
        long h = 1469598103934665603L;
        h = ShadowRendererSettings.mix(h, this.shadowMapSize);
        h = ShadowRendererSettings.mix(h, this.cascades);
        return h;
    }

    public long dynamicSignature() {
        long h = 1469598103934665603L;
        h = ShadowRendererSettings.mix(h, Float.floatToIntBits(this.lambda));
        h = ShadowRendererSettings.mix(h, Float.floatToIntBits(this.intensity));
        h = ShadowRendererSettings.mix(h, Float.floatToIntBits(this.zFarOverride));
        return h;
    }

    public long pipelineSignature() {
        return this.pipeline.signature();
    }

    public ShadowRendererSettings validate() {
        if (this.shadowMapSize <= 0) {
            throw new IllegalArgumentException("shadowMapSize must be > 0");
        }
        if (this.cascades < 1 || this.cascades > 8) {
            throw new IllegalArgumentException("cascades must be in [1..8]");
        }
        float oldLambda = this.lambda;
        float oldIntensity = this.intensity;
        float oldZFar = this.zFarOverride;
        if (this.lambda < 0.0f) {
            this.lambda = 0.0f;
        }
        if (this.lambda > 1.0f) {
            this.lambda = 1.0f;
        }
        if (this.intensity < 0.0f) {
            this.intensity = 0.0f;
        }
        if (this.zFarOverride < 0.0f) {
            this.zFarOverride = 0.0f;
        }
        if (oldLambda != this.lambda || oldIntensity != this.intensity || oldZFar != this.zFarOverride) {
            log.debug("[shadow][cfg] normalize lambda:{}->{} intensity:{}->{} zFarOverride:{}->{}", (Object)Float.valueOf(oldLambda), (Object)Float.valueOf(this.lambda), (Object)Float.valueOf(oldIntensity), (Object)Float.valueOf(this.intensity), (Object)Float.valueOf(oldZFar), (Object)Float.valueOf(this.zFarOverride));
        }
        return this;
    }
}

