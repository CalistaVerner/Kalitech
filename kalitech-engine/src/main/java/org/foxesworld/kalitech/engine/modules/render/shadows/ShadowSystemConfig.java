// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/ShadowSystemConfig.java
// Author: Calista Verner (K\u039bYL\u039b)
package org.foxesworld.kalitech.engine.modules.render.shadows;

import org.foxesworld.kalitech.engine.modules.render.shadows.filters.*;

import java.util.Objects;

/**
 * One source of truth for the shadow system.
 * <p>
 * Design rules:
 * - No shadow feature is toggled by ad-hoc flags or side effects.
 * - All behavior is expressed as a deterministic pipeline composition.
 * - To change behavior at runtime, apply a new config and rebuild the pipeline.
 */
public final class ShadowSystemConfig {

    public final SplitComputeFilter.Cfg splitCfg = new SplitComputeFilter.Cfg();
    public final EnhancedSplitHysteresisFilter.Cfg hysteresisCfg = new EnhancedSplitHysteresisFilter.Cfg();
    public final CascadeRangeFilter.Cfg rangeCfg = new CascadeRangeFilter.Cfg();
    public final StableLightBasis.Config basisCfg = new StableLightBasis.Config();
    public final StableCascadeFitterFilter.Cfg fitterCfg = new StableCascadeFitterFilter.Cfg();
    public final ShadowCamPlacementFilter.Cfg placementCfg = new ShadowCamPlacementFilter.Cfg();
    public final MaterialDefaultsFilter.Cfg materialCfg = new MaterialDefaultsFilter.Cfg();
    public final ShadowSnapper.Config snapCfg = new ShadowSnapper.Config();
    public RendererType rendererType = RendererType.PCSS;
    public int mapSize = 2048;
    public int splits = 4;
    public float intensity = 0.75f;
    public boolean debugEnabled = false;
    public int debugEveryFrames = 60;
    /**
     * Pipeline feature toggles (implemented strictly by filter presence).
     */
    public boolean enableSnap = true;
    public boolean enableSplitHysteresis = true;

    public ShadowSystemConfig copy() {
        ShadowSystemConfig c = new ShadowSystemConfig();
        c.rendererType = this.rendererType;
        c.mapSize = this.mapSize;
        c.splits = this.splits;
        c.intensity = this.intensity;
        c.debugEnabled = this.debugEnabled;
        c.debugEveryFrames = this.debugEveryFrames;
        c.enableSnap = this.enableSnap;
        c.enableSplitHysteresis = this.enableSplitHysteresis;

        c.splitCfg.lambda = this.splitCfg.lambda;
        c.splitCfg.fixedSplitDistances = (this.splitCfg.fixedSplitDistances == null)
                ? null
                : this.splitCfg.fixedSplitDistances.clone();

        c.hysteresisCfg.fastMotionDamping = this.hysteresisCfg.fastMotionDamping;
        c.hysteresisCfg.fastMotionSpeed = this.hysteresisCfg.fastMotionSpeed;
        c.hysteresisCfg.minHalfLifeSeconds = this.hysteresisCfg.minHalfLifeSeconds;
        c.hysteresisCfg.maxHalfLifeSeconds = this.hysteresisCfg.maxHalfLifeSeconds;

        c.rangeCfg.minNear = this.rangeCfg.minNear;
        c.rangeCfg.minGap = this.rangeCfg.minGap;

        c.basisCfg.customUp.set(this.basisCfg.customUp);
        c.basisCfg.flipThreshold = this.basisCfg.flipThreshold;
        c.basisCfg.enableRollSnap = this.basisCfg.enableRollSnap;
        c.basisCfg.rollSnapRate = this.basisCfg.rollSnapRate;
        c.basisCfg.smoothingFactor = this.basisCfg.smoothingFactor;
        c.basisCfg.useWorldUp = this.basisCfg.useWorldUp;

        c.fitterCfg.extentsPadding = this.fitterCfg.extentsPadding;
        c.fitterCfg.zPadding = this.fitterCfg.zPadding;
        c.fitterCfg.minZSpan = this.fitterCfg.minZSpan;
        c.fitterCfg.quantTexels = this.fitterCfg.quantTexels;

        c.placementCfg.backOffset = this.placementCfg.backOffset;
        c.placementCfg.minRadius = this.placementCfg.minRadius;
        c.placementCfg.minNear = this.placementCfg.minNear;
        c.placementCfg.minFarGap = this.placementCfg.minFarGap;

        c.materialCfg.shadowBias = this.materialCfg.shadowBias;
        c.materialCfg.shadowSlopeBias = this.materialCfg.shadowSlopeBias;
        c.materialCfg.shadowNormalOffset = this.materialCfg.shadowNormalOffset;
        c.materialCfg.cascadeBlendEnabled = this.materialCfg.cascadeBlendEnabled;
        c.materialCfg.cascadeBlendLen = this.materialCfg.cascadeBlendLen;

        c.snapCfg.enablePositionSnap = this.snapCfg.enablePositionSnap;
        c.snapCfg.positionThreshold = this.snapCfg.positionThreshold;
        c.snapCfg.maxSnapDistanceTexels = this.snapCfg.maxSnapDistanceTexels;
        c.snapCfg.adaptiveSnapping = this.snapCfg.adaptiveSnapping;
        c.snapCfg.conservative = this.snapCfg.conservative;

        return c;
    }

    public void buildPipeline(StableDirectionalLightShadowRenderer r) {
        Objects.requireNonNull(r, "r");

        SplitComputeFilter splitCompute = new SplitComputeFilter(splitCfg);
        EnhancedSplitHysteresisFilter hysteresis =
                new EnhancedSplitHysteresisFilter(new SplitHysteresisManager(), hysteresisCfg);
        CascadeRangeFilter cascadeRange = new CascadeRangeFilter(rangeCfg);

        StableBasisFilter stableBasis = new StableBasisFilter(basisCfg);
        FrustumSphereFitFilter frustumFit = new FrustumSphereFitFilter();
        StableCascadeFitterFilter stableFitter = new StableCascadeFitterFilter(fitterCfg);
        ShadowCamPlacementFilter placement = new ShadowCamPlacementFilter(placementCfg);

        MaterialDefaultsFilter materialDefaults = new MaterialDefaultsFilter(materialCfg);

        r.pipeline().clear();

        r.pipeline()
                .add(materialDefaults)
                .add(splitCompute);

        if (enableSplitHysteresis) {
            r.pipeline().add(hysteresis);
        }

        r.pipeline()
                .add(cascadeRange)
                .add(stableBasis)
                .add(frustumFit)
                .add(stableFitter)
                .add(placement);

        if (enableSnap) {
            ShadowSnapper snapper = new ShadowSnapper(mapSize, snapCfg, Math.max(1, splits));
            r.pipeline().add(new ShadowSnapperFilter(snapper));
        }
    }

    public enum RendererType {
        STABLE,
        PCSS
    }
}