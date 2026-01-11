// FILE: org/foxesworld/kalitech/engine/modules/render/ShadowSnapper.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

/**
 * Texel snapping for directional shadow cameras.
 *
 * Fixes shimmering by:
 *  1) Stabilizing ortho extents (square frustum) so it doesn't "breathe"
 *     with view camera rotation (fit-to-scene-ish).
 *  2) Snapping camera position along Left/Up axes in world units per texel.
 */
public final class ShadowSnapper {

    private final int shadowMapSize;

    private final Vector3f tmpDir = new Vector3f();
    /**
     * If true, we force each shadow cam ortho frustum to be a stable square
     * (fit-to-scene-ish) so it doesn't "breathe" when the view camera rotates.
     */
    private boolean stabilizeExtents = true;

    private final Vector3f tmpLoc = new Vector3f();
    private final Vector3f tmpLeft = new Vector3f();
    private final Vector3f tmpUp = new Vector3f();
    private final Vector3f delta = new Vector3f();
    /**
     * Extra padding applied when stabilizing extents. 1.0 = no padding.
     * Use small values like 1.05..1.20 if you still see edge popping.
     */
    private float extentsPadding = 1.12f;

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

    private static float roundToStep(float v, float step) {
        if (!(step > 0f)) return v;
        return Math.round(v / step) * step;
    }

    public void snap(Camera shadowCam) {
        if (shadowCam == null) throw new IllegalArgumentException("[render][shadow] shadowCam is null");

        // Works only for parallel (ortho) cameras
        if (!shadowCam.isParallelProjection()) return;

        float left = shadowCam.getFrustumLeft();
        float right = shadowCam.getFrustumRight();
        float bottom = shadowCam.getFrustumBottom();
        float top = shadowCam.getFrustumTop();

        float width = right - left;
        float height = top - bottom;

        if (!(width > 0f) || !(height > 0f)) return;

        // ------------------------------------------------------------
        // 1) Stabilize ortho extents so they don't "breathe" with camera rotation
        //    (fit-to-scene-ish square).
        // ------------------------------------------------------------
        if (stabilizeExtents) {
            final float cx = (left + right) * 0.5f;
            final float cy = (bottom + top) * 0.5f;

            final float size = Math.max(width, height) * extentsPadding;
            final float half = size * 0.5f;

            left = cx - half;
            right = cx + half;
            bottom = cy - half;
            top = cy + half;

            width = right - left;
            height = top - bottom;

            // Camera#setFrustum signature is (near, far, left, right, top, bottom)
            shadowCam.setFrustum(
                    shadowCam.getFrustumNear(),
                    shadowCam.getFrustumFar(),
                    left,
                    right,
                    top,
                    bottom
            );
        }

        // After stabilization we expect square frustum, so texel size is uniform.
        final float texel = Math.max(width, height) / (float) shadowMapSize;

        // World-space camera basis vectors
        tmpLeft.set(shadowCam.getLeft());
        tmpUp.set(shadowCam.getUp());

        // Current camera position
        tmpLoc.set(shadowCam.getLocation());

        // Project camera position onto left/up axes
        final float x = tmpLoc.dot(tmpLeft);
        final float y = tmpLoc.dot(tmpUp);

        // Snap to nearest texel step (ROUND, not FLOOR, for minimal drift)
        final float sx = roundToStep(x, texel);
        final float sy = roundToStep(y, texel);

        final float dx = sx - x;
        final float dy = sy - y;

        if (dx == 0f && dy == 0f) return;

        // IMPORTANT: do not mutate tmpUp while building delta
        delta.set(tmpLeft).multLocal(dx);
        tmpDir.set(tmpUp).multLocal(dy);
        delta.addLocal(tmpDir);

        tmpLoc.addLocal(delta);
        shadowCam.setLocation(tmpLoc);

        shadowCam.update();
    }
}