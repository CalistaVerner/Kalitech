// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/ShadowSnapperFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.Snapper;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Snaps the shadow camera to texel grid to eliminate shimmering.
 */
public final class ShadowSnapperFilter implements ShadowFilter {

    public boolean enabled = true;
    private Snapper snapper;

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (snapper == null) {
            snapper = new Snapper(ctx.shadowMapSize);
        }
    }

    @Override
    public void endFrame(ShadowFrameContext ctx) {
        // no-op
    }

    @Override
    public void endSplit(ShadowSplitContext ctx) {
        if (!enabled || snapper == null) return;
        Camera sc = ctx.shadowCam;
        snapper.snap(sc);
    }
}