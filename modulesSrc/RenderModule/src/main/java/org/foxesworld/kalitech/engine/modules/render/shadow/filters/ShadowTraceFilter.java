/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.renderer.Camera
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.renderer.Camera;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class ShadowTraceFilter
implements ShadowFilter {
    private static final Logger LOG = LogManager.getLogger(ShadowTraceFilter.class);
    public int everyFrames = 60;

    @Override
    public int order() {
        return 5000;
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        float texel;
        if (ctx.splitIndex != 0) {
            return;
        }
        long frame = ctx.frame.frameId;
        if (this.everyFrames > 1 && frame % (long)this.everyFrames != 0L) {
            return;
        }
        Camera sc = ctx.shadowCam;
        float orthoW = sc.getFrustumRight() - sc.getFrustumLeft();
        float orthoH = sc.getFrustumTop() - sc.getFrustumBottom();
        float ortho = Math.max(orthoW, orthoH);
        float f = texel = ctx.frame.shadowMapSize > 0 ? ortho / (float)ctx.frame.shadowMapSize : -1.0f;
        if (LOG.isDebugEnabled()) {
            LOG.debug("[shadow][trace] frame={} split0 range=[{}..{}] ortho={} texel={} scPos={} handledCam={} snapped={} texelWorld={}", (Object)frame, (Object)Float.valueOf(ctx.splitNear), (Object)Float.valueOf(ctx.splitFar), (Object)Float.valueOf(ortho), (Object)Float.valueOf(texel), (Object)sc.getLocation(), (Object)ctx.handledCam, (Object)ctx.snapped, (Object)Float.valueOf(ctx.texelWorld));
        }
    }

    public void setEveryFrames(int everyFrames) {
        this.everyFrames = everyFrames;
    }
}

