// FILE: org/foxesworld/kalitech/engine/api/interfaces/physics/PhysicsBodyHandle.java
package org.foxesworld.kalitech.engine.api.interfaces.physics;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

import java.util.Map;

import static org.foxesworld.kalitech.engine.script.util.LuaCfg.member;

@SuppressWarnings("unused")
public final class PhysicsBodyHandle {

    public final int id;
    public final int surfaceId;

    final RigidBodyControl body;

    public PhysicsBodyHandle(int id, int surfaceId, RigidBodyControl body) {
        this.id = id;
        this.surfaceId = surfaceId;
        this.body = body;
    }

    // ----- actions -----

    public static float asFloat(Object v, float def) {
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof LuaValueRef val) return val.isNumber() ? (float) val.asDouble() : def;
        return def;
    }

    public static void vec3Into(Object v, Vector3f out, float dx, float dy, float dz) {
        if (out == null) return;

        if (v == null) {
            out.set(dx, dy, dz);
            return;
        }

        if (v instanceof Vector3f vv) {
            out.set(vv);
            return;
        }

        if (v instanceof LuaValueRef val) {
            if (val.isNull()) {
                out.set(dx, dy, dz);
                return;
            }

            if (val.hasArrayElements() && val.getArraySize() >= 3) {
                out.set(
                        asFloat(val.getArrayElement(0), dx),
                        asFloat(val.getArrayElement(1), dy),
                        asFloat(val.getArrayElement(2), dz)
                );
                return;
            }

            if (val.hasMembers()) {
                out.set(
                        asFloat(member(val, "x"), dx),
                        asFloat(member(val, "y"), dy),
                        asFloat(member(val, "z"), dz)
                );
                return;
            }
        }

        if (v instanceof Map<?, ?> m) {
            out.set(
                    asFloat(m.get("x"), dx),
                    asFloat(m.get("y"), dy),
                    asFloat(m.get("z"), dz)
            );
            return;
        }

        out.set(dx, dy, dz);
    }

    public static Vector3f vec3(Object v, float dx, float dy, float dz) {
        Vector3f out = new Vector3f();
        vec3Into(v, out, dx, dy, dz);
        return out;
    }

    @LuaExport
    public void applyImpulse(Object vec3) {
        Vector3f v = vec3(vec3, 0, 0, 0);
        body.applyImpulse(v, Vector3f.ZERO);
    }

    @LuaExport
    public void setKinematic(boolean v) {
        body.setKinematic(v);
        body.activate();
    }

    @LuaExport
    public void setEnabled(boolean v) {
        body.setEnabled(v);
        body.activate();
    }

    @LuaExport
    public void setFriction(double v) {
        body.setFriction((float) v);
    }

    @LuaExport
    public void setRestitution(double v) {
        body.setRestitution((float) v);
    }

    @LuaExport
    public void setDamping(double linear, double angular) {
        body.setDamping((float) linear, (float) angular);
    }

    @LuaExport
    public void applyCentralForce(Object vec3) {
        Vector3f v = vec3(vec3, 0, 0, 0);
        body.applyCentralForce(v);
    }

    @LuaExport
    public void setVelocity(Object vec3) {
        Vector3f v = vec3(vec3, 0, 0, 0);
        body.setLinearVelocity(v);
        body.activate();
    }

    // ----- getters -----

    //  CRITICAL: make id visible to Lua in a durable way
    @LuaExport
    public int id() {
        return id;
    }

    //  CRITICAL: make surfaceId visible to Lua
    @LuaExport
    public int surfaceId() {
        return surfaceId;
    }

    // Optional: allow numeric coercion in Lua (Number(handle) / handle | 0 in some cases)
    @LuaExport
    public int valueOf() {
        return id;
    }

    @LuaExport
    public float mass() {
        return body.getMass();
    }

    @LuaExport
    public Object position() {
        Vector3f p = body.getPhysicsLocation();
        return new PhysicsRayHit.Vec3(p.x, p.y, p.z);
    }

    @LuaExport
    public void setAngularVelocity(Object vec3) {
        Vector3f v = vec3(vec3, 0, 0, 0);
        body.setAngularVelocity(v);
        body.activate();
    }


    // internal
    public RigidBodyControl __raw() { return body; }

    @LuaExport
    public void setGravity(Object vec3) {
        Vector3f g = vec3(vec3, 0, -9.81f, 0);
        body.setGravity(g);
        body.activate();
    }

    @LuaExport
    public void teleport(Object vec3) {
        Vector3f p = vec3(vec3, 0, 0, 0);
        body.setPhysicsLocation(p);
        body.setLinearVelocity(Vector3f.ZERO);
        body.setAngularVelocity(Vector3f.ZERO);
        body.activate();
    }
}