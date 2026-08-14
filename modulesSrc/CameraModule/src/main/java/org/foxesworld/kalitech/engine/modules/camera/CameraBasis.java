/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.FastMath
 */
package org.foxesworld.kalitech.engine.modules.camera;

import com.jme3.math.FastMath;
import org.foxesworld.kalitech.engine.modules.camera.Vec3View;

public final class CameraBasis {
    private CameraBasis() {
    }

    public static void forward(float yaw, float pitch, Vec3View out) {
        float cp = FastMath.cos((float)pitch);
        float sp = FastMath.sin((float)pitch);
        float cy = FastMath.cos((float)yaw);
        float sy = FastMath.sin((float)yaw);
        float fx = -sy * cp;
        float fy = sp;
        float fz = -cy * cp;
        out.set(fx, fy, fz);
    }

    public static void right(float yaw, Vec3View out) {
        float cy = FastMath.cos((float)yaw);
        float sy = FastMath.sin((float)yaw);
        float rx = cy;
        float ry = 0.0f;
        float rz = -sy;
        out.set(rx, 0.0f, rz);
    }

    public static void up(float yaw, float pitch, Vec3View out) {
        float cp = FastMath.cos((float)pitch);
        float sp = FastMath.sin((float)pitch);
        float cy = FastMath.cos((float)yaw);
        float sy = FastMath.sin((float)yaw);
        float fx = -sy * cp;
        float fy = sp;
        float fz = -cy * cp;
        float rx = cy;
        float ry = 0.0f;
        float rz = -sy;
        float ux = 0.0f * fz - rz * fy;
        float uy = rz * fx - rx * fz;
        float uz = rx * fy - 0.0f * fx;
        out.set(ux, uy, uz);
    }
}

