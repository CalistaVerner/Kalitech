// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/ShadowTraceFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Debug trace for shadow stabilization parameters.
 */
public final class ShadowTraceFilter implements ShadowFilter {

    public int everyFrames = 60;

    @Override
    public int order() {
        return 5000;
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (ctx.splitIndex != 0) return;

        long frame = ctx.frame.frameId;
        if (everyFrames > 1 && (frame % everyFrames) != 0) return;

        Camera sc = ctx.shadowCam;

        float orthoW = sc.getFrustumRight() - sc.getFrustumLeft();
        float orthoH = sc.getFrustumTop() - sc.getFrustumBottom();
        float ortho = Math.max(orthoW, orthoH);

        float texel = (ctx.frame.shadowMapSize > 0) ? (ortho / (float) ctx.frame.shadowMapSize) : -1f;

        System.out.println(
                "[shadow][trace] frame=" + frame
                        + " split0 range=[" + ctx.splitNear + ".." + ctx.splitFar + "]"
                        + " ortho=" + ortho
                        + " texel=" + texel
                        + " scPos=" + sc.getLocation()
                        + " handledCam=" + ctx.handledCam
                        + " snapped=" + ctx.snapped
                        + " texelWorld=" + ctx.texelWorld
        );
    }

    public void setEveryFrames(int everyFrames) {
        this.everyFrames = everyFrames;
    }
}