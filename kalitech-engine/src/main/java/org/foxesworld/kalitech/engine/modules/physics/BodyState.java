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

    /**
     * Creates a new snapshot from rigid body.
     */
    public static BodyState from(RigidBodyControl rb) {
        Objects.requireNonNull(rb, "rb");
        BodyState s = new BodyState();
        s.readFrom(rb);
        return s;
    }

    /**
     * Reads rigid body state into this snapshot.
     */
    public void readFrom(RigidBodyControl rb) {
        Objects.requireNonNull(rb, "rb");

        Vector3f p = null;
        Quaternion q = null;
        Vector3f lv = null;
        Vector3f av = null;

        try {
            p = rb.getPhysicsLocation();
        } catch (Throwable ignored) {
        }
        try {
            q = rb.getPhysicsRotation();
        } catch (Throwable ignored) {
        }
        try {
            lv = rb.getLinearVelocity();
        } catch (Throwable ignored) {
        }
        try {
            av = rb.getAngularVelocity();
        } catch (Throwable ignored) {
        }

        if (p != null) this.pos.set(p);
        else this.pos.set(0f, 0f, 0f);

        if (q != null) this.rot.set(q);
        else this.rot.set(0f, 0f, 0f, 1f);

        if (lv != null) this.linVel.set(lv);
        else this.linVel.set(0f, 0f, 0f);

        if (av != null) this.angVel.set(av);
        else this.angVel.set(0f, 0f, 0f);

        boolean a = false;
        try {
            a = rb.isActive();
        } catch (Throwable ignored) {
        }
        this.active = a;
        this.init = true;
    }

    /**
     * Copies values from another snapshot.
     */
    public void setFrom(BodyState other) {
        Objects.requireNonNull(other, "other");
        this.pos.set(other.pos);
        this.rot.set(other.rot);
        this.linVel.set(other.linVel);
        this.angVel.set(other.angVel);
        this.active = other.active;
        this.init = other.init;
    }

    /**
     * Updates this snapshot from rigid body and returns true if anything changed beyond eps thresholds.
     */
    public boolean updateFromAndCheckChanged(
            RigidBodyControl rb,
            float posEps,
            float rotEps,
            float velEps
    ) {
        Objects.requireNonNull(rb, "rb");

        Vector3f p;
        Quaternion q;
        Vector3f lv;
        Vector3f av;

        try {
            p = rb.getPhysicsLocation();
        } catch (Throwable t) {
            p = null;
        }
        try {
            q = rb.getPhysicsRotation();
        } catch (Throwable t) {
            q = null;
        }
        try {
            lv = rb.getLinearVelocity();
        } catch (Throwable t) {
            lv = null;
        }
        try {
            av = rb.getAngularVelocity();
        } catch (Throwable t) {
            av = null;
        }

        boolean a;
        try {
            a = rb.isActive();
        } catch (Throwable t) {
            a = false;
        }

        boolean moved = false;

        float posEps2 = posEps * posEps;
        float velEps2 = velEps * velEps;

        if (!init) {
            if (p != null) pos.set(p);
            if (q != null) rot.set(q);
            if (lv != null) linVel.set(lv);
            if (av != null) angVel.set(av);
            active = a;
            init = true;
            return true;
        }

        if (p != null && pos.distanceSquared(p) > posEps2) moved = true;
        if (q != null && Math.abs(q.dot(rot)) < (1.0f - rotEps)) moved = true;
        if (lv != null && linVel.distanceSquared(lv) > velEps2) moved = true;
        if (av != null && angVel.distanceSquared(av) > velEps2) moved = true;
        if (active != a) moved = true;

        if (moved) {
            if (p != null) pos.set(p);
            if (q != null) rot.set(q);
            if (lv != null) linVel.set(lv);
            if (av != null) angVel.set(av);
            active = a;
        }

        return moved;
    }
}