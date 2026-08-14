/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import java.util.Objects;
import org.foxesworld.kalitech.engine.modules.render.shadow.PipelineDirectionalLightShadowRenderer;
import org.foxesworld.kalitech.engine.modules.render.shadow.config.ShadowRenderConfig;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.CascadeHysteresisFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.ShadowSnapperFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.ShadowTraceFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.StableFitShadowCamFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.StableLightBasisFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.TemporalSnapGateFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.TightStableFitShadowCamFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipeline;

public final class ShadowRendererConfigurator {
    private ShadowRendererConfigurator() {
    }

    public static void apply(PipelineDirectionalLightShadowRenderer r, ShadowRenderConfig cfg) {
        Objects.requireNonNull(r, "renderer");
        Objects.requireNonNull(cfg, "cfg");
        float[] fixed = ShadowRenderConfig.sortedCloneOrNull(cfg.cascades().getFixedSplits());
        if (fixed != null) {
            r.setFixedSplitDistances(fixed);
        } else {
            r.clearFixedSplitDistances();
        }
        ShadowPipeline p = r.pipeline();
        p.clear();
        if (cfg.cascades().isHysteresisEnabled()) {
            CascadeHysteresisFilter h = new CascadeHysteresisFilter();
            h.setHysteresis(cfg.cascades().getSplitHysteresis());
            h.setSmoothing(cfg.cascades().getSplitSmoothing());
            p.add(h);
        }
        p.add(new StableLightBasisFilter());
        switch (cfg.fitting().getMode()) {
            case STABLE_AABB: {
                ShadowFilter f = new StableFitShadowCamFilter();
                ((StableFitShadowCamFilter)f).setMinNear(cfg.fitting().getMinNear());
                ((StableFitShadowCamFilter)f).setCasterBackBase(cfg.fitting().getCasterBackBase());
                ((StableFitShadowCamFilter)f).setCasterBackCascadeMul(cfg.fitting().getCasterBackCascadeMul());
                ((StableFitShadowCamFilter)f).setReceiverFrontBase(cfg.fitting().getReceiverFrontBase());
                ((StableFitShadowCamFilter)f).setExtentsPadding(cfg.fitting().getXyPadding());
                ((StableFitShadowCamFilter)f).setForceSquare(cfg.fitting().isForceSquare());
                ((StableFitShadowCamFilter)f).setSizeQuantizeTexels(cfg.fitting().getSizeQuantizeTexels());
                p.add(f);
                break;
            }
            default: {
                ShadowFilter f = new TightStableFitShadowCamFilter();
                ((TightStableFitShadowCamFilter)f).setMinNear(cfg.fitting().getMinNear());
                ((TightStableFitShadowCamFilter)f).setCasterBackBase(cfg.fitting().getCasterBackBase());
                ((TightStableFitShadowCamFilter)f).setCasterBackCascadeMul(cfg.fitting().getCasterBackCascadeMul());
                ((TightStableFitShadowCamFilter)f).setReceiverFrontBase(cfg.fitting().getReceiverFrontBase());
                ((TightStableFitShadowCamFilter)f).setForceSquare(cfg.fitting().isForceSquare());
                ((TightStableFitShadowCamFilter)f).setSizeQuantizeTexels(cfg.fitting().getSizeQuantizeTexels());
                ((TightStableFitShadowCamFilter)f).setLockNearCascadeSize(cfg.fitting().isLockNearCascadeSize());
                ((TightStableFitShadowCamFilter)f).setNearTierTexels(cfg.fitting().getNearTierTexels());
                ((TightStableFitShadowCamFilter)f).setNearShrinkHysteresisTiers(cfg.fitting().getNearShrinkHysteresisTiers());
                p.add(f);
                break;
            }
        }
        boolean snappingEnabled = cfg.snapping().isEnabled();
        ShadowRenderConfig.TemporalGate tg = cfg.snapping().temporalGate();
        if (snappingEnabled && tg.isEnabled()) {
            TemporalSnapGateFilter gate = new TemporalSnapGateFilter();
            gate.setEnabled(true);
            gate.setMinMoveTexels(Math.max(0.0f, tg.getMinMoveTexels()));
            gate.setMinRotateDeg(Math.max(0.0f, tg.getMinRotateDeg()));
            gate.setGatedFirstCascades(Math.max(0, tg.getGatedFirstCascades()));
            p.add(gate);
        }
        ShadowSnapperFilter snap = new ShadowSnapperFilter();
        snap.setEnabled(snappingEnabled);
        snap.setSnapFirstCascades(Math.max(0, cfg.snapping().getSnapFirstCascades()));
        snap.setHoldEnabled(true);
        float holdTexels = snappingEnabled && tg.isEnabled() ? Math.max(1.0f, tg.getMinMoveTexels()) : 1.25f;
        snap.setHoldThresholdTexels(holdTexels);
        p.add(snap);
        if (cfg.debug().isTraceEnabled()) {
            ShadowTraceFilter t = new ShadowTraceFilter();
            t.setEveryFrames(Math.max(1, cfg.debug().getTraceEveryFrames()));
            p.add(t);
        }
    }
}

