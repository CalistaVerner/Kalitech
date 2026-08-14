/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.asset.AssetManager
 *  com.jme3.material.Material
 *  com.jme3.math.ColorRGBA
 *  com.jme3.math.Vector3f
 *  com.jme3.renderer.Camera
 *  com.jme3.renderer.RenderManager
 *  com.jme3.renderer.ViewPort
 *  com.jme3.renderer.queue.GeometryList
 *  com.jme3.renderer.queue.RenderQueue
 *  com.jme3.renderer.queue.RenderQueue$ShadowMode
 *  com.jme3.scene.Spatial
 *  com.jme3.shadow.DirectionalLightShadowRenderer
 *  com.jme3.shadow.ShadowUtil
 */
package org.foxesworld.kalitech.engine.modules.render.shadow;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.ShadowUtil;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipeline;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class PipelineDirectionalLightShadowRenderer
extends DirectionalLightShadowRenderer {
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
        return this.pipeline;
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    public void destroy(RenderManager rm) {
        if (this.disposed) {
            return;
        }
        this.disposed = true;
        this.frameCtx = null;
        this.fixedSplitDistances = null;
        this.pipeline.clear();
        try {
            this.cleanup();
        }
        catch (RuntimeException runtimeException) {
            // empty catch block
        }
    }

    public void setFixedSplitDistances(float ... distances) {
        if (distances == null || distances.length == 0) {
            this.fixedSplitDistances = null;
            return;
        }
        float[] copy = (float[])distances.clone();
        Arrays.sort(copy);
        this.fixedSplitDistances = copy;
    }

    public void clearFixedSplitDistances() {
        this.fixedSplitDistances = null;
    }

    private boolean hasFixedSplits() {
        return this.fixedSplitDistances != null && this.fixedSplitDistances.length == this.getNumShadowMaps() + 1;
    }

    protected void updateShadowCams(Camera viewCam) {
        if (this.disposed || this.broken) {
            this.frameCtx = null;
            return;
        }
        super.updateShadowCams(viewCam);
        if (this.getLight() == null || this.viewPort == null || viewCam == null) {
            this.frameCtx = null;
            return;
        }
        if (this.hasFixedSplits()) {
            this.applyFixedSplits(viewCam);
        }
        this.frameCtx = new ShadowFrameContext(this.viewPort, viewCam, this.getLight(), this.getShadowMapSize(), this.getNumShadowMaps(), this.splitsArray, this.frameId++);
        this.pipeline.beginFrame(this.frameCtx);
    }

    private void applyFixedSplits(Camera viewCam) {
        float near = Math.max(viewCam.getFrustumNear(), 0.001f);
        float far = this.zFarOverride > 0.0f ? this.zFarOverride : viewCam.getFrustumFar();
        this.splitsArray[0] = near;
        for (int i = 1; i < this.splitsArray.length - 1; ++i) {
            this.splitsArray[i] = PipelineDirectionalLightShadowRenderer.clamp(this.fixedSplitDistances[i], near, far);
        }
        this.splitsArray[this.splitsArray.length - 1] = far;
        ColorRGBA s = this.splits;
        if (this.splitsArray.length > 1) {
            s.r = this.splitsArray[1];
        }
        if (this.splitsArray.length > 2) {
            s.g = this.splitsArray[2];
        }
        if (this.splitsArray.length > 3) {
            s.b = this.splitsArray[3];
        }
        if (this.splitsArray.length > 4) {
            s.a = this.splitsArray[4];
        }
    }

    protected GeometryList getOccludersToRender(int index, GeometryList occluders) {
        boolean handled;
        if (this.disposed || this.broken || this.frameCtx == null) {
            return occluders;
        }
        ShadowUtil.updateFrustumPoints((Camera)this.frameCtx.viewCam, (float)this.splitsArray[index], (float)this.splitsArray[index + 1], (float)1.0f, (Vector3f[])this.points);
        Camera sc = this.getShadowCam(index);
        if (sc == null) {
            return occluders;
        }
        this.getReceivers(this.lightReceivers);
        ShadowSplitContext splitCtx = new ShadowSplitContext(this.frameCtx, index, this.splitsArray[index], this.splitsArray[index + 1], sc, this.points, this.lightReceivers, occluders);
        this.pipeline.beginSplit(splitCtx);
        splitCtx.handledCam = handled = this.pipeline.updateShadowCam(splitCtx);
        if (!handled) {
            ShadowUtil.updateShadowCamera((ViewPort)this.frameCtx.viewPort, (GeometryList)splitCtx.receivers, (Camera)splitCtx.shadowCam, (Vector3f[])splitCtx.frustumPoints, (GeometryList)splitCtx.occluders, (float)splitCtx.stabilizationTexelSize);
        }
        this.pipeline.afterShadowCam(splitCtx);
        this.pipeline.beforeGatherOccluders(splitCtx);
        if (splitCtx.occluders.size() <= 0) {
            for (Spatial scene : this.frameCtx.viewPort.getScenes()) {
                ShadowUtil.getGeometriesInCamFrustum((Spatial)scene, (Camera)splitCtx.shadowCam, (RenderQueue.ShadowMode)RenderQueue.ShadowMode.Cast, (GeometryList)splitCtx.occluders);
            }
        }
        this.pipeline.afterGatherOccluders(splitCtx);
        this.pipeline.endSplit(splitCtx);
        return occluders;
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public boolean isBroken() {
        return this.broken;
    }

    public void postQueue(RenderQueue rq) {
        if (this.disposed || this.broken) {
            this.frameCtx = null;
            return;
        }
        try {
            super.postQueue(rq);
        }
        catch (IllegalStateException fbo) {
            this.broken = true;
            this.frameCtx = null;
            if (this.fboErrorLogged.compareAndSet(false, true)) {
                System.err.println("[shadow] FBO error in postQueue (hot reload?). Disabling this renderer instance.");
                System.err.println("[shadow] " + fbo.getClass().getName() + ": " + fbo.getMessage());
            }
        }
        finally {
            if (this.frameCtx != null) {
                this.pipeline.endFrame(this.frameCtx);
                this.frameCtx = null;
            }
        }
    }

    protected void setMaterialParameters(Material material) {
        super.setMaterialParameters(material);
        if (this.frameCtx != null) {
            this.pipeline.setMaterialParameters(this.frameCtx, material);
        }
    }
}

