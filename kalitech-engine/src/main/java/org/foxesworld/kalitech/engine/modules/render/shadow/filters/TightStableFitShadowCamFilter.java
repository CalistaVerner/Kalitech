// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/TightStableFitShadowCamFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Tight stable cascade fitting in light space with texel-snapped bounds.
 * Adds near-cascade size stabilization (tier quantization + hysteresis) to reduce ortho breathing
 * and residual shimmering.
 */
public final class TightStableFitShadowCamFilter implements ShadowFilter {

    public float minNear = 0.5f;

    public float casterBackBase = 140f;
    public float casterBackCascadeMul = 0.9f;
    public float receiverFrontBase = 40f;

    public float xyPadding = 1.02f;

    public boolean forceSquare = true;

    // ---------------- Quantization/stabilization ----------------

    private final Vector3f tmp = new Vector3f();
    private final Vector3f tmp2 = new Vector3f();
    private final Vector3f camLoc = new Vector3f();
    /**
     * Quantize ortho size by fixed "reference texel" steps.
     * 1..4 is typical.
     */
    public float sizeQuantizeTexels = 1.0f;
    /**
     * Stabilize split 0 ortho size by quantizing it into larger tiers.
     */
    public boolean lockNearCascadeSize = true;
    /**
     * Tier size in texels for split 0. Recommended: 64..256 for 8192 maps.
     */
    public float nearTierTexels = 512f;
    public float maxNearShrinkPerUpdate = 0.10f; // -10% max per update

    private float lastNearSize = Float.NaN;
    /**
     * Shrink hysteresis for split 0 size (in tier units).
     * Prevents shrinking unless requested size is sufficiently smaller.
     */
    public float nearShrinkHysteresisTiers = 2.0f;
    /**
     * Grow hysteresis for split 0 size (in tier units).
     * Prevents growing unless requested size is sufficiently larger.
     */
    public float nearGrowHysteresisTiers = 0.75f;
    /**
     * Maximum allowed growth per update for split 0 (fraction of current size).
     * Example: 0.20 means +20% max per update.
     */
    public float maxNearGrowPerUpdate = 0.20f;
    private float lastNearTexelWorld = Float.NaN;

    @Override
    public int order() {
        return -500;
    }

    private static void normalizeSafe(Vector3f v) {
        float len2 = v.x * v.x + v.y * v.y + v.z * v.z;
        if (len2 <= 1e-20f) return;
        float inv = FastMath.invSqrt(len2);
        v.x *= inv;
        v.y *= inv;
        v.z *= inv;
    }

    private static float ceilToStep(float v, float step) {
        if (!(step > 0f)) return v;
        return (float) Math.ceil(v / step) * step;
    }

