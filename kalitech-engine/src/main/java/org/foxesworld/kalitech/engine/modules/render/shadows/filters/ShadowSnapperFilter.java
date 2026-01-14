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
    public void afterFit(ShadowFrameContext ctx, int cascade) {
        // Snap happens inside renderer after it places the shadow cam.
        // This filter just exists as "owned config + hook point".
    }

    public boolean snap(int cascade, Camera sc, ShadowFrameContext ctx, float dt) {
        if (!enabled || snapper == null || sc == null) return false;
        return snapper.snap(cascade, sc, ctx.basis, dt, out);
    }

}