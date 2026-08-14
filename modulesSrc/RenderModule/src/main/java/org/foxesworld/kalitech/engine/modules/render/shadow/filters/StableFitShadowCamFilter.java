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
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class StableFitShadowCamFilter
implements ShadowFilter {
    private final Vector3f tmp = new Vector3f();
    private final Vector3f camLoc = new Vector3f();
    private final Vector3f tmp2 = new Vector3f();
    private final Vector3f frameDir = new Vector3f();
    private final Vector3f frameLeft = new Vector3f();
    private final Vector3f frameUp = new Vector3f();
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
        if (len2 <= 1.0E-20f) {
            return;
        }
        float inv = FastMath.invSqrt((float)len2);
        v.x *= inv;
        v.y *= inv;
        v.z *= inv;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        this.frameDir.set(ctx.light.getDirection());
        StableFitShadowCamFilter.normalizeSafe(this.frameDir);
        this.tmp.set(Vector3f.UNIT_Y);
        if (FastMath.abs((float)this.frameDir.dot(this.tmp)) > 0.99f) {
            this.tmp.set(Vector3f.UNIT_X);
        }
        this.frameLeft.set(this.tmp).crossLocal(this.frameDir);
        StableFitShadowCamFilter.normalizeSafe(this.frameLeft);
        this.frameUp.set(this.frameDir).crossLocal(this.frameLeft);
        StableFitShadowCamFilter.normalizeSafe(this.frameUp);
        ctx.ws.put(ShadowKeys.LIGHT_DIR, this.frameDir);
        ctx.ws.put(ShadowKeys.LIGHT_LEFT, this.frameLeft);
        ctx.ws.put(ShadowKeys.LIGHT_UP, this.frameUp);
    }

    private void fetchFrameBasis(ShadowSplitContext ctx) {
        Vector3f dir = ctx.frame.ws.get(ShadowKeys.LIGHT_DIR);
        Vector3f left = ctx.frame.ws.get(ShadowKeys.LIGHT_LEFT);
        Vector3f up = ctx.frame.ws.get(ShadowKeys.LIGHT_UP);
        if (dir == null || left == null || up == null) {
            this.beginFrame(ctx.frame);
            dir = ctx.frame.ws.get(ShadowKeys.LIGHT_DIR);
            left = ctx.frame.ws.get(ShadowKeys.LIGHT_LEFT);
            up = ctx.frame.ws.get(ShadowKeys.LIGHT_UP);
        }
        ctx.lightDir.set(dir);
        ctx.lightLeft.set(left);
        ctx.lightUp.set(up);
    }

    @Override
    public boolean updateShadowCam(ShadowSplitContext ctx) {
        float ortho;
        float q;
        float size;
        float texel;
        float step;
        Camera sc = ctx.shadowCam;
        this.fetchFrameBasis(ctx);
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
        if (this.extentsPadding > 0.0f) {
            float p = this.extentsPadding;
            minX -= p;
            maxX += p;
            minY -= p;
            maxY += p;
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
        if (this.sizeQuantizeTexels > 0.0f && ctx.frame.shadowMapSize > 0 && (step = (texel = (size = Math.max(halfW, halfH) * 2.0f) / (float)ctx.frame.shadowMapSize) * (q = Math.max(1.0f, this.sizeQuantizeTexels))) > 0.0f) {
            float half;
            float snappedSize = (float)Math.ceil(size / step) * step;
            halfW = half = snappedSize * 0.5f;
            halfH = half;
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
        if (ctx.frame.shadowMapSize > 0 && (ortho = Math.max(halfW, halfH) * 2.0f) > 0.0f) {
            float texelWorld;
            ctx.texelWorld = texelWorld = ortho / (float)ctx.frame.shadowMapSize;
            ctx.ws.put(ShadowKeys.TEXEL_WORLD, Float.valueOf(texelWorld));
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

