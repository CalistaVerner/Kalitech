// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/ShadowSnapperFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.Snapper;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Simple snapper for legacy pipelines.
 * <p>
 * Uses {@link ShadowKeys#ALLOW_TEXEL_SNAP} gate when present and publishes {@link ShadowKeys#TEXEL_SNAPPED}.
 */
public final class ShadowSnapperFilter implements ShadowFilter {

    public boolean enabled = true;
    public int snapFirstCascades = 2;

    private Snapper snapper;

    private static int ctxShadowMapSizeHack(Snapper s) {
        try {
            var f = Snapper.class.getDeclaredField("shadowMapSize");
            f.setAccessible(true);
            return (int) f.get(s);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (snapper == null || snapperShadowMapSize() != ctx.shadowMapSize) {
            snapper = new Snapper(ctx.shadowMapSize);
        }
    }

    private int snapperShadowMapSize() {
        return snapper != null ? ctxShadowMapSizeHack(snapper) : -1;
    }

    @Override
    public void endSplit(ShadowSplitContext ctx) {
        ctx.snapped = false;
        ctx.texelSnapped = false;

        if (!enabled || snapper == null) return;
        if (ctx.splitIndex >= snapFirstCascades) return;

        Boolean allowBoxed = ctx.ws.get(ShadowKeys.ALLOW_TEXEL_SNAP);
        boolean allow = allowBoxed == null || allowBoxed;
        if (!allow) {
            ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, false);
            return;
        }

        Camera sc = ctx.shadowCam;
        boolean changed = snapper.snap(sc);

        ctx.snapped = changed;
        ctx.texelSnapped = changed;

        ctx.ws.put(ShadowKeys.SNAP_APPLIED, true);
        ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, changed);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSnapFirstCascades(int snapFirstCascades) {
        this.snapFirstCascades = snapFirstCascades;
    }

    public void setSnapper(Snapper snapper) {
        this.snapper = snapper;
    }
}