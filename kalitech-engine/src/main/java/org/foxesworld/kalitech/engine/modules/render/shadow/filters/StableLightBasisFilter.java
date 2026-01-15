// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/StableLightBasisFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Computes deterministic light basis (anti-flip) for stable shadow orientation.
 * <p>
 * Publishes results into {@link org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowWorkspace}
 * (frame-scope) and copies to per-split context.
 */
public final class StableLightBasisFilter implements ShadowFilter {

    private final Vector3f prevLeft = new Vector3f(1, 0, 0);
    private final Vector3f tmp = new Vector3f();
    private final Vector3f outDir = new Vector3f();
    private final Vector3f outLeft = new Vector3f();
    private final Vector3f outUp = new Vector3f();
    private boolean init;

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
        return -1000;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        outDir.set(ctx.light.getDirection());
        normalizeSafe(outDir);

        tmp.set(Vector3f.UNIT_Y);
        if (FastMath.abs(outDir.dot(tmp)) > 0.99f) {
            tmp.set(Vector3f.UNIT_X);
        }

        outLeft.set(tmp).crossLocal(outDir);
        normalizeSafe(outLeft);

        outUp.set(outDir).crossLocal(outLeft);
        normalizeSafe(outUp);

        if (init && prevLeft.dot(outLeft) < 0f) {
            outLeft.negateLocal();
            outUp.negateLocal();
        }

        prevLeft.set(outLeft);
        init = true;

        ctx.ws.put(ShadowKeys.LIGHT_DIR, outDir);
        ctx.ws.put(ShadowKeys.LIGHT_LEFT, outLeft);
        ctx.ws.put(ShadowKeys.LIGHT_UP, outUp);
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
        normalizeSafe(ctx.lightDir);

        tmp.set(Vector3f.UNIT_Y);
        if (FastMath.abs(ctx.lightDir.dot(tmp)) > 0.99f) {
            tmp.set(Vector3f.UNIT_X);
        }

        ctx.lightLeft.set(tmp).crossLocal(ctx.lightDir);
        normalizeSafe(ctx.lightLeft);

        ctx.lightUp.set(ctx.lightDir).crossLocal(ctx.lightLeft);
        normalizeSafe(ctx.lightUp);
    }
}