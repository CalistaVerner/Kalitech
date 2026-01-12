// FILE: org/foxesworld/kalitech/engine/modules/render/StableDirectionalLightShadowRenderer.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

/**
 * Stable Cascaded Shadow Maps (CDPR-style):
 * - For each cascade: compute 8 corners of the frustum slice in world space
 * - Fit a bounding sphere (center = AABB center, radius = max dist to corners)
 * - Place light camera at center - lightDir * (radius * backOffset)
 * - Ortho extents: [-radius..radius] x [-radius..radius], z: [near..far]
 * - Snap light camera to shadow texel grid to remove shimmering
 * <p>
 * Also provides shader bias knobs via setMaterialParameters():
 * - ShadowBias (constant)
 * - ShadowSlopeBias (slope/receiver-plane bias factor)
 * - ShadowNormalOffset (normal offset for casters/receivers)
 * <p>
 * IMPORTANT JME NOTES:
 * - shadowCam near must be > 0 (near=0 can lead to empty shadow maps)
 * - aspect must be float (avoid int/int)
 */
public class StableDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer {

    // ---- stable fitting ----
    protected final ShadowSnapper snapper;
    // ---- temp vectors (no per-frame allocations) ----
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
    protected boolean snapEnabled = true;
    /**
     * sphere padding >= 1.0
     */
    protected float extentsPadding = 1.05f;
    /**
     * camera placement: how far behind the sphere center (in radii)
     */
    protected float backOffset = 1.0f;
    /**
     * near = max(minNear, radius * nearFrac)
     */
    protected float minNear = 0.5f;
    protected float nearFrac = 0.01f;
    /**
     * far = radius * farMul
     */
    protected float farMul = 2.6f;
    // ---- bias knobs (sent to post material if present) ----
    protected float shadowBias = 0.0008f;
    protected float shadowSlopeBias = 2.0f;
    protected float shadowNormalOffset = 0.0f; // set >0 if you implement normal offset in shaders
    // ---- debug/logging ----
    private Logger log;
    private boolean dbgEnabled = false;
    private int dbgEveryFrames = 60;
    private long dbgFrame = 0;

    public StableDirectionalLightShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
        super(assets, shadowMapSize, nbSplits);
        this.snapper = new ShadowSnapper(shadowMapSize);

        // This helps in JME (stabilizes edges) but не заменяет CDPR sphere fitting.
        try {
            this.setEnabledStabilization(true);
        } catch (Throwable ignored) {
        }
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

    public boolean isSnapEnabled() {
        return snapEnabled;
    }

