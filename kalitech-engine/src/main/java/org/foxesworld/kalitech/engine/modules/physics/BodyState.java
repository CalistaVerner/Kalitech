// FILE: org/foxesworld/kalitech/engine/modules/physics/BodyState.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import java.util.Objects;

/**
 * Snapshot of a rigid body state for change detection and event emission.
 */
public final class BodyState {

    public final Vector3f pos = new Vector3f();
    public final Quaternion rot = new Quaternion();
    public final Vector3f linVel = new Vector3f();
    public final Vector3f angVel = new Vector3f();

    public boolean active;
    public boolean init;

    public BodyState() {
    }

    public static BodyState from(RigidBodyControl rb) {
        Objects.requireNonNull(rb, "rb");
        BodyState s = new BodyState();
        s.readFrom(rb);
        return s;
    }

    public void readFrom(RigidBodyControl rb) {
        Objects.requireNonNull(rb, "rb");
        readVec3(rb, ReadKind.POS, pos);
        readQuat(rb, rot);
        readVec3(rb, ReadKind.LIN_VEL, linVel);
        readVec3(rb, ReadKind.ANG_VEL, angVel);
        active = safeIsActive(rb);
        init = true;
    }

    public void setFrom(BodyState other) {
        Objects.requireNonNull(other, "other");
        pos.set(other.pos);
        rot.set(other.rot);
        linVel.set(other.linVel);
        angVel.set(other.angVel);
        active = other.active;
        init = other.init;
    }

    /**
     * Updates snapshot from rigid body and returns true if anything changed beyond eps thresholds.
     * Uses provided scratch objects to avoid ThreadLocal allocations.
     */
    public boolean updateFromAndCheckChanged(
            RigidBodyControl rb,
            float posEps,
            float rotEps,
            float velEps,
            Vector3f tmpV,
            Quaternion tmpQ
    ) {
        Objects.requireNonNull(rb, "rb");
        Objects.requireNonNull(tmpV, "tmpV");
        Objects.requireNonNull(tmpQ, "tmpQ");

        final float posEps2 = posEps * posEps;
        final float velEps2 = velEps * velEps;
        final boolean a = safeIsActive(rb);

        if (!init) {
            readFrom(rb);
            return true;
        }

        boolean changed = false;

        if (readVec3(rb, ReadKind.POS, tmpV) && pos.distanceSquared(tmpV) > posEps2) changed = true;
        if (readQuat(rb, tmpQ) && Math.abs(tmpQ.dot(rot)) < (1.0f - rotEps)) changed = true;
        if (readVec3(rb, ReadKind.LIN_VEL, tmpV) && linVel.distanceSquared(tmpV) > velEps2) changed = true;
        if (readVec3(rb, ReadKind.ANG_VEL, tmpV) && angVel.distanceSquared(tmpV) > velEps2) changed = true;
        if (active != a) changed = true;

        if (changed) {
            readVec3(rb, ReadKind.POS, pos);
            readQuat(rb, rot);
            readVec3(rb, ReadKind.LIN_VEL, linVel);
            readVec3(rb, ReadKind.ANG_VEL, angVel);
            active = a;
        }

        return changed;
    }

    /**
     * Backward-compatible overload (kept), uses ThreadLocal scratch.
     */
    public boolean updateFromAndCheckChanged(RigidBodyControl rb, float posEps, float rotEps, float velEps) {
        return updateFromAndCheckChanged(rb, posEps, rotEps, velEps, TMP.V3.get(), TMP.Q.get());
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

    private enum ReadKind {POS, LIN_VEL, ANG_VEL}

    private static final class TMP {
        static final ThreadLocal<Vector3f> V3 = ThreadLocal.withInitial(Vector3f::new);
        static final ThreadLocal<Quaternion> Q = ThreadLocal.withInitial(Quaternion::new);
    }
}