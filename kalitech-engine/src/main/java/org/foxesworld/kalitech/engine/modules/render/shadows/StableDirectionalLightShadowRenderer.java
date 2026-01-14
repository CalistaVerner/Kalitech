// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/StableDirectionalLightShadowRenderer.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.texture.FrameBuffer;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowPipeline;

import java.lang.reflect.Field;

public class StableDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer implements ShadowTunable {

    private Logger log;
    private boolean dbgEnabled = false;
    private int dbgEveryFrames = 60;
    private long dbgFrame = 0;

    private final ShadowPipeline pipeline = new ShadowPipeline();
    private final ShadowFrameContext frameCtx = new ShadowFrameContext();

    private final Vector3f lightDir = new Vector3f();
    private volatile Camera[] shadowCamsCached = null;

    private long lastNs = 0L;

    // "Last material state" snapshot for setMaterialParameters()
    private final ShadowFrameContext.MaterialState materialState = new ShadowFrameContext.MaterialState();

    public StableDirectionalLightShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
        super(assets, shadowMapSize, nbSplits);
    }

    private static void normalizeSafe(Vector3f v) {
        float len2 = v.x * v.x + v.y * v.y + v.z * v.z;
        if (len2 <= 1e-20f) return;
        float inv = FastMath.invSqrt(len2);
        v.x *= inv;
        v.y *= inv;
        v.z *= inv;
    }

    private static String f3(float v) {
        return String.format("%.3f", v);
    }

    private static Camera[] tryGetShadowCamsByReflection(Object self) {
        try {
            Class<?> c = self.getClass();
            while (c != null && c != Object.class) {
                for (String n : new String[]{"shadowCam", "shadowCams", "shadowCameras"}) {
                    try {
                        Field f = c.getDeclaredField(n);
                        f.setAccessible(true);
                        Object v = f.get(self);
                        if (v instanceof Camera[]) return (Camera[]) v;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static FrameBuffer[] tryGetShadowFbsByReflection(Object self) {
        try {
            Class<?> c = self.getClass();
            while (c != null && c != Object.class) {
                for (String n : new String[]{
                        "shadowFB", "shadowFbs", "shadowFBOs", "shadowFbo", "shadowFramebuffers"
                }) {
                    try {
                        Field f = c.getDeclaredField(n);
                        f.setAccessible(true);
                        Object v = f.get(self);
                        if (v instanceof FrameBuffer[]) return (FrameBuffer[]) v;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public void setDebug(Logger log, boolean enabled, int everyFrames) {
        this.log = log;
        this.dbgEnabled = enabled;
        this.dbgEveryFrames = Math.max(1, everyFrames);
    }

    @Override
    public ShadowPipeline pipeline() {
        return pipeline;
    }

    @Override
    protected void setMaterialParameters(Material material) {
        super.setMaterialParameters(material);
        materialState.applyTo(material);
    }

    @Override
    protected void clearMaterialParameters(Material material) {
        super.clearMaterialParameters(material);
        materialState.clearFrom(material);
    }

    @Override
    protected void updateShadowCams(Camera viewCam) {
        super.updateShadowCams(viewCam);

        DirectionalLight dl = getLight();
        if (dl == null || viewCam == null) return;

        float dt = computeDtClamped();

        Camera[] cams = shadowCamsCached;
        if (cams == null) {
            cams = tryGetShadowCamsByReflection(this);
            shadowCamsCached = cams;
        }
        if (cams == null || cams.length == 0) return;

        float vNear = viewCam.getFrustumNear();
        float vFar = viewCam.getFrustumFar();
        if (!(vFar > vNear) || !(vNear > 0f)) return;

        lightDir.set(dl.getDirection());
        normalizeSafe(lightDir);

        frameCtx.ensure(cams.length);
        frameCtx.viewCam = viewCam;
        frameCtx.light = dl;
        frameCtx.dt = dt;

        frameCtx.viewNear = vNear;
        frameCtx.viewFar = vFar;
        frameCtx.mapSize = getShadowMapSize();
        frameCtx.lightDir.set(lightDir);

        frameCtx.renderer = this;

        pipeline.beginFrame(frameCtx);

        // Splits must be produced by filters (SplitCompute + Hysteresis etc.)
        pipeline.beforeSplits(frameCtx);
        pipeline.afterSplits(frameCtx);

        for (int i = 0; i < cams.length; i++) {
            frameCtx.cascadeIndex = i;
            frameCtx.shadowCam = cams[i];

            pipeline.beforeCascade(frameCtx, i);
            pipeline.afterFit(frameCtx, i);
            pipeline.afterSnap(frameCtx, i);

            if (dbgEnabled && log != null && log.isDebugEnabled() && ((dbgFrame++ % (long) dbgEveryFrames) == 0L)) {
                ShadowFrameContext.CascadeData cd = frameCtx.c[i];
                log.debug("[shadow][pipe] split={} range=[{}..{}] r={} quant={} snapped={}",
                        i, f3(cd.rangeNear), f3(cd.rangeFar), f3(cd.radius), cd.quantized, cd.snapped);
            }
        }

        pipeline.endFrame(frameCtx);

        // Update receiver uniforms snapshot
        updateSplitUniforms(materialState, frameCtx);
    }

    private void updateSplitUniforms(ShadowFrameContext.MaterialState state, ShadowFrameContext ctx) {
        float vFar = ctx.viewFar;
        float[] sf = ctx.splitFarsFinal;

        float s0 = (sf != null && sf.length > 0) ? sf[0] : vFar;
        float s1 = (sf != null && sf.length > 1) ? sf[1] : vFar;
        float s2 = (sf != null && sf.length > 2) ? sf[2] : vFar;
        float s3 = (sf != null && sf.length > 3) ? sf[3] : vFar;

        state.splitFars4.set(s0, s1, s2, s3);

        // copy per-frame material state from ctx.material
        state.shadowBias = ctx.material.shadowBias;
        state.shadowSlopeBias = ctx.material.shadowSlopeBias;
        state.shadowNormalOffset = ctx.material.shadowNormalOffset;

        state.cascadeBlendEnabled = ctx.material.cascadeBlendEnabled;
        state.cascadeBlendLen = ctx.material.cascadeBlendLen;
    }

    private float computeDtClamped() {
        long now = System.nanoTime();
        float dt = 1f / 60f;
        if (lastNs != 0L) {
            dt = (now - lastNs) * 1e-9f;
            dt = FastMath.clamp(dt, 1e-4f, 0.1f);
        }
        lastNs = now;
        return dt;
    }

    @Override
    public void clearShadows(RenderManager rm, ViewPort vp) {
        if (rm == null) return;

        Renderer r = rm.getRenderer();
        if (r == null) return;

        FrameBuffer[] fbs = tryGetShadowFbsByReflection(this);
        if (fbs == null || fbs.length == 0) return;

        FrameBuffer prev = null;
        try {
            prev = r.getCurrentFrameBuffer();
        } catch (Throwable ignored) {
        }

        try {
            for (FrameBuffer fb : fbs) {
                if (fb == null) continue;
                r.setFrameBuffer(fb);

                // Clear depth (and optionally color if your shadow FBO has it)
                r.clearBuffers(false, true, false);
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                r.setFrameBuffer(prev);
            } catch (Throwable ignored) {
            }
        }
    }

}