// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/StableLightBasisFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Computes deterministic light basis (anti-flip) for stable shadow orientation.
 */
public final class StableLightBasisFilter implements ShadowFilter {

    private final Vector3f prevLeft = new Vector3f(1, 0, 0);
    private final Vector3f tmp = new Vector3f();
    private boolean init = false;

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
    public void beginSplit(ShadowSplitContext ctx) {
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

        if (!init) {
            prevLeft.set(ctx.lightLeft);
            init = true;
            return;
        }

        if (prevLeft.dot(ctx.lightLeft) < 0f) {
            ctx.lightLeft.negateLocal();
            ctx.lightUp.negateLocal();
        }
        prevLeft.set(ctx.lightLeft);
    }
}