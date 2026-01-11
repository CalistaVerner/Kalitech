// FILE: org/foxesworld/kalitech/engine/modules/render/ShadowSnapper.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

/**
 * Texel snapping for directional shadow cameras.
 * <p>
 * Idea:
 * - Shadow cameras are orthographic (parallel projection).
 * - We quantize (snap) the camera position along its Left/Up axes
 * with step = frustumSize / shadowMapSize, so the shadow projection
 * becomes stable relative to the world and does not shimmer.
 * <p>
 * This is "always-on snapping" (no thresholds, no smoothing, no gating),
 * so it stays smooth while still stable.
 */
public final class ShadowSnapper {

    private final int shadowMapSize;

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

    private static float roundToStep(float v, float step) {
        if (!(step > 0f)) return v;
        return Math.round(v / step) * step;
    }

    public void snap(Camera shadowCam) {
        if (shadowCam == null) throw new IllegalArgumentException("[render][shadow] shadowCam is null");

        // Works only for parallel (ortho) cameras
        if (!shadowCam.isParallelProjection()) return;

        final float left = shadowCam.getFrustumLeft();
        final float right = shadowCam.getFrustumRight();
        final float bottom = shadowCam.getFrustumBottom();
        final float top = shadowCam.getFrustumTop();

        final float width = right - left;
        final float height = top - bottom;

        if (!(width > 0f) || !(height > 0f)) return;

        final float texelX = width / (float) shadowMapSize;
        final float texelY = height / (float) shadowMapSize;

        // World-space camera basis vectors
        tmpLeft.set(shadowCam.getLeft());
        tmpUp.set(shadowCam.getUp());

        // Current camera position
        tmpLoc.set(shadowCam.getLocation());

        // Project camera position onto left/up axes
        final float x = tmpLoc.dot(tmpLeft);
        final float y = tmpLoc.dot(tmpUp);

        // Snap to nearest texel step (ROUND, not FLOOR, for minimal drift)
        final float sx = roundToStep(x, texelX);
        final float sy = roundToStep(y, texelY);

        final float dx = sx - x;
        final float dy = sy - y;

        if (dx == 0f && dy == 0f) return;

        delta.set(tmpLeft).multLocal(dx).addLocal(tmpUp.mult(dy));

        tmpLoc.addLocal(delta);
        shadowCam.setLocation(tmpLoc);

        // Ensure matrices update
        shadowCam.update();
    }
}