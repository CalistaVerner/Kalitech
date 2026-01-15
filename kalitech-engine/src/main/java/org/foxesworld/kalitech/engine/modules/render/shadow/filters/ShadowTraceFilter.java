// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/ShadowTraceFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.renderer.Camera;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Debug trace for shadow stabilization parameters.
 */
public final class ShadowTraceFilter implements ShadowFilter {

    private static final Logger log = LogManager.getLogger(ShadowTraceFilter.class);

    public int everyFrames = 60;

    @Override
    public int order() {
        return 10_000;
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (!log.isDebugEnabled()) return;

        long frame = ctx.frame.frameId;
        if (everyFrames > 1 && (frame % everyFrames) != 0) return;

        Camera sc = ctx.shadowCam;

        float orthoW = sc.getFrustumRight() - sc.getFrustumLeft();
        float orthoH = sc.getFrustumTop() - sc.getFrustumBottom();
        float ortho = Math.max(orthoW, orthoH);

        float texel = ctx.ws.getOrDefault(ShadowKeys.TEXEL_WORLD, 0f);
        Boolean allow = ctx.ws.get(ShadowKeys.ALLOW_TEXEL_SNAP);
        Float move = ctx.frame.ws.get(ShadowKeys.VIEW_CAM_MOVE_WORLD);
        Float rot = ctx.frame.ws.get(ShadowKeys.VIEW_CAM_ROTATE_DEG);
        Boolean snapped = ctx.ws.get(ShadowKeys.TEXEL_SNAPPED);

        log.debug("[shadow][trace] frame={} split={} range=[{}..{}] ortho={} texel={} allowSnap={} camMove={} camRotDeg={} texelSnapped={} handledCam={}",
                frame,
                ctx.splitIndex,
                ctx.splitNear, ctx.splitFar,
                ortho,
                texel,
                allow != null ? allow : true,
                move != null ? move : 0f,
                rot != null ? rot : 0f,
                snapped != null ? snapped : false,
                ctx.handledCam);
    }

    public void setEveryFrames(int everyFrames) {
        this.everyFrames = everyFrames;
    }
}