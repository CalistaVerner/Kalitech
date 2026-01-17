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
import org.foxesworld.kalitech.engine.modules.render.shadow.gpu.ShadowGpuLayout;
import org.foxesworld.kalitech.engine.modules.render.shadow.gpu.ShadowGpuParams;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipeline;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL44;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Directional shadow renderer with fully externalized pipeline and mandatory GPU UBO upload.
 * <p>
 * Owns a persistent-mapped UBO and updates it once per frame from the shadow pipeline.
 * The pipeline must provide {@link ShadowGpuParams} via {@link ShadowKeys#GPU_PARAMS}.
 */
public final class PipelineDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer {

    /**
     * GLSL binding index:
     * <pre>
     * layout(std140, binding = 3) uniform KT_ShadowUBO { ... };
     * </pre>
     */
    public static final int SHADOW_UBO_BINDING = 3;

    private final ShadowPipeline pipeline = new ShadowPipeline();

    private ShadowFrameContext frameCtx;
    private long frameId = 0L;

    private final AtomicBoolean fboErrorLogged = new AtomicBoolean(false);
    private volatile boolean disposed = false;
    private volatile boolean broken = false;

    private float[] fixedSplitDistances;

    // -------- GPU (UBO) --------
    private int shadowUboId = 0;
    private ByteBuffer shadowUboMapped = null;
    private long lastUploadedFrameId = Long.MIN_VALUE;
    private boolean gpuInitialized = false;

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

    public void destroy(RenderManager rm) {
        if (disposed) return;
        disposed = true;

        frameCtx = null;
        fixedSplitDistances = null;
        pipeline.clear();

        // Must run on render thread with valid GL context.
        destroyGpu();

        try {
            cleanup();
        } catch (RuntimeException e) {
            System.err.println("[shadow] cleanup failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
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

    private boolean hasFixedSplits() {
        return fixedSplitDistances != null
                && fixedSplitDistances.length == (getNumShadowMaps() + 1);
    }

    @Override
    protected void updateShadowCams(Camera viewCam) {
        if (disposed || broken) {
            frameCtx = null;
            return;
        }

        // GPU path is mandatory. Ensure UBO exists before doing any work.
        ensureGpuReady();

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

        ColorRGBA s = splits;
        if (splitsArray.length > 1) s.r = splitsArray[1];
        if (splitsArray.length > 2) s.g = splitsArray[2];
        if (splitsArray.length > 3) s.b = splitsArray[3];
        if (splitsArray.length > 4) s.a = splitsArray[4];
    }

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
        splitCtx.handledCam = handled;

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

    public boolean isDisposed() {
        return disposed;
    }

    public boolean isBroken() {
        return broken;
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
            broken = true;
            frameCtx = null;

            destroyGpu();

            if (fboErrorLogged.compareAndSet(false, true)) {
                System.err.println("[shadow] FBO error in postQueue (hot reload?). Disabling this renderer instance.");
                System.err.println("[shadow] " + fbo.getClass().getName() + ": " + fbo.getMessage());
            }
        } finally {
            if (frameCtx != null) {
                pipeline.endFrame(frameCtx);

                // Mandatory GPU upload: once per frame, after pipeline produced ShadowGpuParams.
                try {
                    uploadShadowUbo(frameCtx);
                } catch (RuntimeException e) {
                    broken = true;
                    System.err.println("[shadow] GPU upload failed. Disabling this renderer instance.");
                    System.err.println("[shadow] " + e.getClass().getName() + ": " + e.getMessage());
                    destroyGpu();
                }

                frameCtx = null;
            }
        }
    }

    @Override
    protected void setMaterialParameters(Material material) {
        // Keep jME base params. Real GPU path uses UBO binding only.
        super.setMaterialParameters(material);
    }

    // ---------------- GPU: UBO management ----------------

    private void ensureGpuReady() {
        if (gpuInitialized) return;

        // Must be on render thread with a valid OpenGL context.
        GL.createCapabilities();
        GLCapabilities caps = GL.getCapabilities();

        if (!isPersistentUboSupported(caps)) {
            broken = true;
            throw new IllegalStateException("Persistent mapped UBO is not supported (requires OpenGL 4.4 or ARB_buffer_storage)");
        }

        createOrRecreateUbo(caps);
        bindUboBase();

        gpuInitialized = true;
    }

    private static boolean isPersistentUboSupported(GLCapabilities caps) {
        return caps.OpenGL44 || caps.GL_ARB_buffer_storage;
    }

    private void createOrRecreateUbo(GLCapabilities caps) {
        destroyGpu();

        shadowUboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, shadowUboId);

        // Buffer storage + persistent mapping flags (storage + map access).
        // Using coherent mapping to avoid explicit flush.
        int flags = GL44.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;

        // glBufferStorage is either core (GL44) or ARB extension.
        if (caps.OpenGL44) {
            GL44.glBufferStorage(GL31.GL_UNIFORM_BUFFER, ShadowGpuLayout.TOTAL_BYTES, flags);
        } else if (caps.GL_ARB_buffer_storage) {
            ARBBufferStorage.glBufferStorage(GL31.GL_UNIFORM_BUFFER, ShadowGpuLayout.TOTAL_BYTES, flags);
        } else {
            // Should be impossible due to earlier check.
            throw new IllegalStateException("No glBufferStorage support");
        }

        shadowUboMapped = GL30.glMapBufferRange(
                GL31.GL_UNIFORM_BUFFER,
                0,
                ShadowGpuLayout.TOTAL_BYTES,
                flags,
                null
        );

        if (shadowUboMapped == null) {
            throw new IllegalStateException("Failed to map shadow UBO persistently (shadowUboMapped == null)");
        }

        shadowUboMapped.order(ByteOrder.nativeOrder());

        // Unbind
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);

        lastUploadedFrameId = Long.MIN_VALUE;
    }

    private void bindUboBase() {
        if (shadowUboId == 0) return;
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, SHADOW_UBO_BINDING, shadowUboId);
    }

    private void uploadShadowUbo(ShadowFrameContext ctx) {
        if (broken || disposed) return;
        if (shadowUboId == 0 || shadowUboMapped == null) {
            throw new IllegalStateException("Shadow UBO not initialized");
        }

        ShadowGpuParams params = ctx.ws.get(ShadowKeys.GPU_PARAMS);
        if (params == null) {
            throw new IllegalStateException("ShadowGpuParams missing. ShadowGpuParamsPackFilter must be present and mandatory.");
        }

        long fid = params.frameId();
        if (fid == lastUploadedFrameId) {
            return;
        }

        // Pack directly into persistent mapped buffer (no staging, no allocations).
        shadowUboMapped.clear();
        params.packStd140(shadowUboMapped);

        // Coherent mapping: no explicit flush.
        // Rebind base is cheap and ensures binding survives hot-reload state churn.
        bindUboBase();

        lastUploadedFrameId = fid;
    }

    private void destroyGpu() {
        gpuInitialized = false;
        lastUploadedFrameId = Long.MIN_VALUE;

        if (shadowUboId != 0) {
            // Best-effort unmap + delete (must be on render thread with GL context).
            try {
                GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, shadowUboId);
                try {
                    GL15.glUnmapBuffer(GL31.GL_UNIFORM_BUFFER);
                } catch (Throwable t) {
                    System.err.println("[shadow] UBO unmap failed: " + t.getClass().getName() + ": " + t.getMessage());
                }
                GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
            } catch (Throwable t) {
                System.err.println("[shadow] UBO unbind/unmap failed: " + t.getClass().getName() + ": " + t.getMessage());
            }

            try {
                GL15.glDeleteBuffers(shadowUboId);
            } catch (Throwable t) {
                System.err.println("[shadow] UBO delete failed: " + t.getClass().getName() + ": " + t.getMessage());
            }
        }

        shadowUboId = 0;
        shadowUboMapped = null;
    }
}