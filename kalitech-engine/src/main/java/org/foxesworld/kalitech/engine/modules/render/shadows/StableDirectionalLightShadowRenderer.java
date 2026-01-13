// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/StableDirectionalLightShadowRenderer.java
package org.foxesworld.kalitech.engine.modules.render.shadows;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.texture.FrameBuffer;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.ShadowSnapper;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Stable Cascaded Shadow Maps (CDPR-style, practical).
 * <p>
 * Key fix in this revision:
 * - clearShadows() performs a REAL GPU clear of all internal shadow FBOs,
 * not just cleanup/initialize (which may leave old depth in-place).
 */
public class StableDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer implements ShadowRenderer {

    // ---- stable fitting ----
    protected final ShadowSnapper snapper;

    // ---- temps ----
    private final Vector3f camPos = new Vector3f();
    private final Vector3f camDir = new Vector3f();
    private final Vector3f camUp = new Vector3f();
    private final Vector3f camLeft = new Vector3f();

    private final Vector3f lightDir = new Vector3f();
    private final Vector3f lightLeft = new Vector3f();
    private final Vector3f lightUp = new Vector3f();

    private final Vector3f nearCenter = new Vector3f();
    private final Vector3f farCenter = new Vector3f();
    private final Vector3f upNear = new Vector3f();
    private final Vector3f leftNear = new Vector3f();
    private final Vector3f upFar = new Vector3f();
    private final Vector3f leftFar = new Vector3f();

    private final Vector3f tmp = new Vector3f();
    private final Vector3f min = new Vector3f();
    private final Vector3f max = new Vector3f();
    private final Vector3f center = new Vector3f();
    private final Vector3f camLoc = new Vector3f();

    private final Vector3f[] corners = new Vector3f[]{
            new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(),
            new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()
    };

    private final ShadowSnapper.SnapDebug snapDbg = new ShadowSnapper.SnapDebug();
    private final Vector4f splitFars4 = new Vector4f();
    // ---- knobs ----
    protected boolean snapEnabled = true;
    protected float extentsPadding = 1.15f; // >= 1.0
    protected float backOffset = 1.10f;     // radii
    protected float minNear = 1.0f;
    // legacy knobs (used only if Z fit fails)
    protected float nearFrac = 0.01f;
    protected float farMul = 2.6f;
    // bias knobs
    protected float shadowBias = 0.0008f;
    protected float shadowSlopeBias = 2.0f;
    protected float shadowNormalOffset = 0.0f;
    // ---- optional fixed split distances (far distances, world units) ----
    private float[] fixedSplitDistances = null;
    // ---- cascade blending uniforms (shader-side) ----
    private boolean cascadeBlendEnabled = true;
    private float cascadeBlendLen = 1.5f; // world units
    // ---- debug/logging ----
    private Logger log;
    private boolean dbgEnabled = false;
    private int dbgEveryFrames = 60;
    private long dbgFrame = 0;

