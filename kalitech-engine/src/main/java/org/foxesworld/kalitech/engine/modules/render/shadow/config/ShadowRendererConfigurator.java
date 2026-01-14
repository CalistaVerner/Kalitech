// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/config/ShadowRendererConfigurator.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import org.foxesworld.kalitech.engine.modules.render.shadow.PipelineDirectionalLightShadowRenderer;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.*;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipeline;

import java.util.Objects;

/**
 * Applies {@link ShadowRenderConfig} to a renderer and rebuilds its pipeline deterministically.
 */
public final class ShadowRendererConfigurator {

    private ShadowRendererConfigurator() {
    }

    public static void apply(PipelineDirectionalLightShadowRenderer r, ShadowRenderConfig cfg) {
        Objects.requireNonNull(r, "renderer");
        Objects.requireNonNull(cfg, "cfg");

        // Cascades: fixed splits are applied at renderer-level (before pipeline).
        float[] fixed = ShadowRenderConfig.sortedCloneOrNull(cfg.cascades().getFixedSplits());
        if (fixed != null) {
            r.setFixedSplitDistances(fixed);
        } else {
            r.clearFixedSplitDistances();
        }

        ShadowPipeline p = r.pipeline();
        p.clear();

        // 1) Cascade split stabilization (popping guard)
        if (cfg.cascades().isHysteresisEnabled()) {
            CascadeHysteresisFilter h = new CascadeHysteresisFilter();
            h.setHysteresis(cfg.cascades().getSplitHysteresis());
            h.setSmoothing(cfg.cascades().getSplitSmoothing());
            p.add(h);
        }

        // 2) Stable deterministic light basis (anti-flip)
        p.add(new StableLightBasisFilter());

        // 3) Shadow camera fitting policy
        switch (cfg.fitting().getMode()) {
            case STABLE_AABB: {
                StableFitShadowCamFilter f = new StableFitShadowCamFilter();
                f.setMinNear(cfg.fitting().getMinNear());
                f.setCasterBackBase(cfg.fitting().getCasterBackBase());
                f.setCasterBackCascadeMul(cfg.fitting().getCasterBackCascadeMul());
                f.setReceiverFrontBase(cfg.fitting().getReceiverFrontBase());
                f.setExtentsPadding(cfg.fitting().getXyPadding());
                f.setForceSquare(cfg.fitting().isForceSquare());
                f.setSizeQuantizeTexels(cfg.fitting().getSizeQuantizeTexels());
                p.add(f);
                break;
            }
            case TIGHT_STABLE:
            default: {
                TightStableFitShadowCamFilter f = new TightStableFitShadowCamFilter();
                f.setMinNear(cfg.fitting().getMinNear());
                f.setCasterBackBase(cfg.fitting().getCasterBackBase());
                f.setCasterBackCascadeMul(cfg.fitting().getCasterBackCascadeMul());
                f.setReceiverFrontBase(cfg.fitting().getReceiverFrontBase());
                f.setXyPadding(cfg.fitting().getXyPadding());
                f.setForceSquare(cfg.fitting().isForceSquare());
                f.setSizeQuantizeTexels(cfg.fitting().getSizeQuantizeTexels());
                f.setLockNearCascadeSize(cfg.fitting().isLockNearCascadeSize());
                f.setNearTierTexels(cfg.fitting().getNearTierTexels());
                f.setNearShrinkHysteresisTiers(cfg.fitting().getNearShrinkHysteresisTiers());
                p.add(f);
                break;
            }
        }

        // 4) Texel snapping + temporal gating
        TemporalSnapGateFilter gate = null;
        if (cfg.snapping().temporalGate().isEnabled()) {
            gate = new TemporalSnapGateFilter();
            gate.setMinMoveTexels(cfg.snapping().temporalGate().getMinMoveTexels());
            gate.setMinRotateDeg(cfg.snapping().temporalGate().getMinRotateDeg());
            gate.setGatedFirstCascades(cfg.snapping().temporalGate().getGatedFirstCascades());
            p.add(gate);
        }

        TexelSnapFilter snap = new TexelSnapFilter();
        snap.setEnabled(cfg.snapping().isEnabled());
        snap.setSnapFirstCascades(cfg.snapping().getSnapFirstCascades());
        snap.setGate(gate);
        p.add(snap);

        // 5) Debug / tracing
        if (cfg.debug().isTraceEnabled()) {
            ShadowTraceFilter t = new ShadowTraceFilter();
            t.setEveryFrames(cfg.debug().getTraceEveryFrames());
            p.add(t);
        }
    }
}