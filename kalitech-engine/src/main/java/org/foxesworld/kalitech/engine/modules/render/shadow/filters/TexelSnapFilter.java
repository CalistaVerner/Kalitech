// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/TexelSnapFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.Snapper;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class TexelSnapFilter implements ShadowFilter {

    public boolean enabled = true;
    public int snapFirstCascades = 1;
    /**
     * Optional temporal gate (set by module).
     */
    public TemporalSnapGateFilter gate;
    private Snapper snapper;

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (snapper == null) {
            snapper = new Snapper(ctx.shadowMapSize);
        }
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (!enabled || snapper == null) return;
        if (ctx.splitIndex >= snapFirstCascades) return;

        Camera sc = ctx.shadowCam;

        float texelWorld = 0f;
        if (sc.getWidth() > 0) {
            float orthoW = sc.getFrustumRight() - sc.getFrustumLeft();
            texelWorld = orthoW / (float) sc.getWidth();
        }

        if (gate != null) {
            if (!gate.allowSnap(ctx, texelWorld)) {
                return;
            }
        }

        snapper.snap(sc);
        sc.update();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSnapFirstCascades(int snapFirstCascades) {
        this.snapFirstCascades = snapFirstCascades;
    }

    public void setGate(TemporalSnapGateFilter gate) {
        this.gate = gate;
    }

    public void setSnapper(Snapper snapper) {
        this.snapper = snapper;
    }
}