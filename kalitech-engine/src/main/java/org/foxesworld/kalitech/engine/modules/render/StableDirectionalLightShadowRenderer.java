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
import java.util.Objects;

/**
 * Stable Cascaded Shadow Maps (CDPR-style):
 * <ul>
 *   <li>Compute 8 corners of the view frustum slice (world space).</li>
 *   <li>Fit a stable bounding sphere: center = AABB center, radius = max distance to corners.</li>
 *   <li>Ortho extents in XY from the sphere: [-radius..radius].</li>
 *   <li>Depth range in Z is computed along light direction from receiver volume,
 *       extended to include shadow casters (prevents clipping).</li>
 *   <li>Snap the light camera to the shadow texel grid to remove shimmering.</li>
 *   <li>Anti-flip light basis to keep stable orientation across frames.</li>
 * </ul>
 */
public class StableDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer {

    protected final ShadowSnapper snapper;

    private final Vector3f camPos = new Vector3f();
    private final Vector3f camDir = new Vector3f();
    private final Vector3f camUp = new Vector3f();
    private final Vector3f camLeft = new Vector3f();

    private final Vector3f lightDir = new Vector3f();
    private final Vector3f lightLeft = new Vector3f();
    private final Vector3f lightUp = new Vector3f();

    private final Vector3f prevLightLeft = new Vector3f(1f, 0f, 0f);
    private final Vector3f prevLightUp = new Vector3f(0f, 1f, 0f);
    private final Vector3f nearCenter = new Vector3f();
    private final Vector3f farCenter = new Vector3f();
    private final Vector3f upNear = new Vector3f();
    private final Vector3f leftNear = new Vector3f();
    private final Vector3f upFar = new Vector3f();
    private final Vector3f leftFar = new Vector3f();
    private final Vector3f tmp = new Vector3f();
    private final Vector3f tmp2 = new Vector3f();
    private final Vector3f min = new Vector3f();
    private final Vector3f max = new Vector3f();
    private final Vector3f center = new Vector3f();
    private final Vector3f centerPerp = new Vector3f();
    private final Vector3f camLoc = new Vector3f();
    private final Vector3f[] corners = new Vector3f[]{
            new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(),
            new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()
    };
    private final ShadowSnapper.SnapDebug snapDbg = new ShadowSnapper.SnapDebug();
    protected boolean snapEnabled = true;
    protected float extentsPadding = 1.05f;
    protected float minNear = 0.5f;
    protected float shadowBias = 0.0008f;
    protected float shadowSlopeBias = 2.0f;
    protected float shadowNormalOffset = 0.0f;
    private boolean basisInit = false;
    private float casterBackBase = 140f;
    private float casterBackCascadeMul = 0.9f;
    private float receiverFrontBase = 40f;
    private Logger log;
    private boolean dbgEnabled = false;
    private int dbgEveryFrames = 60;
    private long dbgFrame = 0;

    public StableDirectionalLightShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
        super(assets, shadowMapSize, nbSplits);
        this.snapper = new ShadowSnapper(shadowMapSize);

