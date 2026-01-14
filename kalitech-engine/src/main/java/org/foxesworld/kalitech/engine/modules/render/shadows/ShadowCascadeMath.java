// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/ShadowCascadeMath.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.modules.render.shadows;

import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

/**
 * ShadowCascadeMath (self-contained, NO Ardor3D)
 * <p>
 * Computes cascade ortho bounds in LIGHT space for a view camera frustum split [splitNear..splitFar].
 * Hot path: no allocations.
 */
public final class ShadowCascadeMath {

    private final Vector3f camLeft = new Vector3f();
    private final Vector3f camUp = new Vector3f();
    private final Vector3f camDir = new Vector3f();
    private final Vector3f tmpA = new Vector3f();
    private final Vector3f tmpB = new Vector3f();
    private final Vector3f tmpC = new Vector3f();
    private final Vector3f[] cornersW = new Vector3f[]{
            new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(),
            new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()
    };
    private final Matrix4f lightView = new Matrix4f();

    public void fitSplit(Camera viewCam, float splitNear, float splitFar, Vector3f lightDirNormalized, CascadeFit out) {
        if (viewCam == null || lightDirNormalized == null || out == null) return;

        final float camNear = viewCam.getFrustumNear();
        final float camFar = viewCam.getFrustumFar();

        if (!(splitNear > 0f)) splitNear = Math.max(0.01f, camNear);
        if (!(splitFar > splitNear)) splitFar = Math.min(camFar, splitNear + 0.01f);

        buildSplitCornersWorld(viewCam, splitNear, splitFar);

        // center of split (world)
        final Vector3f c = out.centerWorld;
        c.set(0, 0, 0);
        for (int i = 0; i < 8; i++) c.addLocal(cornersW[i]);
        c.multLocal(1f / 8f);

        // robust basis
        final Vector3f fwd = tmpA.set(lightDirNormalized);
        if (fwd.lengthSquared() > 0f) fwd.normalizeLocal();
        else fwd.set(0, -1, 0);

        final Vector3f refUp = (FastMath.abs(fwd.y) > 0.98f) ? tmpB.set(Vector3f.UNIT_Z) : tmpB.set(Vector3f.UNIT_Y);

        final Vector3f right = tmpC.set(refUp).crossLocal(fwd);
        final float rLen2 = right.lengthSquared();
        if (rLen2 < 1e-12f) right.set(1, 0, 0);
        else right.multLocal(FastMath.invSqrt(rLen2));

        final Vector3f up = camUp.set(fwd).crossLocal(right);
        final float uLen2 = up.lengthSquared();
        if (uLen2 < 1e-12f) up.set(0, 1, 0);
        else up.multLocal(FastMath.invSqrt(uLen2));

        final float rx = right.x, ry = right.y, rz = right.z;
        final float ux = up.x, uy = up.y, uz = up.z;
        final float fx = fwd.x, fy = fwd.y, fz = fwd.z;

        final float cx = c.x, cy = c.y, cz = c.z;

        lightView.set(
                rx, ry, rz, -(rx * cx + ry * cy + rz * cz),
                ux, uy, uz, -(ux * cx + uy * cy + uz * cz),
                fx, fy, fz, -(fx * cx + fy * cy + fz * cz),
                0f, 0f, 0f, 1f
        );

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < 8; i++) {
            final Vector3f p = cornersW[i];

            // compute in double for stability, cast down once
            final double x = p.x, y = p.y, z = p.z;

            final float lx = (float) (lightView.m00 * x + lightView.m01 * y + lightView.m02 * z + lightView.m03);
            final float ly = (float) (lightView.m10 * x + lightView.m11 * y + lightView.m12 * z + lightView.m13);
            final float lz = (float) (lightView.m20 * x + lightView.m21 * y + lightView.m22 * z + lightView.m23);

            if (lx < minX) minX = lx;
            if (ly < minY) minY = ly;
            if (lz < minZ) minZ = lz;

            if (lx > maxX) maxX = lx;
            if (ly > maxY) maxY = ly;
            if (lz > maxZ) maxZ = lz;
        }

        out.left = minX;
        out.right = maxX;
        out.bottom = minY;
        out.top = maxY;
        out.minZ = minZ;
        out.maxZ = maxZ;
    }

    private void buildSplitCornersWorld(Camera cam, float splitNear, float splitFar) {
        // Your camera contract: getFov()
        float fov = cam.getFov();
        final float fovY = (fov > 3.2f) ? (fov * FastMath.DEG_TO_RAD) : fov; // auto deg/rad
        final float tan = FastMath.tan(0.5f * fovY);

        final float aspect;
        if (cam.getHeight() > 0) aspect = (float) cam.getWidth() / (float) cam.getHeight();
        else aspect = cam.getAspect();

        final float nh = tan * splitNear;
        final float nw = nh * aspect;

        final float fh = tan * splitFar;
        final float fw = fh * aspect;

        cam.getLeft(camLeft);
        cam.getUp(camUp);
        cam.getDirection(camDir);

        final Vector3f loc = cam.getLocation();

        setCornerWorld(0, loc, -nw, +nh, splitNear);
        setCornerWorld(1, loc, +nw, +nh, splitNear);
        setCornerWorld(2, loc, +nw, -nh, splitNear);
        setCornerWorld(3, loc, -nw, -nh, splitNear);

        setCornerWorld(4, loc, -fw, +fh, splitFar);
        setCornerWorld(5, loc, +fw, +fh, splitFar);
        setCornerWorld(6, loc, +fw, -fh, splitFar);
        setCornerWorld(7, loc, -fw, -fh, splitFar);
    }

    private void setCornerWorld(int idx, Vector3f camLoc, float xLeft, float yUp, float dist) {
        final Vector3f p = cornersW[idx];
        p.set(camLoc);

        p.x += camLeft.x * xLeft;
        p.y += camLeft.y * xLeft;
        p.z += camLeft.z * xLeft;

        p.x += camUp.x * yUp;
        p.y += camUp.y * yUp;
        p.z += camUp.z * yUp;

        p.x += camDir.x * dist;
        p.y += camDir.y * dist;
        p.z += camDir.z * dist;
    }

    public static final class CascadeFit {
        public final Vector3f centerWorld = new Vector3f();
        public float left, right, bottom, top;
        public float minZ, maxZ;
    }
}