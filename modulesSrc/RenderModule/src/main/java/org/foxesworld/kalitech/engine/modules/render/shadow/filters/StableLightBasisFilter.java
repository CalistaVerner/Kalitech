/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.FastMath
 *  com.jme3.math.Vector3f
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class StableLightBasisFilter
implements ShadowFilter {
    private final Vector3f prevLeft = new Vector3f(1.0f, 0.0f, 0.0f);
    private final Vector3f tmp = new Vector3f();
    private final Vector3f outDir = new Vector3f();
    private final Vector3f outLeft = new Vector3f();
    private final Vector3f outUp = new Vector3f();
    private boolean init;

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
    public int order() {
        return -1000;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        this.outDir.set(ctx.light.getDirection());
        StableLightBasisFilter.normalizeSafe(this.outDir);
        this.tmp.set(Vector3f.UNIT_Y);
        if (FastMath.abs((float)this.outDir.dot(this.tmp)) > 0.99f) {
            this.tmp.set(Vector3f.UNIT_X);
        }
        this.outLeft.set(this.tmp).crossLocal(this.outDir);
        StableLightBasisFilter.normalizeSafe(this.outLeft);
        this.outUp.set(this.outDir).crossLocal(this.outLeft);
        StableLightBasisFilter.normalizeSafe(this.outUp);
        if (this.init && this.prevLeft.dot(this.outLeft) < 0.0f) {
            this.outLeft.negateLocal();
            this.outUp.negateLocal();
        }
        this.prevLeft.set(this.outLeft);
        this.init = true;
        ctx.ws.put(ShadowKeys.LIGHT_DIR, this.outDir);
        ctx.ws.put(ShadowKeys.LIGHT_LEFT, this.outLeft);
        ctx.ws.put(ShadowKeys.LIGHT_UP, this.outUp);
    }

    @Override
    public void beginSplit(ShadowSplitContext ctx) {
        Vector3f d = ctx.frame.ws.get(ShadowKeys.LIGHT_DIR);
        Vector3f l = ctx.frame.ws.get(ShadowKeys.LIGHT_LEFT);
        Vector3f u = ctx.frame.ws.get(ShadowKeys.LIGHT_UP);
        if (d != null && l != null && u != null) {
            ctx.lightDir.set(d);
            ctx.lightLeft.set(l);
            ctx.lightUp.set(u);
            return;
        }
        ctx.lightDir.set(ctx.light.getDirection());
        StableLightBasisFilter.normalizeSafe(ctx.lightDir);
        this.tmp.set(Vector3f.UNIT_Y);
        if (FastMath.abs((float)ctx.lightDir.dot(this.tmp)) > 0.99f) {
            this.tmp.set(Vector3f.UNIT_X);
        }
        ctx.lightLeft.set(this.tmp).crossLocal(ctx.lightDir);
        StableLightBasisFilter.normalizeSafe(ctx.lightLeft);
        ctx.lightUp.set(ctx.lightDir).crossLocal(ctx.lightLeft);
        StableLightBasisFilter.normalizeSafe(ctx.lightUp);
    }
}

