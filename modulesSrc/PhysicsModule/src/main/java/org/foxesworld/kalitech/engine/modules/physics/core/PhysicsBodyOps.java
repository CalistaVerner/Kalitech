/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector3f
 *  org.foxesworld.kalitech.engine.script.events.ScriptEventBus
 *  org.foxesworld.kalitech.engine.script.lua.LuaObject
 */
package org.foxesworld.kalitech.engine.modules.physics.core;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.Objects;
import java.util.function.IntFunction;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsLua;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsRegistry;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

public final class PhysicsBodyOps {
    private final PhysicsRegistry registry;
    private final ScriptEventBus bus;
    private final IntFunction<String> entityOfSurface;

    public PhysicsBodyOps(PhysicsRegistry registry, ScriptEventBus bus, IntFunction<String> entityOfSurface) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.entityOfSurface = Objects.requireNonNull(entityOfSurface, "entityOfSurface");
    }

    public Object position(Object handleOrId) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.position()");
        Vector3f p = h.__raw().getPhysicsLocation();
        return new PhysicsRayHit.Vec3(p.x, p.y, p.z);
    }

    public Object velocity(Object handleOrId) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.velocity()");
        Vector3f v = h.__raw().getLinearVelocity();
        return new PhysicsRayHit.Vec3(v.x, v.y, v.z);
    }

    public void velocity(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.velocity(v)");
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0.0f, 0.0f, 0.0f);
        h.__raw().setLinearVelocity(v);
    }

    public void angularVelocity(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.angularVelocity(v)");
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0.0f, 0.0f, 0.0f);
        h.__raw().setAngularVelocity(v);
    }

    public Object angularVelocity(Object handleOrId) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.angularVelocity()");
        Vector3f v = h.__raw().getAngularVelocity();
        return new PhysicsRayHit.Vec3(v.x, v.y, v.z);
    }

    public void yaw(Object handleOrId, double yaw) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.yaw(yaw)");
        RigidBodyControl rb = h.__raw();
        Quaternion q = TMP.Q.get();
        q.fromAngles(0.0f, (float)yaw, 0.0f);
        rb.setPhysicsRotation(q);
        rb.setAngularVelocity(Vector3f.ZERO);
    }

    public void warp(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.warp(pos)");
        Vector3f p = PhysicsValueParsers.vec3(vec3, 0.0f, 0.0f, 0.0f);
        RigidBodyControl rb = h.__raw();
        rb.setPhysicsLocation(p);
        rb.setLinearVelocity(Vector3f.ZERO);
        rb.setAngularVelocity(Vector3f.ZERO);
        if (this.bus != null) {
            this.bus.emit("engine.physics.body.teleport", (Object)PhysicsLua.evtLua("bodyId", h.id, "surfaceId", h.surfaceId, "entity", this.entityOfSurface.apply(h.surfaceId), "pos", PhysicsLua.luaVec3(p)));
        }
    }

    public void applyImpulse(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.applyImpulse(impulse)");
        Vector3f imp = PhysicsValueParsers.vec3(vec3, 0.0f, 0.0f, 0.0f);
        h.__raw().applyImpulse(imp, Vector3f.ZERO);
    }

    public void applyCentralForce(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.applyCentralForce(force)");
        Vector3f f = PhysicsValueParsers.vec3(vec3, 0.0f, 0.0f, 0.0f);
        h.__raw().applyCentralForce(f);
    }

    public void applyTorque(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.applyTorque(torque)");
        Vector3f t = PhysicsValueParsers.vec3(vec3, 0.0f, 0.0f, 0.0f);
        h.__raw().applyTorque(t);
    }

    public void lockRotation(Object handleOrId, boolean lock) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.lockRotation(lock)");
        RigidBodyControl rb = h.__raw();
        if (lock) {
            rb.setAngularFactor(0.0f);
            rb.setAngularVelocity(Vector3f.ZERO);
        } else {
            rb.setAngularFactor(1.0f);
        }
    }

    public void setKinematic(Object handleOrId, boolean kinematic) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.setKinematic(kinematic)");
        RigidBodyControl rb = h.__raw();
        rb.setKinematic(kinematic);
        try {
            rb.activate();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public void collisionGroups(Object handleOrId, int group, int mask) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.collisionGroups(group,mask)");
        RigidBodyControl rb = h.__raw();
        rb.setCollisionGroup(group);
        rb.setCollideWithGroups(mask);
    }

    public void clearForces(Object handleOrId) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.clearForces()");
        RigidBodyControl rb = h.__raw();
        rb.clearForces();
        rb.setAngularVelocity(Vector3f.ZERO);
        rb.setLinearVelocity(Vector3f.ZERO);
    }

    public LuaObject groups(Object handleOrId) {
        PhysicsBodyHandle h = this.registry.requireHandle(handleOrId, "physics.groups()");
        RigidBodyControl rb = h.__raw();
        int group = 0;
        int mask = 0;
        try {
            group = rb.getCollisionGroup();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            mask = rb.getCollideWithGroups();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (group == 0 && mask == 0) {
            return null;
        }
        return PhysicsLua.evtLua("group", group, "mask", mask);
    }

    private static final class TMP {
        static final ThreadLocal<Quaternion> Q = ThreadLocal.withInitial(Quaternion::new);

        private TMP() {
        }
    }
}

