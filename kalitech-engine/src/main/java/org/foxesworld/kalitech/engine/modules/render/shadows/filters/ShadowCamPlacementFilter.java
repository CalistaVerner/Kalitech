// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/ShadowCamPlacementFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

public final class ShadowCamPlacementFilter implements ShadowFilter {

    private final Cfg cfg = new Cfg();
    private final Vector3f axisRight = new Vector3f();
    private final Vector3f axisUp = new Vector3f();
    private final Vector3f axisDir = new Vector3f();
    private final Vector3f axisRightNeg = new Vector3f();
    private final Vector3f tmp = new Vector3f();

    @Override
    public String id() {
        return "ShadowCamPlacement";
    }

    public Cfg cfg() {
        return cfg;
    }

    @Override
    public void afterFit(ShadowFrameContext ctx, int cascade) {
        if (!cfg.enabled) return;
        if (ctx == null) return;

        Camera sc = ctx.shadowCam;
        if (sc == null) return;

        ShadowFrameContext.CascadeData cd = ctx.c[cascade];

        float radius = Math.max(cfg.minRadius, cd.radius);
        Vector3f center = cd.centerWS;

        Matrix3f b = ctx.basis;
        b.getColumn(0, axisRight);
        b.getColumn(1, axisUp);
        b.getColumn(2, axisDir);

        float backOffset = Math.max(0.5f, cfg.backOffset);

        Vector3f camLoc = tmp.set(axisDir).multLocal(-radius * backOffset).addLocal(center);

        float distToCenter = radius * backOffset;
        float near = distToCenter + cd.zNearRel;
        float far = distToCenter + cd.zFarRel;

        if (near < cfg.minNear) near = cfg.minNear;
        if (far <= near + cfg.minFarGap) far = near + cfg.minFarGap;

        sc.setParallelProjection(true);
        sc.setLocation(camLoc);

        axisRightNeg.set(-axisRight.x, -axisRight.y, -axisRight.z);
        sc.setAxes(axisRightNeg, axisUp, axisDir);

        sc.setFrustum(near, far, -radius, radius, radius, -radius);
        sc.update();
    }

    public static final class Cfg {
        public boolean enabled = true;

        public float backOffset = 1.10f;
        public float minNear = 1.0f;
        public float minFarGap = 0.001f;
        public float minRadius = 0.001f;
    }
}