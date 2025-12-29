package org.foxesworld.kalitech.engine.api.impl.camera;

import com.jme3.math.FastMath;

/**
 * Cheap camera basis derived from yaw/pitch (roll = 0).
 * Matches the convention used by jME Quaternion.fromAngles(pitch, yaw, roll).
 */
public final class CameraBasis {

    private CameraBasis() {}

    public static void forward(float yaw, float pitch, Vec3View out) {
        final float cp = FastMath.cos(pitch);
        final float sp = FastMath.sin(pitch);
        final float cy = FastMath.cos(yaw);
        final float sy = FastMath.sin(yaw);

        final float fx = -sy * cp;
        final float fy = sp;
        final float fz = -cy * cp;

        out.set(fx, fy, fz);
    }

    /** For roll=0 typical FPS: right depends only on yaw. */
    public static void right(float yaw, Vec3View out) {
        final float cy = FastMath.cos(yaw);
        final float sy = FastMath.sin(yaw);

        final float rx = cy;
        final float ry = 0f;
        final float rz = -sy;

        out.set(rx, ry, rz);
    }

    public static void up(float yaw, float pitch, Vec3View out) {
        // up = cross(right, forward)
        final float cp = FastMath.cos(pitch);
        final float sp = FastMath.sin(pitch);
        final float cy = FastMath.cos(yaw);
        final float sy = FastMath.sin(yaw);

        final float fx = -sy * cp;
        final float fy = sp;
        final float fz = -cy * cp;

        final float rx = cy;
        final float ry = 0f;
        final float rz = -sy;

        final float ux = (ry * fz) - (rz * fy);
        final float uy = (rz * fx) - (rx * fz);
        final float uz = (rx * fy) - (ry * fx);

        out.set(ux, uy, uz);
    }
}