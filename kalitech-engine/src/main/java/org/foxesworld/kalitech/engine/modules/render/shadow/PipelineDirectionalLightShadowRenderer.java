// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/PipelineDirectionalLightShadowRenderer.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.ShadowUtil;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipeline;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Directional shadow renderer with fully externalized pipeline.
 * <p>
 * Hot-reload safe when lifecycle is handled by detach -> destroy -> new -> attach.
 * Includes a fail-safe to avoid crashing the whole engine on rare FBO invalidation.
 */
public final class PipelineDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer {

    private final ShadowPipeline pipeline = new ShadowPipeline();

    private ShadowFrameContext frameCtx;
    private long frameId = 0L;

    private final AtomicBoolean fboErrorLogged = new AtomicBoolean(false);
    private volatile boolean disposed = false;
    private volatile boolean broken = false;

    private float[] fixedSplitDistances;

    public PipelineDirectionalLightShadowRenderer(AssetManager assets, int mapSize, int splits) {
        super(assets, mapSize, splits);
    }

    public ShadowPipeline pipeline() {
        return pipeline;
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public boolean isBroken() {
        return broken;
    }

    // ------------------------------------------------------------

    /**
     * Must be called ONLY after this processor was removed from ViewPort.
     * Must be executed on render thread.
     */
    public void destroy(RenderManager rm) {
        if (disposed) return;
        disposed = true;

        frameCtx = null;
        fixedSplitDistances = null;
        pipeline.clear();

        try {
            cleanup();
        } catch (RuntimeException ignored) {
        }
    }

    public float getZFarOverride() {
        return zFarOverride;
    }

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

    public void setZFarOverride(float zFarOverride) {
        this.zFarOverride = Math.max(0f, zFarOverride);
    }

    private boolean hasFixedSplits() {
        return fixedSplitDistances != null
                && fixedSplitDistances.length == (getNumShadowMaps() + 1);
    }

    // ------------------------------------------------------------

    @Override
    protected void updateShadowCams(Camera viewCam) {
        if (disposed || broken) {
            frameCtx = null;
            return;
        }

        super.updateShadowCams(viewCam);

        if (getLight() == null || viewPort == null || viewCam == null) {
            frameCtx = null;
            return;
        }

        if (hasFixedSplits()) {
            applyFixedSplits(viewCam);
        }

        frameCtx = new ShadowFrameContext(
                viewPort,
                viewCam,
                getLight(),
                getShadowMapSize(),
                getNumShadowMaps(),
                splitsArray,
                frameId++
        );

        pipeline.beginFrame(frameCtx);
    }

    private void applyFixedSplits(Camera viewCam) {
        float near = Math.max(viewCam.getFrustumNear(), 0.001f);
        float far = zFarOverride > 0f ? zFarOverride : viewCam.getFrustumFar();

        splitsArray[0] = near;
        for (int i = 1; i < splitsArray.length - 1; i++) {
            splitsArray[i] = clamp(fixedSplitDistances[i], near, far);
        }
        splitsArray[splitsArray.length - 1] = far;

        updateSplitsColorFromArray();
    }

    private void updateSplitsColorFromArray() {
        ColorRGBA s = splits;
        if (splitsArray.length > 1) s.r = splitsArray[1];
        if (splitsArray.length > 2) s.g = splitsArray[2];
        if (splitsArray.length > 3) s.b = splitsArray[3];
        if (splitsArray.length > 4) s.a = splitsArray[4];
    }

    // ------------------------------------------------------------

    @Override
    protected GeometryList getOccludersToRender(int index, GeometryList occluders) {
        if (disposed || broken || frameCtx == null) {
            return occluders;
        }

        ShadowUtil.updateFrustumPoints(
                frameCtx.viewCam,
                splitsArray[index],
                splitsArray[index + 1],
                1.0f,
                points
        );

        Camera sc = getShadowCam(index);
        if (sc == null) return occluders;

        getReceivers(lightReceivers);

        ShadowSplitContext splitCtx = new ShadowSplitContext(
                frameCtx,
                index,
                splitsArray[index],
                splitsArray[index + 1],
                sc,
                points,
                lightReceivers,
                occluders
        );

        pipeline.beginSplit(splitCtx);

        boolean handled = pipeline.updateShadowCam(splitCtx);
        splitCtx.shadowCamHandled = handled;

        if (!handled) {
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

        if (splitCtx.occluders.size() <= 0) {
            for (Spatial scene : frameCtx.viewPort.getScenes()) {
                ShadowUtil.getGeometriesInCamFrustum(
                        scene,
                        splitCtx.shadowCam,
                        RenderQueue.ShadowMode.Cast,
                        splitCtx.occluders
                );
            }
        }

        pipeline.afterGatherOccluders(splitCtx);
        pipeline.endSplit(splitCtx);

        return occluders;
    }

    @Override
    public void postQueue(RenderQueue rq) {
        if (disposed || broken) {
            frameCtx = null;
            return;
        }

        try {
            super.postQueue(rq);
        } catch (IllegalStateException fbo) {
            // Fail-safe: do not crash the engine during hot reload / transient GL state.
            broken = true;
            frameCtx = null;

            if (fboErrorLogged.compareAndSet(false, true)) {
                System.err.println("[shadow] FBO error in postQueue (hot reload?). Disabling this renderer instance.");
                System.err.println("[shadow] " + fbo.getClass().getName() + ": " + fbo.getMessage());
            }
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