    @Override
    public boolean updateShadowCam(ShadowSplitContext ctx) {
        Camera sc = ctx.shadowCam;

        // Basis (deterministic if StableLightBasisFilter is in pipeline; otherwise fall back here)
        if (ctx.lightDir.lengthSquared() == 0f) {
            ctx.lightDir.set(ctx.light.getDirection());
            normalizeSafe(ctx.lightDir);

            tmp.set(Vector3f.UNIT_Y);
            if (FastMath.abs(ctx.lightDir.dot(tmp)) > 0.99f) tmp.set(Vector3f.UNIT_X);

            ctx.lightLeft.set(tmp).crossLocal(ctx.lightDir);
            normalizeSafe(ctx.lightLeft);

            ctx.lightUp.set(ctx.lightDir).crossLocal(ctx.lightLeft);
            normalizeSafe(ctx.lightUp);
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;

        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        // Frustum slice bounds in light space
        for (int i = 0; i < 8; i++) {
            Vector3f p = ctx.frustumPoints[i];

            float x = p.dot(ctx.lightLeft);
            float y = p.dot(ctx.lightUp);
            float z = p.dot(ctx.lightDir);

            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;

            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        float pad = Math.max(1.0f, xyPadding);

        float casterBack = casterBackBase * (1.0f + (float) ctx.splitIndex * casterBackCascadeMul);
        float receiverFront = receiverFrontBase;

        minZ -= casterBack;
        maxZ += receiverFront;

        float nearVal = Math.max(0.001f, minNear);
        float farVal = (maxZ - minZ) + nearVal;
        if (farVal < nearVal + 1.0f) farVal = nearVal + 1.0f;

        // Shadow map resolution
        int map = Math.max(1, ctx.frame.shadowMapSize);

        float sizeX = (maxX - minX) * pad;
        float sizeY = (maxY - minY) * pad;

        float baseSize = forceSquare ? Math.max(sizeX, sizeY) : sizeX;
        if (!(baseSize > 0f)) baseSize = 1.0f;

        // -----------------------------------------------------------------
        // Key: quantize size using a stable reference texelWorld, not baseSize-derived texel.
        // -----------------------------------------------------------------
        float texelRef = (!Float.isNaN(lastNearTexelWorld) && lastNearTexelWorld > 0f)
                ? lastNearTexelWorld
                : (baseSize / (float) map);

        // Base quantization (stabilizes scale)
        if (sizeQuantizeTexels > 0f) {
            float q = Math.max(1.0f, sizeQuantizeTexels);
            float stepWorld = texelRef * q;
            baseSize = ceilToStep(baseSize, stepWorld);
        }

        // Split0 tier lock with bidirectional hysteresis + grow clamp
        if (lockNearCascadeSize && ctx.splitIndex == 0 && nearTierTexels > 0f) {
            float tierWorld = texelRef * Math.max(1.0f, nearTierTexels);
            float tiered = ceilToStep(baseSize, tierWorld);

            if (Float.isNaN(lastNearSize)) {
                lastNearSize = tiered;
            } else {
                // Grow hysteresis
                float growGate = lastNearSize + tierWorld * Math.max(0f, nearGrowHysteresisTiers);
                if (tiered > growGate) {
                    float maxGrow = lastNearSize * (1.0f + Math.max(0f, maxNearGrowPerUpdate));
                    lastNearSize = Math.min(tiered, maxGrow);
                } else if (tiered > lastNearSize) {
                    tiered = lastNearSize;
                }

                // Shrink hysteresis
                float shrinkGate = lastNearSize - tierWorld * Math.max(0f, nearShrinkHysteresisTiers);
                if (tiered < shrinkGate) {
                    float minShrink = lastNearSize * (1.0f - Math.max(0f, maxNearShrinkPerUpdate));
                    lastNearSize = Math.max(tiered, minShrink);
                } else if (tiered < lastNearSize) {
                    tiered = lastNearSize;
                }

            }

            baseSize = lastNearSize;
            lastNearTexelWorld = baseSize / (float) map;
        } else {
            // Do not let far cascades contaminate split0 reference
            if (ctx.splitIndex == 0) {
                lastNearTexelWorld = baseSize / (float) map;
            }
        }

        // Center (still derived from actual min/max; size is stabilized)
        float cx0 = (minX + maxX) * 0.5f;
        float cy0 = (minY + maxY) * 0.5f;

        float half = baseSize * 0.5f;
        float minXs = cx0 - half;
        float maxXs = cx0 + half;
        float minYs = cy0 - half;
        float maxYs = cy0 + half;

        // Snap bounds to texel grid using final texelWorld = baseSize/map
        float texelWorld = baseSize / (float) map;

        minXs = FastMath.floor(minXs / texelWorld) * texelWorld;
        minYs = FastMath.floor(minYs / texelWorld) * texelWorld;
        maxXs = FastMath.floor(maxXs / texelWorld) * texelWorld;
        maxYs = FastMath.floor(maxYs / texelWorld) * texelWorld;

        float cx = (minXs + maxXs) * 0.5f;
        float cy = (minYs + maxYs) * 0.5f;

        float halfW = (maxXs - minXs) * 0.5f;
        float halfH = (maxYs - minYs) * 0.5f;

        if (forceSquare) {
            float m = Math.max(halfW, halfH);
            halfW = m;
            halfH = m;
        }

        camLoc.set(ctx.lightLeft).multLocal(cx);
        tmp.set(ctx.lightUp).multLocal(cy);
        camLoc.addLocal(tmp);
        tmp2.set(ctx.lightDir).multLocal(minZ - nearVal);
        camLoc.addLocal(tmp2);

        sc.setParallelProjection(true);
        sc.setAxes(ctx.lightLeft, ctx.lightUp, ctx.lightDir);
        sc.setLocation(camLoc);
        sc.setFrustum(nearVal, farVal, -halfW, halfW, halfH, -halfH);
        sc.update();

        return true;
    }

    // ---------------------------------------------------------------------
    // Setters (optional; JS config can set public fields directly)
    // ---------------------------------------------------------------------

    public void setMinNear(float minNear) {
        this.minNear = minNear;
    }

    public void setCasterBackBase(float casterBackBase) {
        this.casterBackBase = casterBackBase;
    }

    public void setCasterBackCascadeMul(float casterBackCascadeMul) {
        this.casterBackCascadeMul = casterBackCascadeMul;
    }

    public void setReceiverFrontBase(float receiverFrontBase) {
        this.receiverFrontBase = receiverFrontBase;
    }

    public void setXyPadding(float xyPadding) {
        this.xyPadding = xyPadding;
    }

    public void setForceSquare(boolean forceSquare) {
        this.forceSquare = forceSquare;
    }

    public void setSizeQuantizeTexels(float sizeQuantizeTexels) {
        this.sizeQuantizeTexels = sizeQuantizeTexels;
    }

    public void setLockNearCascadeSize(boolean lockNearCascadeSize) {
        this.lockNearCascadeSize = lockNearCascadeSize;
    }

    public void setNearTierTexels(float nearTierTexels) {
        this.nearTierTexels = nearTierTexels;
    }

    public void setNearShrinkHysteresisTiers(float nearShrinkHysteresisTiers) {
        this.nearShrinkHysteresisTiers = nearShrinkHysteresisTiers;
    }

    public void setNearGrowHysteresisTiers(float nearGrowHysteresisTiers) {
        this.nearGrowHysteresisTiers = nearGrowHysteresisTiers;
    }

    public void setMaxNearGrowPerUpdate(float maxNearGrowPerUpdate) {
        this.maxNearGrowPerUpdate = maxNearGrowPerUpdate;
    }

    /**
     * Resets the near-cascade lock state.
     */
    public void resetNearSizeLock() {
        lastNearSize = Float.NaN;
        lastNearTexelWorld = Float.NaN;
    }
}