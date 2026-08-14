/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.FastMath
 *  com.jme3.math.Vector3f
 *  com.jme3.renderer.Camera
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class TightStableFitShadowCamFilter
implements ShadowFilter {
    public float minNear = 0.5f;
    public float casterBackBase = 0.5f;
    public float casterBackCascadeMul = 0.35f;
    public float receiverFrontBase = 0.5f;
    public boolean forceSquare = true;
    public int nearTierTexels = 0;
    public boolean lockNearCascadeSize = false;
    public int nearShrinkHysteresisTiers = 0;
    public float sizeQuantizeTexels = 0.0f;
    private final Vector3f tmp = new Vector3f();
    private final Vector3f tmp2 = new Vector3f();
    private final Vector3f camLoc = new Vector3f();
    private float lockedOrthoSize = -1.0f;
    private int lockedTier = -1;

    @Override
    public int order() {
        return -500;
    }

    private static void normalizeSafe(Vector3f v) {
        float len2 = v.x * v.x + v.y * v.y + v.z * v.z;
        if (len2 <= 1.0E-20f) {
            return;
        }
        float inv = FastMath.invSqrt((float)len2);
        v.x *= inv;
        v.y *= inv;
        v.z *= inv;
    }

    private static float ceilToStep(float v, float step) {
        if (!(step > 0.0f)) {
            return v;
        }
        return (float)Math.ceil(v / step) * step;
    }

    @Override
    public boolean updateShadowCam(ShadowSplitContext ctx) {
        float ortho;
        Camera sc = ctx.shadowCam;
        if (ctx.lightDir.lengthSquared() == 0.0f) {
            ctx.lightDir.set(ctx.light.getDirection());
            TightStableFitShadowCamFilter.normalizeSafe(ctx.lightDir);
            this.tmp.set(Vector3f.UNIT_Y);
            if (FastMath.abs((float)ctx.lightDir.dot(this.tmp)) > 0.99f) {
                this.tmp.set(Vector3f.UNIT_X);
            }
            ctx.lightLeft.set(this.tmp).crossLocal(ctx.lightDir);
            TightStableFitShadowCamFilter.normalizeSafe(ctx.lightLeft);
            ctx.lightUp.set(ctx.lightDir).crossLocal(ctx.lightLeft);
            TightStableFitShadowCamFilter.normalizeSafe(ctx.lightUp);
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; ++i) {
            Vector3f p = ctx.frustumPoints[i];
            float x = p.dot(ctx.lightLeft);
            float y = p.dot(ctx.lightUp);
            float z = p.dot(ctx.lightDir);
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (z < minZ) {
                minZ = z;
            }
            if (!(z > maxZ)) continue;
            maxZ = z;
        }
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float halfW = (maxX - minX) * 0.5f;
        float halfH = (maxY - minY) * 0.5f;
        if (this.forceSquare) {
            float m;
            halfW = m = Math.max(halfW, halfH);
            halfH = m;
        }
        float receiverFront = Math.max(0.0f, this.receiverFrontBase);
        float casterBack = Math.max(0.0f, this.casterBackBase + this.casterBackCascadeMul * (float)ctx.splitIndex);
        float nearVal = Math.max(this.minNear, minZ - casterBack);
        float farVal = maxZ + receiverFront;
        int map = ctx.frame.shadowMapSize;
        if (map > 0 && (ortho = Math.max(halfW, halfH) * 2.0f) > 0.0f) {
            float half;
            float texelWorld = ortho / (float)map;
            if (this.sizeQuantizeTexels > 0.0f) {
                float q = Math.max(1.0f, this.sizeQuantizeTexels);
                float step = texelWorld * q;
                float snapped = TightStableFitShadowCamFilter.ceilToStep(ortho, step);
                halfW = half = snapped * 0.5f;
                halfH = half;
                ortho = snapped;
                texelWorld = ortho / (float)map;
            }
            if (this.lockNearCascadeSize && this.nearTierTexels > 0 && ctx.splitIndex == 0) {
                int hyst;
                float tierWorld = texelWorld * (float)this.nearTierTexels;
                float tieredOrtho = TightStableFitShadowCamFilter.ceilToStep(ortho, tierWorld);
                int tier = Math.max(1, Math.round(tieredOrtho / tierWorld));
                if (this.lockedTier < 0) {
                    this.lockedTier = tier;
                    this.lockedOrthoSize = tieredOrtho;
                } else if (tier > this.lockedTier) {
                    this.lockedTier = tier;
                    this.lockedOrthoSize = tieredOrtho;
                } else if (tier < this.lockedTier && this.lockedTier - tier > (hyst = Math.max(0, this.nearShrinkHysteresisTiers))) {
                    this.lockedTier = tier;
                    this.lockedOrthoSize = tieredOrtho;
                }
                halfW = half = this.lockedOrthoSize * 0.5f;
                halfH = half;
                texelWorld = this.lockedOrthoSize / (float)map;
            }
            ctx.texelWorld = texelWorld;
            ctx.ws.put(ShadowKeys.TEXEL_WORLD, Float.valueOf(texelWorld));
        }
        this.camLoc.set(ctx.lightLeft).multLocal(cx);
        this.tmp.set(ctx.lightUp).multLocal(cy);
        this.camLoc.addLocal(this.tmp);
        this.tmp2.set(ctx.lightDir).multLocal(minZ - nearVal);
        this.camLoc.addLocal(this.tmp2);
        sc.setParallelProjection(true);
        sc.setLocation(this.camLoc);
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

