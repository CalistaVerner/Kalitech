// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/FrustumSphereFitFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

public final class FrustumSphereFitFilter implements ShadowFilter {

    private final Cfg cfg = new Cfg();
    private final Vector3f camPos = new Vector3f();
    private final Vector3f camDir = new Vector3f();
    private final Vector3f camUp = new Vector3f();
    private final Vector3f camLeft = new Vector3f();
    private final Vector3f cn = new Vector3f();
    private final Vector3f cf = new Vector3f();
    private final Vector3f min = new Vector3f();
    private final Vector3f max = new Vector3f();
    private final Vector3f[] corners = new Vector3f[8];

    public FrustumSphereFitFilter() {
        for (int i = 0; i < corners.length; i++) corners[i] = new Vector3f();
    }

    @Override
    public String id() {
        return "FrustumSphereFit";
    }

    public Cfg cfg() {
        return cfg;
    }

    @Override
    public void afterFit(ShadowFrameContext ctx, int cascade) {
        if (!cfg.enabled) return;
        if (ctx == null) return;

        Camera vc = ctx.viewCam;
        if (vc == null) return;

        ShadowFrameContext.CascadeData cd = ctx.c[cascade];

        float sliceNear = cd.rangeNear;
        float sliceFar = cd.rangeFar;

        float fovY = vc.getFov();
        if (fovY > FastMath.PI) fovY = fovY * FastMath.DEG_TO_RAD;

        float tanY = FastMath.tan(0.5f * fovY);
        float tanX = tanY * vc.getAspect();

        camPos.set(vc.getLocation());
        camDir.set(vc.getDirection()).normalizeLocal();
        camUp.set(vc.getUp()).normalizeLocal();
        camLeft.set(vc.getLeft()).normalizeLocal();

        cn.set(camDir).multLocal(sliceNear).addLocal(camPos);
        cf.set(camDir).multLocal(sliceFar).addLocal(camPos);

        float nh = sliceNear * tanY;
        float nw = sliceNear * tanX;
        float fh = sliceFar * tanY;
        float fw = sliceFar * tanX;

        // near
        corners[0].set(cn).addLocal(camUp.x * nh + camLeft.x * nw, camUp.y * nh + camLeft.y * nw, camUp.z * nh + camLeft.z * nw);
        corners[1].set(cn).addLocal(camUp.x * nh - camLeft.x * nw, camUp.y * nh - camLeft.y * nw, camUp.z * nh - camLeft.z * nw);
        corners[2].set(cn).addLocal(-camUp.x * nh - camLeft.x * nw, -camUp.y * nh - camLeft.y * nw, -camUp.z * nh - camLeft.z * nw);
        corners[3].set(cn).addLocal(-camUp.x * nh + camLeft.x * nw, -camUp.y * nh + camLeft.y * nw, -camUp.z * nh + camLeft.z * nw);

        // far
        corners[4].set(cf).addLocal(camUp.x * fh + camLeft.x * fw, camUp.y * fh + camLeft.y * fw, camUp.z * fh + camLeft.z * fw);
        corners[5].set(cf).addLocal(camUp.x * fh - camLeft.x * fw, camUp.y * fh - camLeft.y * fw, camUp.z * fh - camLeft.z * fw);
        corners[6].set(cf).addLocal(-camUp.x * fh - camLeft.x * fw, -camUp.y * fh - camLeft.y * fw, -camUp.z * fh - camLeft.z * fw);
        corners[7].set(cf).addLocal(-camUp.x * fh + camLeft.x * fw, -camUp.y * fh + camLeft.y * fw, -camUp.z * fh + camLeft.z * fw);

        min.set(corners[0]);
        max.set(corners[0]);
        for (int i = 1; i < 8; i++) {
            Vector3f p = corners[i];
            if (p.x < min.x) min.x = p.x;
            if (p.y < min.y) min.y = p.y;
            if (p.z < min.z) min.z = p.z;
            if (p.x > max.x) max.x = p.x;
            if (p.y > max.y) max.y = p.y;
            if (p.z > max.z) max.z = p.z;
        }

        cd.centerWS.set(min).addLocal(max).multLocal(0.5f);

        float r = 0f;
        for (int i = 0; i < 8; i++) {
            float d = corners[i].distance(cd.centerWS);
            if (d > r) r = d;
        }

        cd.radius = Math.max(0.001f, r);
        cd.zNearRel = -cd.radius;
        cd.zFarRel = +cd.radius;
        cd.quantized = false;
        cd.snapped = false;
    }

    public static final class Cfg {
        public boolean enabled = true;
    }
}