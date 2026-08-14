/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector3f
 *  com.jme3.scene.Spatial
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 *  org.foxesworld.kalitech.engine.script.lua.LuaObject
 */
package org.foxesworld.kalitech.engine.modules.physics.events;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.ContactAgg;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsLua;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

public final class CollisionEventBuilder {
    private final SurfaceRegistry surfaces;

    public CollisionEventBuilder(SurfaceRegistry surfaces) {
        this.surfaces = surfaces;
    }

    private static boolean isActiveSafe(RigidBodyControl rb) {
        if (rb == null) {
            return false;
        }
        try {
            return rb.isActive();
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isKinematicSafe(RigidBodyControl rb) {
        if (rb == null) {
            return false;
        }
        try {
            return rb.isKinematic();
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private static float massSafe(RigidBodyControl rb) {
        if (rb == null) {
            return 0.0f;
        }
        try {
            return rb.getMass();
        }
        catch (Throwable ignored) {
            return 0.0f;
        }
    }

    private static LuaObject groupsSafe(RigidBodyControl rb) {
        if (rb == null) {
            return null;
        }
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

    private String entityOfSurface(int surfaceId) {
        if (this.surfaces == null || surfaceId <= 0) {
            return null;
        }
        String uuid = this.surfaces.attachedEntityUuid(surfaceId);
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        return uuid;
    }

    private static LuaObject luaVec3SafePos(RigidBodyControl rb) {
        Vector3f p;
        try {
            p = rb != null ? rb.getPhysicsLocation() : null;
        }
        catch (Throwable ignored) {
            p = null;
        }
        if (p == null) {
            return PhysicsLua.evtLua("x", Float.valueOf(0.0f), "y", Float.valueOf(0.0f), "z", Float.valueOf(0.0f));
        }
        return PhysicsLua.luaVec3(p);
    }

    private static LuaObject luaVec3SafeVel(RigidBodyControl rb) {
        Vector3f v;
        try {
            v = rb != null ? rb.getLinearVelocity() : null;
        }
        catch (Throwable ignored) {
            v = null;
        }
        if (v == null) {
            return PhysicsLua.evtLua("x", Float.valueOf(0.0f), "y", Float.valueOf(0.0f), "z", Float.valueOf(0.0f));
        }
        return PhysicsLua.luaVec3(v);
    }

    private static LuaObject luaVec3SafeAngVel(RigidBodyControl rb) {
        Vector3f v;
        try {
            v = rb != null ? rb.getAngularVelocity() : null;
        }
        catch (Throwable ignored) {
            v = null;
        }
        if (v == null) {
            return PhysicsLua.evtLua("x", Float.valueOf(0.0f), "y", Float.valueOf(0.0f), "z", Float.valueOf(0.0f));
        }
        return PhysicsLua.luaVec3(v);
    }

    private static LuaObject luaQuatSafe(RigidBodyControl rb) {
        Quaternion q;
        try {
            q = rb != null ? rb.getPhysicsRotation() : null;
        }
        catch (Throwable ignored) {
            q = null;
        }
        if (q == null) {
            return PhysicsLua.evtLua("x", Float.valueOf(0.0f), "y", Float.valueOf(0.0f), "z", Float.valueOf(0.0f), "w", Float.valueOf(1.0f));
        }
        return PhysicsLua.luaQuat(q);
    }

    public LuaObject contactPayload(ContactAgg agg) {
        if (agg == null || agg.points <= 0) {
            return PhysicsLua.evtLua("maxImpulse", Float.valueOf(0.0f), "points", 0, "point", PhysicsLua.evtLua("x", Float.valueOf(0.0f), "y", Float.valueOf(0.0f), "z", Float.valueOf(0.0f)), "normal", PhysicsLua.evtLua("x", Float.valueOf(0.0f), "y", Float.valueOf(1.0f), "z", Float.valueOf(0.0f)));
        }
        float inv = 1.0f / (float)Math.max(1, agg.points);
        float px = agg.sumPx * inv;
        float py = agg.sumPy * inv;
        float pz = agg.sumPz * inv;
        float nx = agg.sumNx * inv;
        float ny = agg.sumNy * inv;
        float nz = agg.sumNz * inv;
        float nLen2 = nx * nx + ny * ny + nz * nz;
        if (nLen2 > 1.0E-12f) {
            float invN = 1.0f / (float)Math.sqrt(nLen2);
            nx *= invN;
            ny *= invN;
            nz *= invN;
        } else {
            nx = 0.0f;
            ny = 1.0f;
            nz = 0.0f;
        }
        return PhysicsLua.evtLua("maxImpulse", Float.valueOf(agg.maxImpulse), "points", agg.points, "point", PhysicsLua.evtLua("x", Float.valueOf(px), "y", Float.valueOf(py), "z", Float.valueOf(pz)), "normal", PhysicsLua.evtLua("x", Float.valueOf(nx), "y", Float.valueOf(ny), "z", Float.valueOf(nz)));
    }

    public LuaObject bodyPayload(PhysicsBodyHandle handle) {
        if (handle == null) return null;

        RigidBodyControl body;
        try {
            body = handle.__raw();
        } catch (Throwable ignored) {
            body = null;
        }

        Spatial spatial = null;
        if (surfaces != null) {
            try {
                spatial = surfaces.get(handle.surfaceId);
            } catch (Throwable ignored) {
            }
        }

        String entity = entityOfSurface(handle.surfaceId);
        return PhysicsLua.evtLua(
                "bodyId", handle.id,
                "surfaceId", handle.surfaceId,
                "entity", entity,
                "name", spatial == null ? null : spatial.getName(),
                "pos", luaVec3SafePos(body),
                "rot", luaQuatSafe(body),
                "vel", luaVec3SafeVel(body),
                "angVel", luaVec3SafeAngVel(body),
                "active", isActiveSafe(body),
                "mass", massSafe(body),
                "kinematic", isKinematicSafe(body),
                "groups", groupsSafe(body)
        );

    }
}