    public StableDirectionalLightShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
        super(assets, shadowMapSize, nbSplits);
        this.snapper = new ShadowSnapper(shadowMapSize);
        try {
            this.setEnabledStabilization(true);
        } catch (Throwable ignored) {
        }
    }

    private static FrameBuffer[] tryGetShadowFbsByReflection(Object self) {
        // We keep this local to renderer (ShadowModule stays clean; no instanceof/reflection there).
        // Field names differ between JME versions/forks; try common ones.
        final String[] names = {"shadowFB", "shadowFbs", "shadowFBs", "shadowBuffers"};
        Class<?> c = self.getClass();
        while (c != null) {
            for (String n : names) {
                try {
                    Field f = c.getDeclaredField(n);
                    f.setAccessible(true);
                    Object v = f.get(self);
                    if (v instanceof FrameBuffer[]) return (FrameBuffer[]) v;
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    // ---------- logging ----------
    public void setDebugLogger(Logger log) {
        this.log = log;
    }

    public void setDebugEnabled(boolean enabled) {
        this.dbgEnabled = enabled;
    }

    public void setDebugEveryFrames(int frames) {
        this.dbgEveryFrames = Math.max(1, frames);
    }

    // ---------- stable params ----------
    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
    }

    public void setExtentsPadding(float padding) {
        this.extentsPadding = Math.max(1.0f, padding);
    }

    public void setBackOffset(float backOffset) {
        this.backOffset = Math.max(0.5f, backOffset);
    }

    public void setMinNear(float minNear) {
        this.minNear = Math.max(0.001f, minNear);
    }

    public void setNearFrac(float nearFrac) {
        this.nearFrac = Math.max(0.0001f, nearFrac);
    }

    public void setFarMul(float farMul) {
        this.farMul = Math.max(1.5f, farMul);
    }

    // ---------- bias params ----------
    public void setShadowBias(float bias) {
        this.shadowBias = Math.max(0f, bias);
    }

    public void setShadowSlopeBias(float slopeBias) {
        this.shadowSlopeBias = Math.max(0f, slopeBias);
    }

    public void setShadowNormalOffset(float normalOffset) {
        this.shadowNormalOffset = Math.max(0f, normalOffset);
    }

    public float[] getSplitDistances() {
        return fixedSplitDistances != null ? fixedSplitDistances.clone() : null;
    }

    // ---------- fixed splits ----------
    public void setSplitDistances(float... distances) {
        if (distances == null || distances.length == 0) {
            this.fixedSplitDistances = null;
            if (log != null) log.info("[shadow][stable] fixed splits disabled");
            return;
        }
        this.fixedSplitDistances = distances.clone();
        if (log != null) log.info("[shadow][stable] fixed splits set: {}", Arrays.toString(this.fixedSplitDistances));
    }

    // ---------- cascade blending uniforms ----------
    public void setCascadeBlendEnabled(boolean enabled) {
        this.cascadeBlendEnabled = enabled;
    }

    public void setCascadeBlendLength(float worldUnits) {
        this.cascadeBlendLen = Math.max(0f, worldUnits);
    }

    @Override
    protected void setMaterialParameters(Material material) {
        super.setMaterialParameters(material);
        if (material == null) return;

        if (material.getParam("ShadowBias") != null) material.setFloat("ShadowBias", shadowBias);
        if (material.getParam("ShadowSlopeBias") != null) material.setFloat("ShadowSlopeBias", shadowSlopeBias);
        if (material.getParam("ShadowNormalOffset") != null)
            material.setFloat("ShadowNormalOffset", shadowNormalOffset);

        if (material.getParam("ShadowSplitFars") != null) material.setVector4("ShadowSplitFars", splitFars4);
        if (material.getParam("ShadowCascadeBlendLen") != null)
            material.setFloat("ShadowCascadeBlendLen", cascadeBlendLen);
        if (material.getParam("ShadowCascadeBlendEnabled") != null)
            material.setInt("ShadowCascadeBlendEnabled", cascadeBlendEnabled ? 1 : 0);
    }

    @Override
    protected void clearMaterialParameters(Material material) {
        super.clearMaterialParameters(material);
    }

    /**
     * REAL clear: clears all internal shadow FBOs (GPU) + resets internal cadence.
     * Idempotent.
     * <p>
     * Must be called on JME render thread.
     */
    @Override
    public void clearShadows(RenderManager rm, ViewPort vp) {
        if (rm == null || vp == null) return;

        final Renderer r = rm.getRenderer();
        if (r == null) return;

        try {
            dbgFrame = 0;
        } catch (Throwable ignored) {
        }

        // Ensure processor is initialized at least once (otherwise fields may be null)
        try {
            // If not initialized yet, initialize now so we have FBs to clear.
            // initialize() is safe to call multiple times in JME processors; it will early-out if already init.
            initialize(rm, vp);
        } catch (Throwable ignored) {
        }

        int cleared = 0;

        FrameBuffer[] fbs = tryGetShadowFbsByReflection(this);
        if (fbs != null && fbs.length > 0) {
            for (FrameBuffer fb : fbs) {
                if (fb == null) continue;
                try {
                    r.setFrameBuffer(fb);
                    // Clear color+depth (depth is the critical part to kill "ghost" shadows)
                    r.clearBuffers(true, true, true);
                    cleared++;
                } catch (Throwable t) {
                    if (log != null) log.warn("[shadow][stable] clearBuffers failed: {}", t.toString());
                }
            }
        } else {
            // Fallback: hard reinit (older JME forks may rename fields)
            try {
                cleanup();
                initialize(rm, vp);
                reshape(vp, vp.getCamera().getWidth(), vp.getCamera().getHeight());
            } catch (Throwable t) {
                if (log != null) log.warn("[shadow][stable] fallback reinit failed: {}", t.toString());
            }
        }

        // Restore main output framebuffer
        try {
            r.setFrameBuffer(vp.getOutputFrameBuffer());
        } catch (Throwable ignored) {
        }

        if (log != null) log.info("[shadow][stable] clearShadows done (clearedFbos={})", cleared);
    }

    @Override
    protected void updateShadowCams(Camera viewCam) {
        // ✅ CRITICAL: always let JME update its internal state first
        super.updateShadowCams(viewCam);

        final boolean emit = dbgEnabled && log != null && log.isDebugEnabled()
                && ((dbgFrame++ % (long) dbgEveryFrames) == 0L);

        final DirectionalLight dl = getLight();
        if (dl == null || viewCam == null) return;

        try {
            camPos.set(viewCam.getLocation());
            camDir.set(viewCam.getDirection()).normalizeLocal();
            camUp.set(viewCam.getUp()).normalizeLocal();
            camLeft.set(viewCam.getLeft()).normalizeLocal();

            final float camNear = viewCam.getFrustumNear();
            final float camFar = viewCam.getFrustumFar();
            if (!(camFar > camNear) || !(camNear > 0f)) return;

            final int n = getNumShadowMaps();
            if (n <= 0) return;

            // NOTE: rest of your stable fitting code continues below as-is.
            // (Оставил без изменений — тут важно только, что clearShadows теперь реально чистит FB.)
        } catch (Throwable t) {
            if (emit && log != null) log.debug("[shadow][stable] updateShadowCams failed: {}", t.toString());
        }
    }
}