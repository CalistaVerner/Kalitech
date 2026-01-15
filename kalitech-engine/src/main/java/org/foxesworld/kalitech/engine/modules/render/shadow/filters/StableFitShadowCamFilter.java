// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/StableFitShadowCamFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Stable tight cascade fitting in light space.
 * <p>
 * Uses light-space AABB of the 8 frustum slice points instead of a bounding sphere
 * to reduce oversized ortho extents (smaller texels, less shimmering).
 * Publishes {@link ShadowKeys#TEXEL_WORLD} for downstream filters.
 */
public final class StableFitShadowCamFilter implements ShadowFilter {

    private final Vector3f tmp = new Vector3f();
    private final Vector3f camLoc = new Vector3f();
    private final Vector3f tmp2 = new Vector3f();

    public float minNear = 0.5f;
    public float receiverFrontBase = 0.5f;

    public boolean forceSquare = true;
    public float casterBackBase = 0.5f;
    public float casterBackCascadeMul = 0.35f;
    public float extentsPadding = 0.0f;
    public float sizeQuantizeTexels = 0.0f;

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

        if (extentsPadding > 0f) {
            float p = extentsPadding;
            minX -= p;
            maxX += p;
            minY -= p;
            maxY += p;
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

        if (sizeQuantizeTexels > 0f && ctx.frame.shadowMapSize > 0) {
            float size = Math.max(halfW, halfH) * 2.0f;
            float texel = size / (float) ctx.frame.shadowMapSize;

            float q = Math.max(1.0f, sizeQuantizeTexels);
            float step = texel * q;

            if (step > 0f) {
                float snappedSize = (float) Math.ceil(size / step) * step;
                float half = snappedSize * 0.5f;
                halfW = half;
                halfH = half;
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

        if (ctx.frame.shadowMapSize > 0) {
            float ortho = Math.max(halfW, halfH) * 2.0f;
            if (ortho > 0f) {
                float texelWorld = ortho / (float) ctx.frame.shadowMapSize;
                ctx.texelWorld = texelWorld;
                ctx.ws.put(ShadowKeys.TEXEL_WORLD, texelWorld);
            }
        }

        return true;
    }

    public void setSizeQuantizeTexels(float sizeQuantizeTexels) {
        this.sizeQuantizeTexels = sizeQuantizeTexels;
    }

    public void setForceSquare(boolean forceSquare) {
        this.forceSquare = forceSquare;
    }

    public void setReceiverFrontBase(float receiverFrontBase) {
        this.receiverFrontBase = receiverFrontBase;
    }

    public void setCasterBackCascadeMul(float casterBackCascadeMul) {
        this.casterBackCascadeMul = casterBackCascadeMul;
    }

    public void setCasterBackBase(float casterBackBase) {
        this.casterBackBase = casterBackBase;
    }

    public void setMinNear(float minNear) {
        this.minNear = minNear;
    }

    public void setExtentsPadding(float extentsPadding) {
        this.extentsPadding = extentsPadding;
    }
}