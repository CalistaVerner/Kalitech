// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/ShadowSnapperFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadows.ShadowSnapper;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

import java.util.Objects;

/**
 * Texel-grid snapping for each cascade (translation snapping in stable light space).
 * Presence in pipeline == enabled.
 */
public final class ShadowSnapperFilter implements ShadowFilter {

    private final ShadowSnapper snapper;
    private final ShadowSnapper.SnapResult out = new ShadowSnapper.SnapResult();

    public ShadowSnapperFilter(ShadowSnapper snapper) {
        this.snapper = Objects.requireNonNull(snapper, "snapper");
    }

    @Override
    public void afterSnap(ShadowFrameContext ctx, int cascade) {
        Camera sc = ctx.shadowCam;
        boolean snapped = snapper.snap(cascade, sc, ctx.basis, ctx.dt, out);
        ctx.c[cascade].snapped = snapped;
    }
}