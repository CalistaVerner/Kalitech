// FILE: org/foxesworld/kalitech/engine/api/interfaces/PhysicsBodyHandle.java
package org.foxesworld.kalitech.engine.api.interfaces.physics;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.api.impl.physics.PhysicsValueParsers;

@SuppressWarnings("unused")
public final class PhysicsBodyHandle {

    public final int id;
    public final int surfaceId;

    private final RigidBodyControl body;

    public PhysicsBodyHandle(int id, int surfaceId, RigidBodyControl body) {
        this.id = id;
        this.surfaceId = surfaceId;
        this.body = body;
    }

    // ----- actions -----

    @HostAccess.Export
    public void applyImpulse(Object vec3) {
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        body.applyImpulse(v, Vector3f.ZERO);
    }

    @HostAccess.Export
    public void applyCentralForce(Object vec3) {
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        body.applyCentralForce(v);
    }

    @HostAccess.Export
    public void setVelocity(Object vec3) {
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        body.setLinearVelocity(v);
        body.activate();
    }

    @HostAccess.Export
    public void setAngularVelocity(Object vec3) {
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        body.setAngularVelocity(v);
        body.activate();
    }

    @HostAccess.Export
    public void setKinematic(boolean v) {
        body.setKinematic(v);
        body.activate();
    }

    @HostAccess.Export
    public void setEnabled(boolean v) {
        body.setEnabled(v);
        body.activate();
    }

    @HostAccess.Export
    public void setFriction(double v) {
        body.setFriction((float) v);
    }

    @HostAccess.Export
    public void setRestitution(double v) {
        body.setRestitution((float) v);
    }

    @HostAccess.Export
    public void setDamping(double linear, double angular) {
        body.setDamping((float) linear, (float) angular);
    }

    @HostAccess.Export
    public void setGravity(Object vec3) {
        Vector3f g = PhysicsValueParsers.vec3(vec3, 0, -9.81f, 0);
        body.setGravity(g);
        body.activate();
    }

    @HostAccess.Export
    public void teleport(Object vec3) {
        Vector3f p = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        body.setPhysicsLocation(p);
        body.setLinearVelocity(Vector3f.ZERO);
        body.setAngularVelocity(Vector3f.ZERO);
        body.activate();
    }

    // ----- getters -----

    @HostAccess.Export
    public float mass() {
        return body.getMass();
    }

    @HostAccess.Export
    public Object position() {
        Vector3f p = body.getPhysicsLocation();
        return new PhysicsRayHit.Vec3(p.x, p.y, p.z);
    }

    // internal
    public RigidBodyControl __raw() { return body; }
}