    // ---------- stable params ----------
    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
    }

    public float getExtentsPadding() {
        return extentsPadding;
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

    @Override
    protected void setMaterialParameters(Material material) {
        super.setMaterialParameters(material);

        // Only set if the material definition supports these params.
        if (material == null) return;

        if (material.getMaterialDef() != null) {
            if (material.getParam("ShadowBias") != null) {
                material.setFloat("ShadowBias", shadowBias);
            }
            if (material.getParam("ShadowSlopeBias") != null) {
                material.setFloat("ShadowSlopeBias", shadowSlopeBias);
            }
            if (material.getParam("ShadowNormalOffset") != null) {
                material.setFloat("ShadowNormalOffset", shadowNormalOffset);
            }
        }
    }

    @Override
    protected void clearMaterialParameters(Material material) {
        // keep super behavior
        super.clearMaterialParameters(material);
        // (We do not clear our params; they are stable per-frame)
    }

    @Override
    protected void updateShadowCams(Camera viewCam) {
        final boolean emit = dbgEnabled && log != null && log.isDebugEnabled()
                && ((dbgFrame++ % (long) dbgEveryFrames) == 0L);

        DirectionalLight dl = getLight();
        if (dl == null) {
            if (emit) log.debug("[shadow][stable] light=null => no shadows (setLight not called?)");
            super.updateShadowCams(viewCam);
            return;
        }

        if (viewCam == null) {
            if (emit) log.debug("[shadow][stable] viewCam=null => fallback to super");
            super.updateShadowCams(null);
            return;
        }

        // view camera basis
        camPos.set(viewCam.getLocation());
        camDir.set(viewCam.getDirection()).normalizeLocal();
        camUp.set(viewCam.getUp()).normalizeLocal();
        camLeft.set(viewCam.getLeft()).normalizeLocal();

        float camNear = viewCam.getFrustumNear();
        final float camFar = viewCam.getFrustumFar();

        if (!(camFar > camNear) || !(camNear > 0f)) {
            if (emit) log.debug(String.format(Locale.ROOT,
                    "[shadow][stable] invalid view frustum near=%.6f far=%.6f => fallback to super",
                    camNear, camFar));
            super.updateShadowCams(viewCam);
            return;
        }

        final int n = getNumShadowMaps();
        if (n <= 0) {
            if (emit) log.debug("[shadow][stable] numShadowMaps<=0 => fallback to super");
            super.updateShadowCams(viewCam);
            return;
        }

        // FOV & aspect (IMPORTANT: float division!)
        final float aspect = viewCam.getAspect();           // уже float, правильно учитывает viewport
        camNear = viewCam.getFrustumNear();
        final float frustumTop = viewCam.getFrustumTop();

// tan(fovY/2) = frustumTop / frustumNear (для perspective)
        final float tanFovHalf = frustumTop / camNear;

        if (!(aspect > 0.01f) || !(tanFovHalf > 0f)) {
            if (emit) log.debug(String.format(Locale.ROOT,
                    "[shadow][stable] invalid aspect/tanHalf aspect=%.6f tanHalf=%.6f top=%.6f near=%.6f parallel=%s => fallback to super",
                    aspect, tanFovHalf, frustumTop, camNear, String.valueOf(viewCam.isParallelProjection())));
            super.updateShadowCams(viewCam);
            return;
        }


        if (!(aspect > 0.01f) || !(tanFovHalf > 0f)) {
            if (emit) log.debug(String.format(Locale.ROOT,
                    "[shadow][stable] invalid aspect/fov aspect=%.6f fovY=%.6f => fallback to super",
                    aspect, viewCam.getFov()));
            super.updateShadowCams(viewCam);
            return;
        }

        // PSSM split distances (CDPR-style distribution via lambda)
        final float lambda = getLambda();
        final float[] splitDist = new float[n + 1];
        splitDist[0] = camNear;
        for (int i = 1; i < n; i++) {
            float id = (float) i / (float) n;
            float logSplit = camNear * FastMath.pow(camFar / camNear, id);
            float uniSplit = camNear + (camFar - camNear) * id;
            splitDist[i] = logSplit * lambda + uniSplit * (1.0f - lambda);
        }
        splitDist[n] = camFar;

        // light basis
        lightDir.set(dl.getDirection()).normalizeLocal();

        // pick an up candidate not parallel to lightDir
        tmp.set(Vector3f.UNIT_Y);
        if (FastMath.abs(lightDir.dot(tmp)) > 0.99f) tmp.set(Vector3f.UNIT_X);

        // left = up x dir
        lightLeft.set(tmp).crossLocal(lightDir).normalizeLocal();
        // up = dir x left
        lightUp.set(lightDir).crossLocal(lightLeft).normalizeLocal();

        if (emit) {
            log.debug(String.format(Locale.ROOT,
                    "[shadow][stable] view loc=(%.2f,%.2f,%.2f) near=%.3f far=%.3f fovY=%.2f aspect=%.3f splits=%d map=%d lambda=%.3f snap=%s pad=%.3f",
                    camPos.x, camPos.y, camPos.z,
                    camNear, camFar,
                    viewCam.getFov(), aspect,
                    n, getShadowMapSize(), lambda,
                    String.valueOf(snapEnabled), extentsPadding));
        }

        // per cascade
        for (int i = 0; i < n; i++) {
            float splitNear = splitDist[i];
            float splitFar = splitDist[i + 1];

            // centers
            nearCenter.set(camDir).multLocal(splitNear).addLocal(camPos);
            farCenter.set(camDir).multLocal(splitFar).addLocal(camPos);

            // plane extents
            float nearH = tanFovHalf * splitNear;
            float nearW = nearH * aspect;
            float farH = tanFovHalf * splitFar;
            float farW = farH * aspect;

            upNear.set(camUp).multLocal(nearH);
            leftNear.set(camLeft).multLocal(nearW);
            upFar.set(camUp).multLocal(farH);
            leftFar.set(camLeft).multLocal(farW);

            // 8 corners (no allocations)
            // near
            corners[0].set(nearCenter).addLocal(upNear).addLocal(leftNear);       // NTL
            corners[1].set(nearCenter).addLocal(upNear).subtractLocal(leftNear);  // NTR
            corners[2].set(nearCenter).subtractLocal(upNear).addLocal(leftNear);  // NBL
            corners[3].set(nearCenter).subtractLocal(upNear).subtractLocal(leftNear); // NBR
            // far
            corners[4].set(farCenter).addLocal(upFar).addLocal(leftFar);          // FTL
            corners[5].set(farCenter).addLocal(upFar).subtractLocal(leftFar);     // FTR
            corners[6].set(farCenter).subtractLocal(upFar).addLocal(leftFar);     // FBL
            corners[7].set(farCenter).subtractLocal(upFar).subtractLocal(leftFar);// FBR

            // AABB in world
            min.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
            max.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
            for (int k = 0; k < 8; k++) {
                Vector3f c = corners[k];
                if (c.x < min.x) min.x = c.x;
                if (c.y < min.y) min.y = c.y;
                if (c.z < min.z) min.z = c.z;
                if (c.x > max.x) max.x = c.x;
                if (c.y > max.y) max.y = c.y;
                if (c.z > max.z) max.z = c.z;
            }

            // sphere center = AABB center (good approximation, stable)
            center.set(min).addLocal(max).multLocal(0.5f);

            // radius = max dist to corners
            float radius = 0f;
            for (int k = 0; k < 8; k++) {
                float d = corners[k].distance(center);
                if (d > radius) radius = d;
            }
            radius *= extentsPadding;

            // light cam location: behind center
            camLoc.set(lightDir).multLocal(-radius * backOffset).addLocal(center);

            // Ortho frustum
            float left = -radius;
            float right = radius;
            float bottom = -radius;
            float top = radius;

            float nearVal = Math.max(minNear, radius * nearFrac);
            float farVal = radius * farMul;

            Camera sCam = getShadowCam(i);
            if (sCam == null) {
                if (emit) log.debug("[shadow][stable] split=" + i + " shadowCam=null");
                continue;
            }

            sCam.setParallelProjection(true);
            sCam.setLocation(camLoc);
            // Camera axes in JME are (left, up, direction)
            sCam.setAxes(lightLeft, lightUp, lightDir);
            sCam.setFrustum(nearVal, farVal, left, right, top, bottom);
            sCam.update();

            boolean snapped = false;
            if (snapEnabled) {
                snapped = snapper.snap(sCam, emit ? snapDbg : null);
                if (snapped) sCam.update();
            }

            if (emit) {
                float texel = (emit ? snapDbg.texel : (2f * radius / (float) getShadowMapSize()));
                log.debug(String.format(Locale.ROOT,
                        "[shadow][stable] split=%d range=[%.3f..%.3f] sphereC=(%.2f,%.2f,%.2f) r=%.3f cam=(%.2f,%.2f,%.2f) orthoXY=[%.3f] z=[%.3f..%.3f] texel=%.6f snapped=%s dxy=(%.6f,%.6f)",
                        i, splitNear, splitFar,
                        center.x, center.y, center.z,
                        radius,
                        sCam.getLocation().x, sCam.getLocation().y, sCam.getLocation().z,
                        radius,
                        nearVal, farVal,
                        texel,
                        String.valueOf(snapped),
                        emit ? snapDbg.dx : 0f,
                        emit ? snapDbg.dy : 0f
                ));
            }
        }
    }
}