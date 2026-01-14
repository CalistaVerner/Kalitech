// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/StableFitShadowCamFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Stable tight cascade fitting in light space.
 * <p>
 * Uses light-space AABB of the 8 frustum slice points instead of a bounding sphere
 * to avoid oversized ortho extents (huge texels).
 */
public final class StableFitShadowCamFilter implements ShadowFilter {

    private final Vector3f tmp = new Vector3f();
    private final Vector3f tmp2 = new Vector3f();
    private final Vector3f camLoc = new Vector3f();
    public float extentsPadding = 1.02f;
    public float minNear = 0.5f;
    public float casterBackBase = 140f;
    public float casterBackCascadeMul = 0.9f;
    public float receiverFrontBase = 40f;
    /**
     * Force square ortho extents (recommended for stable texel snapping).
     */
    public boolean forceSquare = true;
    /**
     * Quantize ortho size in texel steps to stabilize texel world size (0 disables).
     */
    public float sizeQuantizeTexels = 1.0f;

    private static void normalizeSafe(Vector3f v) {
        float len2 = v.x * v.x + v.y * v.y + v.z * v.z;
        if (len2 <= 1e-20f) return;
        float inv = FastMath.invSqrt(len2);
        v.x *= inv;
        v.y *= inv;
        v.z *= inv;
    }

    @Override
    public int order() {
        return -500;
    }

    @Override
    public boolean updateShadowCam(ShadowSplitContext ctx) {
        Camera sc = ctx.shadowCam;

        // Requires basis to be computed before; fallback if not provided.
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

        float pad = Math.max(1.0f, extentsPadding);

        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;

        float halfW = (maxX - minX) * 0.5f * pad;
        float halfH = (maxY - minY) * 0.5f * pad;

        if (forceSquare) {
            float m = Math.max(halfW, halfH);
            halfW = m;
            halfH = m;
        }

        float casterBack = casterBackBase * (1.0f + (float) ctx.splitIndex * casterBackCascadeMul);
        float receiverFront = receiverFrontBase;

        minZ -= casterBack;
        maxZ += receiverFront;

        float nearVal = Math.max(0.001f, minNear);
        float farVal = (maxZ - minZ) + nearVal;
        if (farVal < nearVal + 1.0f) farVal = nearVal + 1.0f;

        // Quantize size to stabilize texelWorld.
        if (sizeQuantizeTexels > 0f && sc.getWidth() > 0) {
            int map = sc.getWidth();
            float size = Math.max(halfW, halfH) * 2.0f;
            float texel = size / (float) map;

            float q = Math.max(1.0f, sizeQuantizeTexels);
            float step = texel * q;

            if (step > 0f) {
                float snappedSize = (float) Math.ceil(size / step) * step;
                float half = snappedSize * 0.5f;
                halfW = half;
                halfH = half;
            }
        }

        // Camera location in world: left*cx + up*cy + dir*(minZ - near)
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
}