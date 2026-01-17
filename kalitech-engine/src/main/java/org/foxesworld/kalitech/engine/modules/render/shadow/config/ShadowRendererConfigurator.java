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

        float[] fixed = ShadowRenderConfig.sortedCloneOrNull(cfg.cascades().getFixedSplits());
        if (fixed != null) r.setFixedSplitDistances(fixed);
        else r.clearFixedSplitDistances();

        ShadowPipeline p = r.pipeline();
        p.clear();

        if (cfg.cascades().isHysteresisEnabled()) {
            CascadeHysteresisFilter h = new CascadeHysteresisFilter();
            h.setHysteresis(cfg.cascades().getSplitHysteresis());
            h.setSmoothing(cfg.cascades().getSplitSmoothing());
            p.add(h);
        }

        p.add(new StableLightBasisFilter());

        ShadowTemporalStabilityPolicyFilter policy = new ShadowTemporalStabilityPolicyFilter();
        policy.setEnabled(true);

        ShadowRenderConfig.TemporalGate tg = cfg.snapping().temporalGate();
        if (cfg.snapping().isEnabled() && tg.isEnabled()) {
            policy.setMinMoveTexelsForSnap(tg.getMinMoveTexels());
            policy.setMinRotateDegForSnap(tg.getMinRotateDeg());
            policy.setGateSnapFirstCascades(tg.getGatedFirstCascades());

            policy.setMinMoveTexelsForRefit(Math.max(0.25f, tg.getMinMoveTexels() * 0.75f));
            policy.setMinRotateDegForRefit(Math.max(0.10f, tg.getMinRotateDeg() * 0.75f));
            policy.setGateRefitFirstCascades(Math.max(0, tg.getGatedFirstCascades()));
        }
        p.add(policy);

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
                f.setForceSquare(cfg.fitting().isForceSquare());
                f.setSizeQuantizeTexels(cfg.fitting().getSizeQuantizeTexels());
                f.setLockNearCascadeSize(cfg.fitting().isLockNearCascadeSize());
                f.setNearTierTexels(cfg.fitting().getNearTierTexels());
                f.setNearShrinkHysteresisTiers(cfg.fitting().getNearShrinkHysteresisTiers());
                p.add(f);
                break;
            }
        }

        ShadowSnapperFilter snap = new ShadowSnapperFilter();
        snap.setEnabled(cfg.snapping().isEnabled());
        snap.setSnapFirstCascades(cfg.snapping().getSnapFirstCascades());
        snap.setHoldEnabled(true);
        float holdTexels = (cfg.snapping().isEnabled() && tg.isEnabled())
                ? Math.max(1.0f, tg.getMinMoveTexels())
                : 1.25f;
        snap.setHoldThresholdTexels(holdTexels);
        p.add(snap);

        // Mandatory GPU packet build stage (CPU-side).
        p.add(new ShadowGpuParamsPackFilter());

        if (cfg.debug().isTraceEnabled()) {
            ShadowTraceFilter t = new ShadowTraceFilter();
            t.setEveryFrames(Math.max(1, cfg.debug().getTraceEveryFrames()));
            p.add(t);
        }
    }
}