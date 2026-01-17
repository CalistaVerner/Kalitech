// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/ShadowGpuParamsPackFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.Matrix4f;
import org.foxesworld.kalitech.engine.modules.render.shadow.gpu.ShadowGpuParams;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKey;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowOrders;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

import java.util.Set;

/**
 * Mandatory CPU-side GPU packet builder.
 * <p>
 * Builds {@link ShadowGpuParams} in {@link org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowWorkspace}
 * for the current frame. GPU upload must be performed by the renderer once per frame.
 */
public final class ShadowGpuParamsPackFilter implements ShadowFilter {

    private final Matrix4f tmpViewProj = new Matrix4f();
    private ShadowGpuParams params;

    @Override
    public int order() {
        return ShadowOrders.GPU_PACK;
    }

    @Override
    public Set<ShadowKey<?>> requires() {
        return Set.of(
                ShadowKeys.ALLOW_SHADOW_CAM_REFIT,
                ShadowKeys.ALLOW_TEXEL_SNAP,
                ShadowKeys.SPLIT_TELEPORT,
                ShadowKeys.TEXEL_WORLD
        );
    }

    @Override
    public Set<ShadowKey<?>> provides() {
        return Set.of(ShadowKeys.GPU_PARAMS);
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (params == null) {
            params = new ShadowGpuParams();
        }
        params.beginFrame(ctx.frameId, ctx.numSplits, ctx.shadowMapSize);

        // Strict-writes friendly: write once, mutate in-place later.
        ctx.ws.put(ShadowKeys.GPU_PARAMS, params);
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (params == null) return;

        tmpViewProj.set(ctx.shadowCam.getViewProjectionMatrix());

        Boolean allowRefitBoxed = ctx.ws.get(ShadowKeys.ALLOW_SHADOW_CAM_REFIT);
        boolean allowRefit = allowRefitBoxed == null || allowRefitBoxed;

        Boolean allowSnapBoxed = ctx.ws.get(ShadowKeys.ALLOW_TEXEL_SNAP);
        boolean allowSnap = allowSnapBoxed == null || allowSnapBoxed;

        Boolean teleportBoxed = ctx.ws.get(ShadowKeys.SPLIT_TELEPORT);
        boolean teleport = teleportBoxed != null && teleportBoxed;

        Float texelWorldBoxed = ctx.ws.get(ShadowKeys.TEXEL_WORLD);
        float texelWorld = texelWorldBoxed != null ? texelWorldBoxed : ctx.texelWorld;

        int flags = 0;
        if (allowRefit) flags |= ShadowGpuParams.FLAG_ALLOW_REFIT;
        if (allowSnap) flags |= ShadowGpuParams.FLAG_ALLOW_SNAP;
        if (teleport) flags |= ShadowGpuParams.FLAG_TELEPORT;
        if (ctx.texelSnapped) flags |= ShadowGpuParams.FLAG_TEXEL_SNAPPED;

        params.setSplit(
                ctx.splitIndex,
                tmpViewProj,
                ctx.splitNear,
                ctx.splitFar,
                texelWorld,
                flags
        );
    }
}