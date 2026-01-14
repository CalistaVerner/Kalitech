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
 * <p>
 * Key points:
 * - Uses light-space AABB of the 8 frustum slice points.
 * - Expands Z for casters/receivers.
 * - Snaps min/max XY to worldUnitsPerTexel grid to eliminate shimmering.
 */
public final class TightStableFitShadowCamFilter implements ShadowFilter {

    private final Vector3f tmp = new Vector3f();
    private final Vector3f tmp2 = new Vector3f();
    private final Vector3f camLoc = new Vector3f();
    public float minNear = 0.5f;
    public float casterBackBase = 140f;
    public float casterBackCascadeMul = 0.9f;
    public float receiverFrontBase = 40f;
    public float xyPadding = 1.02f;
    public boolean forceSquare = true;
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

        float pad = Math.max(1.0f, xyPadding);

        float casterBack = casterBackBase * (1.0f + (float) ctx.splitIndex * casterBackCascadeMul);
        float receiverFront = receiverFrontBase;

        minZ -= casterBack;
        maxZ += receiverFront;

        float nearVal = Math.max(0.001f, minNear);
        float farVal = (maxZ - minZ) + nearVal;
        if (farVal < nearVal + 1.0f) farVal = nearVal + 1.0f;

        int map = Math.max(1, sc.getWidth());

        float sizeX = (maxX - minX) * pad;
        float sizeY = (maxY - minY) * pad;
        float baseSize = forceSquare ? Math.max(sizeX, sizeY) : sizeX;

        if (!(baseSize > 0f)) baseSize = 1.0f;

        float texel = baseSize / (float) map;

        if (sizeQuantizeTexels > 0f) {
            float q = Math.max(1.0f, sizeQuantizeTexels);
            float step = texel * q;
            if (step > 0f) {
                float snappedSize = (float) Math.ceil(baseSize / step) * step;
                baseSize = snappedSize;
                texel = baseSize / (float) map;
            }
        }

        float cx0 = (minX + maxX) * 0.5f;
        float cy0 = (minY + maxY) * 0.5f;

        float halfW0 = (maxX - minX) * 0.5f * pad;
        float halfH0 = (maxY - minY) * 0.5f * pad;

        if (forceSquare) {
            float m = Math.max(halfW0, halfH0);
            halfW0 = m;
            halfH0 = m;
        }

        float minXs = cx0 - halfW0;
        float maxXs = cx0 + halfW0;
        float minYs = cy0 - halfH0;
        float maxYs = cy0 + halfH0;

        minXs = FastMath.floor(minXs / texel) * texel;
        minYs = FastMath.floor(minYs / texel) * texel;
        maxXs = FastMath.floor(maxXs / texel) * texel;
        maxYs = FastMath.floor(maxYs / texel) * texel;

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
}