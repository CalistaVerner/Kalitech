// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/PhysicsBodyStateTracker.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime;

import com.jme3.bullet.control.RigidBodyControl;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.modules.physics.BodyState;
import org.foxesworld.kalitech.engine.modules.physics.BodyStateStore;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsService;
import org.graalvm.polyglot.proxy.ProxyObject;

import static org.foxesworld.kalitech.engine.modules.physics.PhysicsJs.*;

public final class PhysicsBodyStateTracker {

    private static final float MOVE_POS_EPS = 0.0025f;
    private static final float MOVE_ROT_EPS = 0.0010f;
    private static final float MOVE_VEL_EPS = 0.01f;
    private static final int MOVE_EVENT_MAX_PER_STEP = 512;

    private final PhysicsService svc;
    private final Logger log;
    private final BodyStateStore store = new BodyStateStore(2048);

    public PhysicsBodyStateTracker(PhysicsService svc, Logger log) {
        this.svc = svc;
        this.log = log;
    }

    private static boolean safeActive(RigidBodyControl rb) {
        try {
            return rb.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void onBodyRemoved(int bodyId) {
        store.remove(bodyId);
    }

    public void clear() {
        store.clear();
    }

    public void emit(long step, float dt) {
        int emitted = 0;

        for (var e : svc.registry().entries()) {
            PhysicsBodyHandle h = e.value();
            if (h == null) continue;

            RigidBodyControl rb;
            try {
                rb = h.__raw();
            } catch (Throwable ignored) {
                continue;
            }
            if (rb == null) continue;

            int delta = store.updateAndGetDelta(h.id, rb, MOVE_POS_EPS, MOVE_ROT_EPS, MOVE_VEL_EPS);
            if (delta == BodyState.DELTA_NONE) continue;

            if ((delta & BodyState.DELTA_WAKE) != 0) {
                svc.engine().getBus().emit("engine.physics.body.wake", evtJs(
                        "step", step,
                        "dt", dt,
                        "bodyId", h.id,
                        "surfaceId", h.surfaceId
                ));
            }

            if ((delta & BodyState.DELTA_SLEEP) != 0) {
                svc.engine().getBus().emit("engine.physics.body.sleep", evtJs(
                        "step", step,
                        "dt", dt,
                        "bodyId", h.id,
                        "surfaceId", h.surfaceId
                ));
            }

            if ((delta & BodyState.DELTA_MOVE) != 0) {
                if (emitted++ >= MOVE_EVENT_MAX_PER_STEP) return;

                ProxyObject payload = evtJs(
                        "step", step,
                        "dt", dt,
                        "bodyId", h.id,
                        "surfaceId", h.surfaceId,
                        "pos", jsVec3(rb.getPhysicsLocation()),
                        "rot", jsQuat(rb.getPhysicsRotation()),
                        "vel", jsVec3(rb.getLinearVelocity()),
                        "angVel", jsVec3(rb.getAngularVelocity()),
                        "active", safeActive(rb)
                );

                svc.engine().getBus().emit("engine.physics.body.move", payload);
            }
        }
    }
}