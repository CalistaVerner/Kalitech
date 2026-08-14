/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector3f
 */
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.Objects;

public final class BodyState {
    public static final int DELTA_NONE = 0;
    public static final int DELTA_INIT = 1;
    public static final int DELTA_MOVE = 2;
    public static final int DELTA_WAKE = 4;
    public static final int DELTA_SLEEP = 8;
    public final Vector3f pos = new Vector3f();
    public final Quaternion rot = new Quaternion();
    public final Vector3f linVel = new Vector3f();
    public final Vector3f angVel = new Vector3f();
    public boolean active;
    public boolean init;

    public static BodyState from(RigidBodyControl rb) {
        Objects.requireNonNull(rb, "rb");
        BodyState s = new BodyState();
        s.readFrom(rb);
        return s;
    }

    private static boolean safeIsActive(RigidBodyControl rb) {
        try {
            return rb.isActive();
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean readVec3(RigidBodyControl rb, ReadKind kind, Vector3f store) {
        try {
            switch (kind) {
                case POS: {
                    rb.getPhysicsLocation(store);
                    return true;
                }
                case LIN_VEL: {
                    rb.getLinearVelocity(store);
                    return true;
                }
                case ANG_VEL: {
                    rb.getAngularVelocity(store);
                    return true;
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
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
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        store.set(0.0f, 0.0f, 0.0f);
        return false;
    }

    private static boolean readQuat(RigidBodyControl rb, Quaternion store) {
        try {
            rb.getPhysicsRotation(store);
            return true;
        }
        catch (Throwable throwable) {
            try {
                Quaternion q = rb.getPhysicsRotation();
                if (q != null) {
                    store.set(q);
                    return true;
                }
            }
            catch (Throwable throwable2) {
                // empty catch block
            }
            store.set(0.0f, 0.0f, 0.0f, 1.0f);
            return false;
        }
    }

    public void readFrom(RigidBodyControl rb) {
        Objects.requireNonNull(rb, "rb");
        BodyState.readVec3(rb, ReadKind.POS, this.pos);
        BodyState.readQuat(rb, this.rot);
        BodyState.readVec3(rb, ReadKind.LIN_VEL, this.linVel);
        BodyState.readVec3(rb, ReadKind.ANG_VEL, this.angVel);
        this.active = BodyState.safeIsActive(rb);
        this.init = true;
    }

    public int updateFromAndGetDelta(RigidBodyControl rb, float posEps, float rotEps, float velEps) {
        float dot;
        Objects.requireNonNull(rb, "rb");
        float posEps2 = posEps * posEps;
        float velEps2 = velEps * velEps;
        boolean activeNow = BodyState.safeIsActive(rb);
        if (!this.init) {
            this.readFrom(rb);
            int d = 1;
            if (activeNow) {
                d |= 4;
            }
            return d;
        }
        int delta = 0;
        Vector3f tmpV = TMP.V3.get();
        Quaternion tmpQ = TMP.Q.get();
        boolean moved = false;
        if (BodyState.readVec3(rb, ReadKind.POS, tmpV) && this.pos.distanceSquared(tmpV) > posEps2) {
            moved = true;
        }
        if (!moved && BodyState.readQuat(rb, tmpQ) && 1.0f - (dot = Math.abs(tmpQ.dot(this.rot))) > rotEps) {
            moved = true;
        }
        if (!moved && BodyState.readVec3(rb, ReadKind.LIN_VEL, tmpV) && this.linVel.distanceSquared(tmpV) > velEps2) {
            moved = true;
        }
        if (!moved && BodyState.readVec3(rb, ReadKind.ANG_VEL, tmpV) && this.angVel.distanceSquared(tmpV) > velEps2) {
            moved = true;
        }
        if (moved) {
            delta |= 2;
        }
        if (this.active != activeNow) {
            delta |= activeNow ? 4 : 8;
        }
        if (delta != 0) {
            BodyState.readVec3(rb, ReadKind.POS, this.pos);
            BodyState.readQuat(rb, this.rot);
            BodyState.readVec3(rb, ReadKind.LIN_VEL, this.linVel);
            BodyState.readVec3(rb, ReadKind.ANG_VEL, this.angVel);
            this.active = activeNow;
        }
        return delta;
    }

    private static enum ReadKind {
        POS,
        LIN_VEL,
        ANG_VEL;

    }

    private static final class TMP {
        static final ThreadLocal<Vector3f> V3 = ThreadLocal.withInitial(Vector3f::new);
        static final ThreadLocal<Quaternion> Q = ThreadLocal.withInitial(Quaternion::new);

        private TMP() {
        }
    }
}