        try {
            this.setEnabledStabilization(false);
        } catch (Throwable ignored) {
        }
    }

    private static void normalizeSafe(Vector3f v) {
        Objects.requireNonNull(v, "v");
        float len2 = v.x * v.x + v.y * v.y + v.z * v.z;
        if (len2 <= 1e-20f) return;
        float inv = FastMath.invSqrt(len2);
        v.x *= inv;
        v.y *= inv;
        v.z *= inv;
    }

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

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
    }

    public float getExtentsPadding() {
        return extentsPadding;
    }

    public void setExtentsPadding(float padding) {
        this.extentsPadding = Math.max(1.0f, padding);
    }

    public void setMinNear(float minNear) {
        this.minNear = Math.max(0.001f, minNear);
    }

    public float getCasterBackBase() {
        return casterBackBase;
    }

    public void setCasterBackBase(float casterBackBase) {
        this.casterBackBase = Math.max(0f, casterBackBase);
    }

    public float getCasterBackCascadeMul() {
        return casterBackCascadeMul;
    }

    public void setCasterBackCascadeMul(float casterBackCascadeMul) {
        this.casterBackCascadeMul = Math.max(0f, casterBackCascadeMul);
    }

    public float getReceiverFrontBase() {
        return receiverFrontBase;
    }

    public void setReceiverFrontBase(float receiverFrontBase) {
        this.receiverFrontBase = Math.max(0f, receiverFrontBase);
    }

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

        if (material == null || material.getMaterialDef() == null) return;

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

    @Override
    protected void updateShadowCams(Camera viewCam) {
        final boolean emit = dbgEnabled && log != null && log.isDebugEnabled()
                && ((dbgFrame++ % (long) dbgEveryFrames) == 0L);

        DirectionalLight dl = getLight();
        if (dl == null || viewCam == null) {
            if (emit && log != null) log.debug("[shadow][stable] missing light/viewCam => fallback to super");
            super.updateShadowCams(viewCam);
            return;
        }

        camPos.set(viewCam.getLocation());
        camDir.set(viewCam.getDirection());
        camUp.set(viewCam.getUp());
        camLeft.set(viewCam.getLeft());
        normalizeSafe(camDir);
        normalizeSafe(camUp);
        normalizeSafe(camLeft);

        final float vNear = viewCam.getFrustumNear();
        final float vFarCam = viewCam.getFrustumFar();
        if (!(vFarCam > vNear) || !(vNear > 0f)) {
            if (emit && log != null) {
                log.debug(String.format(Locale.ROOT,
                        "[shadow][stable] invalid view frustum near=%.6f far=%.6f => fallback to super",
                        vNear, vFarCam));
            }
            super.updateShadowCams(viewCam);
            return;
        }

        final float zExtend = getShadowZExtend();
        final float vFar = (zExtend > 0f) ? Math.min(vFarCam, zExtend) : vFarCam;

        final int n = getNumShadowMaps();
        if (n <= 0) {
            super.updateShadowCams(viewCam);
            return;
        }

        final float aspect = viewCam.getAspect();
        final float frustumTop = viewCam.getFrustumTop();
        final float tanFovHalf = frustumTop / vNear;

        if (!(aspect > 0.01f) || !(tanFovHalf > 0f)) {
            if (emit && log != null) {
                log.debug(String.format(Locale.ROOT,
                        "[shadow][stable] invalid aspect/tanHalf aspect=%.6f tanHalf=%.6f => fallback to super",
                        aspect, tanFovHalf));
            }
            super.updateShadowCams(viewCam);
            return;
        }

        final float lambda = getLambda();
        final float[] splitDist = new float[n + 1];
        splitDist[0] = vNear;
        for (int i = 1; i < n; i++) {
            float id = (float) i / (float) n;
            float logSplit = vNear * FastMath.pow(vFar / vNear, id);
            float uniSplit = vNear + (vFar - vNear) * id;
            splitDist[i] = logSplit * lambda + uniSplit * (1.0f - lambda);
        }
        splitDist[n] = vFar;

        lightDir.set(dl.getDirection());
        normalizeSafe(lightDir);

        tmp.set(Vector3f.UNIT_Y);
        if (FastMath.abs(lightDir.dot(tmp)) > 0.99f) {
            tmp.set(Vector3f.UNIT_X);
        }

        lightLeft.set(tmp).crossLocal(lightDir);
        normalizeSafe(lightLeft);

        lightUp.set(lightDir).crossLocal(lightLeft);
        normalizeSafe(lightUp);

        if (!basisInit) {
            prevLightLeft.set(lightLeft);
            prevLightUp.set(lightUp);
            basisInit = true;
        } else {
            if (prevLightLeft.dot(lightLeft) < 0f) {
                lightLeft.negateLocal();
                lightUp.negateLocal();
            }
            prevLightLeft.set(lightLeft);
            prevLightUp.set(lightUp);
        }

        if (emit && log != null) {
            log.debug(String.format(Locale.ROOT,
                    "[shadow][stable] view loc=(%.2f,%.2f,%.2f) near=%.3f far=%.3f fovY=%.2f aspect=%.3f splits=%d map=%d lambda=%.3f snap=%s pad=%.3f zExtend=%.3f",
                    camPos.x, camPos.y, camPos.z,
                    vNear, vFar,
                    viewCam.getFov(), aspect,
                    n, getShadowMapSize(), lambda,
                    String.valueOf(snapEnabled), extentsPadding, zExtend));
        }

        for (int i = 0; i < n; i++) {
            float splitNear = splitDist[i];
            float splitFar = splitDist[i + 1];

            nearCenter.set(camDir).multLocal(splitNear).addLocal(camPos);
            farCenter.set(camDir).multLocal(splitFar).addLocal(camPos);

            float nearH = tanFovHalf * splitNear;
            float nearW = nearH * aspect;
            float farH = tanFovHalf * splitFar;
            float farW = farH * aspect;

            upNear.set(camUp).multLocal(nearH);
            leftNear.set(camLeft).multLocal(nearW);
            upFar.set(camUp).multLocal(farH);
            leftFar.set(camLeft).multLocal(farW);

            corners[0].set(nearCenter).addLocal(upNear).addLocal(leftNear);
            corners[1].set(nearCenter).addLocal(upNear).subtractLocal(leftNear);
            corners[2].set(nearCenter).subtractLocal(upNear).addLocal(leftNear);
            corners[3].set(nearCenter).subtractLocal(upNear).subtractLocal(leftNear);
            corners[4].set(farCenter).addLocal(upFar).addLocal(leftFar);
            corners[5].set(farCenter).addLocal(upFar).subtractLocal(leftFar);
            corners[6].set(farCenter).subtractLocal(upFar).addLocal(leftFar);
            corners[7].set(farCenter).subtractLocal(upFar).subtractLocal(leftFar);

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

            center.set(min).addLocal(max).multLocal(0.5f);

            float radius = 0f;
            for (int k = 0; k < 8; k++) {
                float d = corners[k].distance(center);
                if (d > radius) radius = d;
            }
            radius *= extentsPadding;

            float left = -radius;
            float right = radius;
            float bottom = -radius;
            float top = radius;

            float minZ = Float.POSITIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (int k = 0; k < 8; k++) {
                float z = corners[k].dot(lightDir);
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
            }

            float casterBack = casterBackBase * (1.0f + (float) i * casterBackCascadeMul);
            float receiverFront = receiverFrontBase;

            minZ -= casterBack;
            maxZ += receiverFront;

            float centerZ = center.dot(lightDir);
            tmp.set(lightDir).multLocal(centerZ);
            centerPerp.set(center).subtractLocal(tmp);

            float camZ = minZ - minNear;
            camLoc.set(centerPerp).addLocal(tmp2.set(lightDir).multLocal(camZ));

            float nearVal = minNear;
            float farVal = (maxZ - minZ) + minNear;
            if (farVal < nearVal + 1.0f) farVal = nearVal + 1.0f;

            Camera sCam = getShadowCam(i);
            if (sCam == null) continue;

            sCam.setParallelProjection(true);
            sCam.setLocation(camLoc);
            sCam.setAxes(lightLeft, lightUp, lightDir);
            sCam.setFrustum(nearVal, farVal, left, right, top, bottom);
            sCam.update();

            boolean snapped = false;
            if (snapEnabled) {
                snapped = snapper.snap(sCam, emit ? snapDbg : null);
                if (snapped) sCam.update();
            }

            if (emit && log != null) {
                float texel = (emit ? snapDbg.texel : (2f * radius / (float) getShadowMapSize()));
                log.debug(String.format(Locale.ROOT,
                        "[shadow][stable] split=%d range=[%.3f..%.3f] center=(%.2f,%.2f,%.2f) r=%.3f ortho=%.3f z=[%.3f..%.3f] texel=%.6f snapped=%s",
                        i, splitNear, splitFar,
                        center.x, center.y, center.z,
                        radius,
                        radius,
                        nearVal, farVal,
                        texel,
                        String.valueOf(snapped)));
            }
        }
    }
}