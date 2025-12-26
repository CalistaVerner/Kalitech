package org.foxesworld.kalitech.engine.world.systems.camera;

// Author: Calista Verner

import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.impl.CameraState;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

public final class ThirdPersonCameraController implements CameraController {

    public interface RaycastProvider {
        float rayFraction(Vector3f from, Vector3f to); // 1=no hit
    }

    private final LookController look;
    private final BodyPositionProvider bodyPos;
    private final RaycastProvider raycast;

    private final Vector3f smoothed = new Vector3f();
    private final Vector3f body = new Vector3f();
    private final Vector3f pivot = new Vector3f();
    private final Vector3f desired = new Vector3f();

    private boolean has = false;

    public float mouseDx, mouseDy;
    public float wheelDelta = 0f;

    public ThirdPersonCameraController(LookController look, BodyPositionProvider bodyPos, RaycastProvider raycast) {
        this.look = look;
        this.bodyPos = bodyPos;
        this.raycast = raycast;
    }

    @Override
    public void onEnter(SystemContext ctx, CameraState s) {
        has = false;
    }

    @Override
    public void update(SystemContext ctx, CameraState s, float tpf) {
        if (ctx == null || ctx.app() == null || s == null) return;

        look.update(ctx, s, mouseDx, mouseDy, tpf);

        int bodyId = s.followBodyId;
        if (bodyId <= 0 || bodyPos == null) return;

        if (wheelDelta != 0f) {
            float dist = s.thirdPersonDistance - wheelDelta * s.zoomSpeed;
            s.thirdPersonDistance = clamp(dist, s.thirdPersonMinDistance, s.thirdPersonMaxDistance);
        }

        if (!bodyPos.getBodyPosition(bodyId, body)) return;

        float yaw = look.yaw();
        float sin = (float) Math.sin(yaw);
        float cos = (float) Math.cos(yaw);

        float bx = -sin * s.thirdPersonDistance;
        float bz = -cos * s.thirdPersonDistance;

        float rx =  cos * s.thirdPersonSide;
        float rz = -sin * s.thirdPersonSide;

        pivot.set(body.x, body.y + s.thirdPersonHeight, body.z);
        desired.set(pivot.x + bx + rx, pivot.y, pivot.z + bz + rz);

        if (s.thirdPersonCollision && raycast != null) {
            float frac = raycast.rayFraction(pivot, desired);
            if (frac < 1f) {
                float safe = Math.max(0f, frac - (s.collisionPadding / Math.max(s.thirdPersonDistance, 0.001f)));
                desired.interpolateLocal(pivot, 1f - safe);
            }
        }

        ctx.app().getCamera().setLocation(smooth(desired, s.followSmoothing, tpf));
        wheelDelta = 0f;
    }

    private Vector3f smooth(Vector3f target, float smoothing, float tpf) {
        if (!has) {
            has = true;
            smoothed.set(target);
            return smoothed;
        }
        float s = Math.max(0f, Math.min(1f, smoothing));
        float k = 1f - s;
        float a = 1f - (float) Math.exp(-k * 30f * tpf);
        smoothed.interpolateLocal(target, a);
        return smoothed;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}