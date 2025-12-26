package org.foxesworld.kalitech.engine.world.systems.camera;

// Author: Calista Verner

import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.impl.CameraState;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

public final class FreeCameraController implements CameraController {

    private final LookController look;

    // velocity integration
    private final Vector3f vel = new Vector3f();
    private final Vector3f velS = new Vector3f();

    // temps (no allocations per frame)
    private final Vector3f dir = new Vector3f();
    private final Vector3f forward = new Vector3f();
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f desired = new Vector3f();
    private final Vector3f targetVel = new Vector3f();
    private final Vector3f pos = new Vector3f();

    // set by CameraSystem each tick
    public float mouseDx, mouseDy;
    public float moveX, moveY, moveZ;

    public FreeCameraController(LookController look) {
        this.look = look;
    }

    @Override
    public void update(SystemContext ctx, CameraState s, float tpf) {
        if (ctx == null || ctx.app() == null || s == null) return;

        look.update(ctx, s, mouseDx, mouseDy, tpf);

        dir.set(moveX, moveY, moveZ);
        if (s.invertMoveX) dir.x = -dir.x;
        if (s.invertMoveZ) dir.z = -dir.z;
        if (dir.lengthSquared() > 0f) dir.normalizeLocal();

        var cam = ctx.app().getCamera();

        forward.set(cam.getDirection()).normalizeLocal();
        right.set(cam.getLeft()).negateLocal().normalizeLocal();
        up.set(cam.getUp()).normalizeLocal();

        desired.set(0, 0, 0)
                .addLocal(right.x * dir.x, right.y * dir.x, right.z * dir.x)
                .addLocal(up.x * dir.y, up.y * dir.y, up.z * dir.y)
                .addLocal(forward.x * dir.z, forward.y * dir.z, forward.z * dir.z);

        if (desired.lengthSquared() > 0f) desired.normalizeLocal();

        float maxSpeed = (float) s.speed;
        targetVel.set(desired).multLocal(maxSpeed);

        float accel = (float) s.accel;
        float a = 1f - (float) Math.exp(-accel * tpf);
        vel.interpolateLocal(targetVel, a);

        float drag = (float) s.drag;
        float d = (float) Math.exp(-drag * tpf);
        vel.multLocal(d);

        float lerp = smoothLerp((float) s.smoothing, tpf);
        velS.interpolateLocal(vel, lerp);

        pos.set(cam.getLocation()).addLocal(velS.x * tpf, velS.y * tpf, velS.z * tpf);
        cam.setLocation(pos);
    }

    private static float smoothLerp(float smoothing, float tpf) {
        float s = Math.max(0f, Math.min(1f, smoothing));
        float k = 1f - s;
        return 1f - (float) Math.exp(-k * 30f * tpf);
    }
}