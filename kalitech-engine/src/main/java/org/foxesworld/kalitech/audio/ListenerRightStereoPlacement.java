package org.foxesworld.kalitech.audio;

import com.jme3.math.Vector3f;

/**
 * Places L/R along listener's right vector (dir x up).
 * This keeps stereo base perceptually stable as the camera turns.
 */
public final class ListenerRightStereoPlacement implements StereoPlacementStrategy {

    @Override
    public Result compute(ListenerSnapshot listener, Vector3f sourceWorldPos, StereoConfig cfg) {
        Vector3f dir = listener.forward();
        Vector3f up = listener.up();

        Vector3f right = dir.cross(up);
        if (!StereoMath.isFinite(right) || right.lengthSquared() < 1e-8f) {
            right = Vector3f.UNIT_X.clone();
        } else {
            right.normalizeLocal();
        }
        return new Result(right);
    }
}