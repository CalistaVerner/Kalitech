// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/TightStableFitShadowCamFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Tight stable cascade fitting in light space with quantized extents.
 * Publishes {@link ShadowKeys#TEXEL_WORLD} for downstream filters.
 */
public final class TightStableFitShadowCamFilter implements ShadowFilter {

    public float minNear = 0.5f;

    public float casterBackBase = 0.5f;
    public float casterBackCascadeMul = 0.35f;

    public float receiverFrontBase = 0.5f;

    public boolean forceSquare = true;

    /**
     * Quantize ortho size in "tier texels" steps for near cascades (0 disables).
     */
    public int nearTierTexels = 0;

    /**
     * Lock near cascade size to avoid breathing (uses tier quantization).
     */
    public boolean lockNearCascadeSize = false;

    /**
     * Hysteresis tiers to avoid frequent shrink/grow in near cascades.
     */
    public int nearShrinkHysteresisTiers = 0;

    /**
     * Texel quantization of bounds in light space (0 disables).
     */
    public float sizeQuantizeTexels = 0.0f;

    private final Vector3f tmp = new Vector3f();
    private final Vector3f tmp2 = new Vector3f();
    private final Vector3f camLoc = new Vector3f();

    private float lockedOrthoSize = -1f;
    private int lockedTier = -1;

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

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < 8; i++) {
            Vector3f p = ctx.frustumPoints[i];

            float x = p.dot(ctx.lightLeft);
            float y = p.dot(ctx.lightUp);
            float z = p.dot(ctx.lightDir);

            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;

        float halfW = (maxX - minX) * 0.5f;
        float halfH = (maxY - minY) * 0.5f;

        if (forceSquare) {
            float m = Math.max(halfW, halfH);
            halfW = m;
            halfH = m;
        }

        float receiverFront = Math.max(0f, receiverFrontBase);
        float casterBack = Math.max(0f, casterBackBase + casterBackCascadeMul * ctx.splitIndex);

        float nearVal = Math.max(minNear, minZ - casterBack);
        float farVal = maxZ + receiverFront;

        int map = ctx.frame.shadowMapSize;
        if (map > 0) {
            float ortho = Math.max(halfW, halfH) * 2.0f;
            if (ortho > 0f) {
                float texelWorld = ortho / (float) map;

                if (sizeQuantizeTexels > 0f) {
                    float q = Math.max(1.0f, sizeQuantizeTexels);
                    float step = texelWorld * q;
                    float snapped = ceilToStep(ortho, step);
                    float half = snapped * 0.5f;
                    halfW = half;
                    halfH = half;
                    ortho = snapped;
                    texelWorld = ortho / (float) map;
                }

                if (lockNearCascadeSize && nearTierTexels > 0 && ctx.splitIndex == 0) {
                    float tierWorld = texelWorld * (float) nearTierTexels;
                    float tieredOrtho = ceilToStep(ortho, tierWorld);
                    int tier = Math.max(1, (int) Math.round(tieredOrtho / tierWorld));

                    if (lockedTier < 0) {
                        lockedTier = tier;
                        lockedOrthoSize = tieredOrtho;
                    } else {
                        if (tier > lockedTier) {
                            lockedTier = tier;
                            lockedOrthoSize = tieredOrtho;
                        } else if (tier < lockedTier) {
                            int hyst = Math.max(0, nearShrinkHysteresisTiers);
                            if ((lockedTier - tier) > hyst) {
                                lockedTier = tier;
                                lockedOrthoSize = tieredOrtho;
                            }
                        }
                    }

                    float half = lockedOrthoSize * 0.5f;
                    halfW = half;
                    halfH = half;
                    texelWorld = lockedOrthoSize / (float) map;
                }

                ctx.texelWorld = texelWorld;
                ctx.ws.put(ShadowKeys.TEXEL_WORLD, texelWorld);
            }
        }

        camLoc.set(ctx.lightLeft).multLocal(cx);
        tmp.set(ctx.lightUp).multLocal(cy);
        camLoc.addLocal(tmp);
        tmp2.set(ctx.lightDir).multLocal(minZ - nearVal);
        camLoc.addLocal(tmp2);

        sc.setParallelProjection(true);
        sc.setLocation(camLoc);
        sc.setAxes(ctx.lightLeft, ctx.lightUp, ctx.lightDir);
        sc.setFrustum(nearVal, farVal, -halfW, halfW, halfH, -halfH);
        sc.update();

        return true;
    }

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

    public void setForceSquare(boolean forceSquare) {
        this.forceSquare = forceSquare;
    }

    public void setNearTierTexels(int nearTierTexels) {
        this.nearTierTexels = nearTierTexels;
    }

    public void setLockNearCascadeSize(boolean lockNearCascadeSize) {
        this.lockNearCascadeSize = lockNearCascadeSize;
    }

    public void setNearShrinkHysteresisTiers(int nearShrinkHysteresisTiers) {
        this.nearShrinkHysteresisTiers = nearShrinkHysteresisTiers;
    }

    public void setSizeQuantizeTexels(float sizeQuantizeTexels) {
        this.sizeQuantizeTexels = sizeQuantizeTexels;
    }
}