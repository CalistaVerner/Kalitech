package org.foxesworld.kalitech.engine.world.systems.camera;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.impl.CameraState;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

public final class FlyCameraController {

    private final Vector3f velocity = new Vector3f();
    private final Vector3f velSmoothed = new Vector3f();

    private float yaw = 0f, pitch = 0f;
    private float yawS = 0f, pitchS = 0f;

    public void reset() {
        velocity.set(0, 0, 0);
        velSmoothed.set(0, 0, 0);
        yaw = pitch = yawS = pitchS = 0f;
    }

    public void updateRotation(SystemContext ctx, CameraState s, Vector2f look, float tpf) {
        var cam = ctx.app().getCamera();
        applyRotation(cam, s, look, tpf);
    }

    public void update(SystemContext ctx, CameraState s, Vector3f move, Vector2f look, float tpf) {
        var cam = ctx.app().getCamera();

        applyRotation(cam, s, look, tpf);

        Vector3f dir = new Vector3f(move.x, move.y, move.z);
        if (s.invertMoveX) dir.x = -dir.x;
        if (s.invertMoveZ) dir.z = -dir.z;
        if (dir.lengthSquared() > 0f) dir.normalizeLocal();

        Vector3f forward = cam.getDirection().clone().normalizeLocal();
        Vector3f right   = cam.getLeft().clone().negateLocal().normalizeLocal();
        Vector3f up      = cam.getUp().clone().normalizeLocal();

        Vector3f desired = new Vector3f();
        desired.addLocal(right.mult(dir.x));
        desired.addLocal(up.mult(dir.y));
        desired.addLocal(forward.mult(dir.z));
        if (desired.lengthSquared() > 0f) desired.normalizeLocal();

        float maxSpeed = (float) s.speed;
        Vector3f targetVel = desired.mult(maxSpeed);

        float accel = (float) s.accel;
        float a = 1f - (float) Math.exp(-accel * tpf);
        velocity.interpolateLocal(targetVel, a);

        float drag = (float) s.drag;
        float d = (float) Math.exp(-drag * tpf);
        velocity.multLocal(d);

        float sm = (float) s.smoothing;
        float velLerp = smoothLerp(sm, tpf);
        velSmoothed.interpolateLocal(velocity, velLerp);

        Vector3f pos = cam.getLocation().clone();
        pos.addLocal(velSmoothed.x * tpf, velSmoothed.y * tpf, velSmoothed.z * tpf);
        cam.setLocation(pos);
    }

    public float yaw()   { return yawS; }
    public float pitch() { return pitchS; }

    private void applyRotation(com.jme3.renderer.Camera cam, CameraState s, Vector2f look, float tpf) {
        float sens = (float) s.lookSensitivity;
        float dx = look.x * sens * (s.invertX ? 1f : -1f);
        float dy = look.y * sens * (s.invertY ? 1f : -1f);

        yaw += dx;
        pitch = clamp(pitch + dy, -FastMath.HALF_PI * 0.98f, FastMath.HALF_PI * 0.98f);

        float sm = (float) s.smoothing;
        float rotLerp = smoothLerp(sm, tpf);
        yawS = lerpAngle(yawS, yaw, rotLerp);
        pitchS = lerp(pitchS, pitch, rotLerp);

        Quaternion q = new Quaternion().fromAngles(pitchS, yawS, 0f);
        cam.setRotation(q);
    }

    private static float smoothLerp(float smoothing, float tpf) {
        float s = clamp(smoothing, 0f, 1f);
        float k = 1f - s;
        return 1f - (float) Math.exp(-k * 30f * tpf);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float lerpAngle(float a, float b, float t) {
        float diff = wrapAngle(b - a);
        return a + diff * t;
    }

    private static float wrapAngle(float a) {
        while (a > FastMath.PI)  a -= FastMath.TWO_PI;
        while (a < -FastMath.PI) a += FastMath.TWO_PI;
        return a;
    }
}