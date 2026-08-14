/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.script.lua.LuaObject
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime;

import com.jme3.bullet.control.RigidBodyControl;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.modules.physics.BodyStateStore;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsLua;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsService;
import org.foxesworld.kalitech.engine.modules.physics.util.IntObjectMap;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

public final class PhysicsBodyStateTracker {
    private static final float MOVE_POS_EPS = 0.0025f;
    private static final float MOVE_ROT_EPS = 0.001f;
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
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    public void onBodyRemoved(int bodyId) {
        this.store.remove(bodyId);
    }

    public void clear() {
        this.store.clear();
    }

    public void emit(long step, float dt) {
        int emitted = 0;
        for (IntObjectMap.Entry<PhysicsBodyHandle> e : this.svc.registry().entries()) {
            int delta;
            RigidBodyControl rb;
            PhysicsBodyHandle h = e.value();
            if (h == null) continue;
            try {
                rb = h.__raw();
            }
            catch (Throwable ignored) {
                continue;
            }
            if (rb == null || (delta = this.store.updateAndGetDelta(h.id, rb, 0.0025f, 0.001f, 0.01f)) == 0) continue;
            if ((delta & 4) != 0) {
                this.svc.engine().getBus().emit("engine.physics.body.wake", (Object)PhysicsLua.evtLua("step", step, "dt", Float.valueOf(dt), "bodyId", h.id, "surfaceId", h.surfaceId));
            }
            if ((delta & 8) != 0) {
                this.svc.engine().getBus().emit("engine.physics.body.sleep", (Object)PhysicsLua.evtLua("step", step, "dt", Float.valueOf(dt), "bodyId", h.id, "surfaceId", h.surfaceId));
            }
            if ((delta & 2) == 0) continue;
            if (emitted++ >= 512) {
                return;
            }
            LuaObject payload = PhysicsLua.evtLua("step", step, "dt", Float.valueOf(dt), "bodyId", h.id, "surfaceId", h.surfaceId, "pos", PhysicsLua.luaVec3(rb.getPhysicsLocation()), "rot", PhysicsLua.luaQuat(rb.getPhysicsRotation()), "vel", PhysicsLua.luaVec3(rb.getLinearVelocity()), "angVel", PhysicsLua.luaVec3(rb.getAngularVelocity()), "active", PhysicsBodyStateTracker.safeActive(rb));
            this.svc.engine().getBus().emit("engine.physics.body.move", (Object)payload);
        }
    }
}

