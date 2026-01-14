// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/ShadowCamPlacementFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

import java.util.Objects;

/**
 * Places the shadow camera using the stable light basis and fitted cascade extents.
 * This is the last "hard" stage before optional snapping.
 */
public final class ShadowCamPlacementFilter implements ShadowFilter {

    public final Cfg cfg;
    private final Vector3f camLoc = new Vector3f();

    private final Vector3f axisRight = new Vector3f();
    private final Vector3f axisUp = new Vector3f();
    private final Vector3f axisDir = new Vector3f();
    private final Vector3f axisRightNeg = new Vector3f();

    public ShadowCamPlacementFilter() {
        this(new Cfg());
    }
    private final Vector3f tmp = new Vector3f();

    public ShadowCamPlacementFilter(Cfg cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    @Override
    public void afterFit(ShadowFrameContext ctx, int cascade) {
        Camera sc = ctx.shadowCam;
        ShadowFrameContext.CascadeData cd = ctx.c[cascade];

        float radius = Math.max(cfg.minRadius, cd.radius);

        Matrix3f b = ctx.basis;
        b.getColumn(0, axisRight);
        b.getColumn(1, axisUp);
        b.getColumn(2, axisDir);

        // camera loc = center - dir * (radius * backOffset)
        camLoc.set(cd.centerWS);
        tmp.set(axisDir).multLocal(radius * cfg.backOffset);
        camLoc.subtractLocal(tmp);

        // near/far in light-space: radius along dir +/- cascade z-range
        float distToCenter = radius * cfg.backOffset;
        float near = distToCenter - cd.zNearRel;
        float far = distToCenter + cd.zFarRel;

        if (near < cfg.minNear) near = cfg.minNear;
        if (far <= near + cfg.minFarGap) far = near + cfg.minFarGap;

        sc.setParallelProjection(true);
        sc.setLocation(camLoc);

        axisRightNeg.set(axisRight).negateLocal();
        sc.setAxes(axisRightNeg, axisUp, axisDir);

        sc.setFrustum(near, far, -radius, radius, radius, -radius);
        sc.update();
    }

    @Override
    public String id() {
        return "ShadowCamPlacement";
    }

    public static final class Cfg {
        public float backOffset = 1.10f;
        public float minRadius = 1.0f;

        public float minNear = 0.1f;
        public float minFarGap = 0.1f;
    }
}
