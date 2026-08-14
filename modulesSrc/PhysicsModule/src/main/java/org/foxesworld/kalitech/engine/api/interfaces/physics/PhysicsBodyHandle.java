/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 */
package org.foxesworld.kalitech.engine.api.interfaces.physics;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public final class PhysicsBodyHandle {
    public final int id;
    public final int surfaceId;
    final RigidBodyControl body;

    public PhysicsBodyHandle(int id, int surfaceId, RigidBodyControl body) {
        this.id = id;
        this.surfaceId = surfaceId;
        this.body = body;
    }

    @LuaExport
    public void applyImpulse(Object vec3) {
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0.0f, 0.0f, 0.0f);
        this.body.applyImpulse(v, Vector3f.ZERO);
    }

    @LuaExport
    public void applyCentralForce(Object vec3) {
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0.0f, 0.0f, 0.0f);
        this.body.applyCentralForce(v);
    }

    @LuaExport
    public void setVelocity(Object vec3) {
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0.0f, 0.0f, 0.0f);
        this.body.setLinearVelocity(v);
        this.body.activate();
    }

    @LuaExport
    public void setAngularVelocity(Object vec3) {
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0.0f, 0.0f, 0.0f);
        this.body.setAngularVelocity(v);
        this.body.activate();
    }

    @LuaExport
    public void setKinematic(boolean v) {
        this.body.setKinematic(v);
        this.body.activate();
    }

    @LuaExport
    public void setEnabled(boolean v) {
        this.body.setEnabled(v);
        this.body.activate();
    }

    @LuaExport
    public void setFriction(double v) {
        this.body.setFriction((float)v);
    }

    @LuaExport
    public void setRestitution(double v) {
        this.body.setRestitution((float)v);
    }

    @LuaExport
    public void setDamping(double linear, double angular) {
        this.body.setDamping((float)linear, (float)angular);
    }

    @LuaExport
    public void setGravity(Object vec3) {
        Vector3f g = PhysicsValueParsers.vec3(vec3, 0.0f, -9.81f, 0.0f);
        this.body.setGravity(g);
        this.body.activate();
    }

    @LuaExport
    public void teleport(Object vec3) {
        Vector3f p = PhysicsValueParsers.vec3(vec3, 0.0f, 0.0f, 0.0f);
        this.body.setPhysicsLocation(p);
        this.body.setLinearVelocity(Vector3f.ZERO);
        this.body.setAngularVelocity(Vector3f.ZERO);
        this.body.activate();
    }

    @LuaExport
    public int id() {
        return this.id;
    }

    @LuaExport
    public int surfaceId() {
        return this.surfaceId;
    }

    @LuaExport
    public int valueOf() {
        return this.id;
    }

    @LuaExport
    public float mass() {
        return this.body.getMass();
    }

    @LuaExport
    public Object position() {
        Vector3f p = this.body.getPhysicsLocation();
        return new PhysicsRayHit.Vec3(p.x, p.y, p.z);
    }

    public RigidBodyControl __raw() {
        return this.body;
    }
}

