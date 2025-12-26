package org.foxesworld.kalitech.engine.world.systems.camera;

// Author: Calista Verner

import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.impl.CameraState;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

public final class FirstPersonCameraController implements CameraController {

    private final LookController look;
    private final BodyPositionProvider bodyPos;

    private final Vector3f smoothed = new Vector3f();
    private final Vector3f body = new Vector3f();
    private final Vector3f target = new Vector3f();

    private boolean has = false;

    public float mouseDx, mouseDy;

    public FirstPersonCameraController(LookController look, BodyPositionProvider bodyPos) {
        this.look = look;
        this.bodyPos = bodyPos;
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

        if (!bodyPos.getBodyPosition(bodyId, body)) return;

        target.set(body).addLocal(s.firstPersonOffset);
        ctx.app().getCamera().setLocation(smooth(target, s.followSmoothing, tpf));
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
}