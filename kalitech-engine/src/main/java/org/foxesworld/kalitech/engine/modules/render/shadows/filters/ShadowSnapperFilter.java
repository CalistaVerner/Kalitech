// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/ShadowSnapperFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadows.ShadowSnapper;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

public final class ShadowSnapperFilter implements ShadowFilter {

    private final ShadowSnapper snapper;
    private final ShadowSnapper.SnapResult out = new ShadowSnapper.SnapResult();

    public boolean enabled = true;

    public ShadowSnapperFilter(ShadowSnapper snapper) {
        this.snapper = snapper;
    }

    @Override
    public String id() {
        return "ShadowSnapper";
    }

    @Override
    public void afterSnap(ShadowFrameContext ctx, int cascade) {
        if (!enabled) return;
        if (snapper == null) return;
        if (ctx == null) return;

        Camera sc = ctx.shadowCam;
        if (sc == null) return;

        boolean snapped = snapper.snap(cascade, sc, ctx.basis, ctx.dt, out);
        ctx.c[cascade].snapped = snapped;
    }
}