// FILE: org/foxesworld/kalitech/engine/modules/physics/BodyState.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import java.util.Objects;

/**
 * Snapshot of a rigid body state for change detection and event emission.
 *
 * <p>Designed for hot-path usage: no allocations in steady state.</p>
 */
public final class BodyState {

    public static final int DELTA_NONE = 0;
    public static final int DELTA_INIT = 1 << 0;
    public static final int DELTA_MOVE = 1 << 1;
    public static final int DELTA_WAKE = 1 << 2;
    public static final int DELTA_SLEEP = 1 << 3;

    public final Vector3f pos = new Vector3f();
    public final Quaternion rot = new Quaternion();
    public final Vector3f linVel = new Vector3f();
    public final Vector3f angVel = new Vector3f();

    public boolean active;
    public boolean init;

    public BodyState() {
    }

    /**
     * Creates a new snapshot from rigid body.
     */
    public static BodyState from(RigidBodyControl rb) {
        Objects.requireNonNull(rb, "rb");
        BodyState s = new BodyState();
        s.readFrom(rb);
        return s;
    }

    private static boolean safeIsActive(RigidBodyControl rb) {
        try {
            return rb.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean readVec3(RigidBodyControl rb, ReadKind kind, Vector3f store) {
        try {
            switch (kind) {
                case POS -> {
                    rb.getPhysicsLocation(store);
                    return true;
                }
                case LIN_VEL -> {
                    rb.getLinearVelocity(store);
                    return true;
                }
                case ANG_VEL -> {
                    rb.getAngularVelocity(store);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Vector3f v = switch (kind) {
                case POS -> rb.getPhysicsLocation();
                case LIN_VEL -> rb.getLinearVelocity();
                case ANG_VEL -> rb.getAngularVelocity();
            };
            if (v != null) {
                store.set(v);
                return true;
            }
        } catch (Throwable ignored) {
        }

        store.set(0f, 0f, 0f);
        return false;
    }

    private static boolean readQuat(RigidBodyControl rb, Quaternion store) {
        try {
            rb.getPhysicsRotation(store);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            Quaternion q = rb.getPhysicsRotation();
            if (q != null) {
                store.set(q);
                return true;
            }
        } catch (Throwable ignored) {
        }

        store.set(0f, 0f, 0f, 1f);
        return false;
    }

    /**
     * Reads rigid body state into this snapshot.
     */
    public void readFrom(RigidBodyControl rb) {
        Objects.requireNonNull(rb, "rb");

        readVec3(rb, ReadKind.POS, pos);
        readQuat(rb, rot);
        readVec3(rb, ReadKind.LIN_VEL, linVel);
        readVec3(rb, ReadKind.ANG_VEL, angVel);

        active = safeIsActive(rb);
        init = true;
    }

    /**
     * Updates this snapshot from rigid body and returns delta flags.
     *
     * <p>Delta flags: INIT, MOVE, WAKE, SLEEP.</p>
     */
    public int updateFromAndGetDelta(RigidBodyControl rb, float posEps, float rotEps, float velEps) {
        Objects.requireNonNull(rb, "rb");

        final float posEps2 = posEps * posEps;
        final float velEps2 = velEps * velEps;

        final boolean activeNow = safeIsActive(rb);

        if (!init) {
            readFrom(rb);
            int d = DELTA_INIT;
            if (activeNow) d |= DELTA_WAKE;
            return d;
        }

        int delta = DELTA_NONE;

        Vector3f tmpV = TMP.V3.get();
        Quaternion tmpQ = TMP.Q.get();

        boolean moved = false;

        if (readVec3(rb, ReadKind.POS, tmpV) && pos.distanceSquared(tmpV) > posEps2) moved = true;

        if (!moved && readQuat(rb, tmpQ)) {
            float dot = Math.abs(tmpQ.dot(rot));
            if ((1.0f - dot) > rotEps) moved = true;
        }

        if (!moved && readVec3(rb, ReadKind.LIN_VEL, tmpV) && linVel.distanceSquared(tmpV) > velEps2) moved = true;

        if (!moved && readVec3(rb, ReadKind.ANG_VEL, tmpV) && angVel.distanceSquared(tmpV) > velEps2) moved = true;

        if (moved) delta |= DELTA_MOVE;

        if (active != activeNow) {
            delta |= activeNow ? DELTA_WAKE : DELTA_SLEEP;
        }

        if (delta != DELTA_NONE) {
            readVec3(rb, ReadKind.POS, pos);
            readQuat(rb, rot);
            readVec3(rb, ReadKind.LIN_VEL, linVel);
            readVec3(rb, ReadKind.ANG_VEL, angVel);
            active = activeNow;
        }

        return delta;
    }

    private enum ReadKind {POS, LIN_VEL, ANG_VEL}

    private static final class TMP {
        static final ThreadLocal<Vector3f> V3 = ThreadLocal.withInitial(Vector3f::new);
        static final ThreadLocal<Quaternion> Q = ThreadLocal.withInitial(Quaternion::new);
    }
}
