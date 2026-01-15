// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/ShadowTraceFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.renderer.Camera;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Debug trace for shadow camera stability diagnostics.
 */
public final class ShadowTraceFilter implements ShadowFilter {

    private static final Logger log = LogManager.getLogger("RenderApiImpl");

    /**
     * Emit trace every N frames.
     */
    public int everyFrames = 60;

    /**
     * If true, logs all cascades. If false, only split 0.
     */
    public boolean allSplits = false;

    /**
     * If false, does nothing.
     */
    public boolean enabled = true;

    private long frameId = 0;

    @Override
    public int order() {
        return 10_000;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        frameId = ctx.frameId;
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (!enabled) return;
        if (everyFrames <= 0) return;
        if ((frameId % (long) everyFrames) != 0L) return;
        if (!allSplits && ctx.splitIndex != 0) return;

        Camera sc = ctx.shadowCam;

        float orthoW = sc.getFrustumRight() - sc.getFrustumLeft();
        float orthoH = sc.getFrustumTop() - sc.getFrustumBottom();
        float ortho = Math.max(orthoW, orthoH);

        float texel = (ctx.frame.shadowMapSize > 0) ? (ortho / (float) ctx.frame.shadowMapSize) : -1f;

        log.debug(
                "[shadow][trace] frame={} split{} range=[{}..{}] ortho={} texel={} scPos={} handledCam={} snapped={} texelWorld={}",
                frameId,
                ctx.splitIndex,
                ctx.splitNear,
                ctx.splitFar,
                ortho,
                texel,
                sc.getLocation(),
                ctx.shadowCamHandled,
                ctx.texelSnapped,
                ctx.texelWorld
        );
    }

    public void setEveryFrames(int everyFrames) {
        this.everyFrames = everyFrames;
    }

    public void setAllSplits(boolean allSplits) {
        this.allSplits = allSplits;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}