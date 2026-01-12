// FILE: org/foxesworld/kalitech/engine/modules/render/ShadowSnapper.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

public final class ShadowSnapper {

    private final Vector3f tmpDir = new Vector3f();

    private final int shadowMapSize;
    /**
     * When {@code true}, this snapper will expand the orthographic frustum of
     * each shadow camera to a square based on the diagonal of the current
     * frustum extents. This approximates the CD Projekt RED approach to
     * stable cascaded shadow maps: the rectangular bounds are enclosed in
     * a circle (diameter = sqrt(width² + height²)), which is then used to
     * define a square projection. Without this, the orthographic extents can
     * change with camera orientation causing shimmering and “breathing”.
     */
    private boolean stabilizeExtents = true;
    /**
     * Padding multiplier applied to the computed diameter of the bounding
     * circle. A value of 1.0 uses the exact diagonal; values above 1.0
     * enlarge the extents slightly to compensate for numerical precision or
     * imperfect fitting. Must be ≥ 1.0.
     */
    private float extentsPadding = 1.05f;

    private static float roundToStep(float v, float step) {
        if (!(step > 0f)) return v;
        // snap downward to the nearest multiple of 'step'. Using floor instead of round
        // avoids oscillating between adjacent texel cells which can cause peter‑panning.
        return (float) Math.floor((double) v / (double) step) * step;
    }

    private final Vector3f tmpLoc = new Vector3f();
    private final Vector3f tmpLeft = new Vector3f();
    private final Vector3f tmpUp = new Vector3f();
    private final Vector3f delta = new Vector3f();

    public ShadowSnapper(int shadowMapSize) {
        if (shadowMapSize <= 0) {
            throw new IllegalArgumentException("[render][shadow] shadowMapSize must be > 0");
        }
        this.shadowMapSize = shadowMapSize;
    }

    public boolean isStabilizeExtents() {
        return stabilizeExtents;
    }

    public void setStabilizeExtents(boolean stabilizeExtents) {
        this.stabilizeExtents = stabilizeExtents;
    }

    public float getExtentsPadding() {
        return extentsPadding;
    }

    public void setExtentsPadding(float extentsPadding) {
        if (!(extentsPadding >= 1.0f)) {
            throw new IllegalArgumentException("[render][shadow] extentsPadding must be >= 1.0");
        }
        this.extentsPadding = extentsPadding;
    }

    public void snap(Camera shadowCam) {
        snap(shadowCam, null);
    }

    public void snap(Camera shadowCam, SnapDebug dbg) {
        if (shadowCam == null) throw new IllegalArgumentException("[render][shadow] shadowCam is null");
        if (!shadowCam.isParallelProjection()) return;

        float left = shadowCam.getFrustumLeft();
        float right = shadowCam.getFrustumRight();
        float bottom = shadowCam.getFrustumBottom();
        float top = shadowCam.getFrustumTop();

        float width = right - left;
        float height = top - bottom;

        if (!(width > 0f) || !(height > 0f)) return;

        // 1) Stabilize extents using bounding circle of current extents
        final boolean didStabilize = stabilizeExtents;
        if (stabilizeExtents) {
            // centre of current orthographic bounds
            final float cx = (left + right) * 0.5f;
            final float cy = (bottom + top) * 0.5f;
            // compute the diagonal length of the rectangular extents (diameter of the minimal
            // circle enclosing the rectangle) and apply padding
            float diag = (float) Math.sqrt((double) width * (double) width + (double) height * (double) height);
            float size = diag * extentsPadding;
            float half = size * 0.5f;
            // build square extents centred on (cx,cy)
            left = cx - half;
            right = cx + half;
            bottom = cy - half;
            top = cy + half;
            width = right - left;
            height = top - bottom;
            // apply the modified extents
            shadowCam.setFrustum(
                    shadowCam.getFrustumNear(),
                    shadowCam.getFrustumFar(),
                    left, right, top, bottom
            );
        }

        // uniform texel size after stabilization
        final float texel = Math.max(width, height) / (float) shadowMapSize;

        tmpLeft.set(shadowCam.getLeft());
        tmpUp.set(shadowCam.getUp());
        tmpLoc.set(shadowCam.getLocation());

        final float x = tmpLoc.dot(tmpLeft);
        final float y = tmpLoc.dot(tmpUp);

        final float sx = roundToStep(x, texel);
        final float sy = roundToStep(y, texel);

        final float dx = sx - x;
        final float dy = sy - y;

        if (dbg != null) {
            dbg.texel = texel;
            dbg.x = x;
            dbg.y = y;
            dbg.sx = sx;
            dbg.sy = sy;
            dbg.dx = dx;
            dbg.dy = dy;
            dbg.width = width;
            dbg.height = height;
            dbg.stabilized = didStabilize;
        }

        if (dx == 0f && dy == 0f) return;

        delta.set(tmpLeft).multLocal(dx);
        tmpDir.set(tmpUp).multLocal(dy);
        delta.addLocal(tmpDir);

        tmpLoc.addLocal(delta);
        shadowCam.setLocation(tmpLoc);
        shadowCam.update();
    }

    /**
     * Optional debug snapshot filled by snap(Camera, SnapDebug).
     * Allocate only when you actually want to log to avoid per-frame garbage.
     */
    public static final class SnapDebug {
        public float texel;
        public float x, y;
        public float sx, sy;
        public float dx, dy;
        public float width, height;
        public boolean stabilized;
    }
}