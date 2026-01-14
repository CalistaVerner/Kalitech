// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/ShadowTraceFilter.java
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class ShadowTraceFilter implements ShadowFilter {

    public int everyFrames = 60;
    private long frame = 0;

    @Override
    public int order() {
        return 5000;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        frame++;
    }

    @Override
    public boolean updateShadowCam(ShadowSplitContext ctx) {
        if (ctx.splitIndex == 0 && (frame % everyFrames) == 0) {
            Camera sc = ctx.shadowCam;
            float w = sc.getFrustumRight() - sc.getFrustumLeft();
            float texel = (sc.getWidth() > 0) ? (w / (float) sc.getWidth()) : -1f;
            System.out.println("[shadow][trace] frame=" + frame + " split0 orthoW=" + w + " texel=" + texel);
        }
        return false;
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (ctx.splitIndex != 0) return;
        if ((frame % everyFrames) != 0) return;

        Camera sc = ctx.shadowCam;

        float orthoW = sc.getFrustumRight() - sc.getFrustumLeft();
        float orthoH = sc.getFrustumTop() - sc.getFrustumBottom();
        float ortho = Math.max(orthoW, orthoH);
        float texel = (sc.getWidth() > 0) ? (ortho / (float) sc.getWidth()) : -1f;

        System.out.println(
                "[shadow][trace] frame=" + frame
                        + " split0 range=[" + ctx.splitNear + ".." + ctx.splitFar + "]"
                        + " ortho=" + ortho
                        + " texel=" + texel
                        + " scPos=" + sc.getLocation()
        );
    }

    public void setEveryFrames(int everyFrames) {
        this.everyFrames = everyFrames;
    }
}
