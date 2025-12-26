package org.foxesworld.kalitech.engine.world.systems.camera;

// Author: Calista Verner

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import org.foxesworld.kalitech.engine.api.impl.CameraState;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

public final class LookController {

    private float yaw = 0f, pitch = 0f;
    private float yawS = 0f, pitchS = 0f;

    private final Quaternion tmpRot = new Quaternion();

    public void reset() {
        yaw = pitch = yawS = pitchS = 0f;
    }

    public float yaw() { return yawS; }
    public float pitch() { return pitchS; }

    public void update(SystemContext ctx, CameraState s, float mouseDx, float mouseDy, float tpf) {
        if (ctx == null || ctx.app() == null) return;
        if (s == null) return;

        final float sens = (float) s.lookSensitivity;

        // Convention:
        //  - positive dx => yaw to the right
        //  - positive dy => look up (unless invertY)
        float dx = mouseDx * sens * (s.invertX ? -1f : 1f);
        float dy = mouseDy * sens * (s.invertY ? -1f : 1f);

        yaw += dx;
        pitch = clamp(pitch + dy, -FastMath.HALF_PI * 0.98f, FastMath.HALF_PI * 0.98f);

        float rotLerp = smoothLerp((float) s.smoothing, tpf);
        yawS = lerpAngle(yawS, yaw, rotLerp);
        pitchS = lerp(pitchS, pitch, rotLerp);

        tmpRot.fromAngles(pitchS, yawS, 0f);
        ctx.app().getCamera().setRotation(tmpRot);
    }

    private static float smoothLerp(float smoothing, float tpf) {
        float s = clamp(smoothing, 0f, 1f);
        float k = 1f - s;
        return 1f - (float) Math.exp(-k * 30f * tpf);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float lerpAngle(float a, float b, float t) {
        float diff = wrapAngle(b - a);
        return a + diff * t;
    }

    private static float wrapAngle(float a) {
        while (a > FastMath.PI) a -= FastMath.TWO_PI;
        while (a < -FastMath.PI) a += FastMath.TWO_PI;
        return a;
    }
}