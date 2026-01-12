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

    /**
     * Quantize a value down to the nearest multiple of {@code step}.
     *
     * <p>
     * Snapping shadow camera movement must be conservative: rounding to the
     * nearest texel as in the original implementation can push the camera in
     * either direction, which may cause the orthographic frustum to no longer
     * fully encompass the visible slice of the view frustum. This can lead to
     * shadows detaching from their casters (“peter panning”) or objects falling
     * outside of the shadow map coverage. To avoid this, we always snap
     * <em>downwards</em> (i.e. toward negative infinity) using {@code floor()}.
     * This approach matches the technique described by Valient and widely used
     * by CD Projekt Red for stable cascaded shadow maps【115644872280442†L51-L71】.
     *
     * @param v    the value to quantize (e.g. the projection of the camera
     *             position onto an axis)
     * @param step the size of one texel in world units
     * @return the quantized value that is a multiple of {@code step}, or
     * {@code v} if {@code step} is not positive
     */
    private static float snapDownToStep(float v, float step) {
        if (!(step > 0f)) return v;
        return (float) Math.floor(v / step) * step;
    }

    public void snap(Camera shadowCam) {
        if (shadowCam == null) throw new IllegalArgumentException("[render][shadow] shadowCam is null");

        // Works only for parallel (ortho) cameras
        if (!shadowCam.isParallelProjection()) return;

        final float left = shadowCam.getFrustumLeft();
        final float right = shadowCam.getFrustumRight();
        final float bottom = shadowCam.getFrustumBottom();
        final float top = shadowCam.getFrustumTop();

        // Compute the size of the orthographic projection.  JME defines the
        // frustum edges relative to the camera’s centre.  Because the PSSM
        // implementation always builds a square projection volume (based on a
        // bounding sphere)【115644872280442†L51-L71】, we conservatively choose the
        // maximum of width and height here.  Using a unified texel size for
        // both axes prevents aspect ratio differences from causing sub‑pixel
        // drift.
        final float width = right - left;
        final float height = top - bottom;
        if (!(width > 0f) || !(height > 0f)) return;
        final float size = Math.max(width, height);
        final float texel = size / (float) shadowMapSize;

        // World-space camera basis vectors
        tmpLeft.set(shadowCam.getLeft());
        tmpUp.set(shadowCam.getUp());

        // Current camera position
        tmpLoc.set(shadowCam.getLocation());

        // Project camera position onto left/up axes
        // Project the camera position onto the left and up axes.  This gives
        // world‑space coordinates measured along those axes relative to the
        // world origin.  We do not offset by half the frustum dimensions here
        // because the camera location already lies at the centre of the
        // orthographic projection.
        final float x = tmpLoc.dot(tmpLeft);
        final float y = tmpLoc.dot(tmpUp);

        // Snap down to the nearest integer multiple of the texel size.  Using
        // floor() instead of round() ensures the projection bounds only ever
        // contract.  This avoids sub‑texel expansion that could otherwise
        // offset shadows away from their casters when the camera moves【115644872280442†L51-L71】.
        final float sx = snapDownToStep(x, texel);
        final float sy = snapDownToStep(y, texel);

        final float dx = sx - x;
        final float dy = sy - y;

        // Only apply an offset when needed.  If the camera is already aligned to
        // the texel grid no work is performed.  Otherwise translate the
        // camera along its left and up axes by the computed deltas.  Note
        // that multLocal(dx) modifies the temporary vector in place, so we
        // construct the final offset by first scaling the left axis and then
        // accumulating the up contribution.
        if (dx == 0f && dy == 0f) return;
        delta.set(tmpLeft).multLocal(dx).addLocal(tmpUp.mult(dy));
        tmpLoc.addLocal(delta);
        shadowCam.setLocation(tmpLoc);
        // Force the camera’s view and projection matrices to update.  Without
        // this call the downstream shadow renderer may continue using stale
        // matrices, leading to shimmering or misaligned shadows.
        shadowCam.update();
    }
}