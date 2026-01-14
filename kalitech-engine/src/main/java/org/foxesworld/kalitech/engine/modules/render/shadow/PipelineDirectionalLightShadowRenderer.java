// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/PipelineDirectionalLightShadowRenderer.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.ShadowUtil;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipeline;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

import java.util.Arrays;

/**
 * Orchestrator renderer: delegates all shadow behavior to {@link ShadowPipeline}.
 * Filters implement stability, snapping, PCSS/PCF settings, cascade policies, etc.
 */
public final class PipelineDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer {

    private final ShadowPipeline pipeline = new ShadowPipeline();

    private ShadowFrameContext frameCtx;
    private long frameId = 0L;

    /**
     * Fixed split distances in view space units: [near, s1, s2, ..., far].
     * When set, PSSM lambda split computation is bypassed.
     */
    private float[] fixedSplitDistances;

    public PipelineDirectionalLightShadowRenderer(AssetManager assetManager, int shadowMapSize, int nbSplits) {
        super(assetManager, shadowMapSize, nbSplits);
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public ShadowPipeline pipeline() {
        return pipeline;
    }

    /**
     * Enables fixed splits (stable cascades).
     * Array must contain (numSplits + 1) values: [near, s1.., far] in ascending order.
     */
    public void setFixedSplitDistances(float... distances) {
        if (distances == null || distances.length == 0) {
            fixedSplitDistances = null;
            return;
        }
        float[] copy = distances.clone();
        Arrays.sort(copy);
        fixedSplitDistances = copy;
    }

    public void clearFixedSplitDistances() {
        fixedSplitDistances = null;
    }

    private boolean hasFixedSplits() {
        return fixedSplitDistances != null && fixedSplitDistances.length == (getNumShadowMaps() + 1);
    }

    @Override
    protected void updateShadowCams(Camera viewCam) {
        super.updateShadowCams(viewCam);

        DirectionalLight dl = getLight();
        if (dl == null || viewPort == null || viewCam == null) {
            frameCtx = null;
            return;
        }

        if (hasFixedSplits()) {
            applyFixedSplits(viewCam);
        }

        frameCtx = new ShadowFrameContext(
                viewPort,
                viewCam,
                dl,
                getShadowMapSize(),
                getNumShadowMaps(),
                splitsArray,
                frameId++
        );

        pipeline.beginFrame(frameCtx);
    }

    private void applyFixedSplits(Camera viewCam) {
        float frustumNear = Math.max(viewCam.getFrustumNear(), 0.001f);

        float zFar = zFarOverride;
        if (zFar == 0f) {
            zFar = viewCam.getFrustumFar();
        }

        float[] src = fixedSplitDistances;

        splitsArray[0] = frustumNear;
        for (int i = 1; i < splitsArray.length - 1; i++) {
            splitsArray[i] = clamp(src[i], frustumNear, zFar);
        }
        splitsArray[splitsArray.length - 1] = zFar;

        if (viewCam.isParallelProjection()) {
            float denom = (zFar - frustumNear);
            if (denom > 0f) {
                for (int i = 0; i < getNumShadowMaps(); i++) {
                    splitsArray[i] = splitsArray[i] / denom;
                }
            }
        }

        updateSplitsColorFromArray();
    }

    private void updateSplitsColorFromArray() {
        ColorRGBA s = this.splits;
        float[] a = this.splitsArray;

        if (a.length >= 2) s.r = a[1];
        if (a.length >= 3) s.g = a[2];
        if (a.length >= 4) s.b = a[3];
        if (a.length >= 5) s.a = a[4];
    }

    @Override
    protected GeometryList getOccludersToRender(int shadowMapIndex, GeometryList shadowMapOccluders) {
        if (frameCtx == null) {
            return super.getOccludersToRender(shadowMapIndex, shadowMapOccluders);
        }

        ShadowUtil.updateFrustumPoints(
                frameCtx.viewCam,
                splitsArray[shadowMapIndex],
                splitsArray[shadowMapIndex + 1],
                1.0f,
                points
        );

        Camera sc = getShadowCam(shadowMapIndex);
        if (sc == null) return shadowMapOccluders;

        getReceivers(lightReceivers);

        ShadowSplitContext splitCtx = new ShadowSplitContext(
                frameCtx,
                shadowMapIndex,
                splitsArray[shadowMapIndex],
                splitsArray[shadowMapIndex + 1],
                sc,
                points,
                lightReceivers,
                shadowMapOccluders
        );

        pipeline.beginSplit(splitCtx);

        boolean handledCam = pipeline.updateShadowCam(splitCtx);
        if (shadowMapIndex == 0 && (frameCtx.frameId % 60) == 0) {
            System.out.println("[shadow][trace] handledCam=" + handledCam);
        }

        if (!handledCam) {
            ShadowUtil.updateShadowCamera(
                    frameCtx.viewPort,
                    splitCtx.receivers,
                    splitCtx.shadowCam,
                    splitCtx.frustumPoints,
                    splitCtx.occluders,
                    splitCtx.stabilizationTexelSize
            );
        }

        pipeline.afterShadowCam(splitCtx);
        pipeline.beforeGatherOccluders(splitCtx);

        if (splitCtx.occluders.size() == 0) {
            for (Spatial scene : frameCtx.viewPort.getScenes()) {
                ShadowUtil.getGeometriesInCamFrustum(scene, splitCtx.shadowCam, RenderQueue.ShadowMode.Cast, splitCtx.occluders);
            }
        }

        pipeline.afterGatherOccluders(splitCtx);
        pipeline.endSplit(splitCtx);

        return shadowMapOccluders;
    }

    @Override
    public void postQueue(RenderQueue rq) {
        try {
            super.postQueue(rq);
        } finally {
            if (frameCtx != null) {
                pipeline.endFrame(frameCtx);
                frameCtx = null;
            }
        }
    }

    @Override
    protected void setMaterialParameters(Material material) {
        super.setMaterialParameters(material);
        if (frameCtx != null) pipeline.setMaterialParameters(frameCtx, material);
    }

    @Override
    protected void clearMaterialParameters(Material material) {
        if (frameCtx != null) pipeline.clearMaterialParameters(frameCtx, material);
        super.clearMaterialParameters(material);
    }

    @Override
    protected boolean checkCulling(Camera viewCam) {
        return true;
    }
}