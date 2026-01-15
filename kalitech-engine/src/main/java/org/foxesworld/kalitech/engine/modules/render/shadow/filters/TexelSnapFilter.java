// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/TexelSnapFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.Snapper;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Snaps the shadow camera to a texel grid to eliminate shimmering.
 */
public final class TexelSnapFilter implements ShadowFilter {

    public boolean enabled = true;
    public int snapFirstCascades = 1;

    public TemporalSnapGateFilter gate;

    private Snapper snapper;
    private int snapperMapSize = -1;

    @Override
    public int order() {
        return 1000;
    }

    private static float computeTexelWorld(Camera sc, int mapSize) {
        if (sc == null || mapSize <= 0) return 0f;
        if (!sc.isParallelProjection()) return 0f;

        float width = sc.getFrustumRight() - sc.getFrustumLeft();
        float height = sc.getFrustumTop() - sc.getFrustumBottom();
        if (!(width > 0f) || !(height > 0f)) return 0f;

        float size = Math.max(width, height);
        return size / (float) mapSize;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (snapper == null || snapperMapSize != ctx.shadowMapSize) {
            snapper = new Snapper(ctx.shadowMapSize);
            snapperMapSize = ctx.shadowMapSize;
        }
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (!enabled || snapper == null) return;
        if (ctx.splitIndex >= snapFirstCascades) return;

        Camera sc = ctx.shadowCam;

        float texelWorld = computeTexelWorld(sc, ctx.frame.shadowMapSize);
        ctx.texelWorld = texelWorld;

        if (gate != null && !gate.allowSnap(ctx, texelWorld)) {
            ctx.texelSnapped = false;
            return;
        }

        ctx.texelSnapped = snapper.snap(sc);
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
        this.snapperMapSize = -1;
    }
}