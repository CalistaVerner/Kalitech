/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi$SurfaceHandle
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime.service.body;

import java.util.Map;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.body.PhysicsBodyConfig;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class PhysicsBodyConfigParser {
    private static int resolveIdFromValue(LuaValueRef v, String ... members) {
        if (v == null || members == null) {
            return 0;
        }
        for (String m : members) {
            try {
                LuaValueRef r;
                LuaValueRef mv;
                if (!v.hasMember(m) || (mv = v.getMember(m)) == null) continue;
                if (mv.isNumber()) {
                    return mv.asInt();
                }
                if (!mv.canExecute() || (r = mv.execute(new Object[0])) == null || !r.isNumber()) continue;
                return r.asInt();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return 0;
    }

    PhysicsBodyConfig parse(Object cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("physics.body(cfg) cfg is required");
        }
        int surfaceId = this.resolveSurfaceId(cfg);
        if (surfaceId <= 0) {
            throw new IllegalArgumentException("physics.body: surface id is required");
        }
        float mass = (float)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mass"), 0.0);
        float friction = (float)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "friction"), 0.8f);
        float restitution = (float)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "restitution"), 0.1f);
        float dampingLinear = 0.05f;
        float dampingAngular = 0.1f;
        Object damping = PhysicsValueParsers.member(cfg, "damping");
        if (damping != null) {
            dampingLinear = (float)PhysicsValueParsers.asNum(PhysicsValueParsers.member(damping, "linear"), dampingLinear);
            dampingAngular = (float)PhysicsValueParsers.asNum(PhysicsValueParsers.member(damping, "angular"), dampingAngular);
        }
        boolean kinematic = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "kinematic"), false);
        boolean lockRot = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "lockRotation"), false);
        float ccdMotionThreshold = (float)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ccdMotionThreshold"), 0.001f);
        float ccdRadius = (float)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ccdSweptSphereRadius"), 0.2f);
        Object colliderCfg = PhysicsValueParsers.member(cfg, "collider");
        return new PhysicsBodyConfig(surfaceId, mass, friction, restitution, dampingLinear, dampingAngular, kinematic, lockRot, Math.max(0.0f, ccdMotionThreshold), Math.max(0.0f, ccdRadius), colliderCfg);
    }

    private int resolveSurfaceId(Object cfg) {
        Object s = PhysicsValueParsers.member(cfg, "surface");
        if (s == null) {
            return 0;
        }
        if (s instanceof Number) {
            Number n = (Number)s;
            return n.intValue();
        }
        if (s instanceof LuaValueRef) {
            LuaValueRef v = (LuaValueRef)s;
            try {
                if (v.isNumber()) {
                    return v.asInt();
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            int id = PhysicsBodyConfigParser.resolveIdFromValue(v, "id", "surfaceId");
            if (id > 0) {
                return id;
            }
        }
        if (s instanceof SurfaceApi.SurfaceHandle) {
            SurfaceApi.SurfaceHandle h = (SurfaceApi.SurfaceHandle)s;
            return h.id;
        }
        if (s instanceof Map) {
            Map m = (Map)s;
            Object id = m.get("id");
            if (id instanceof Number) {
                Number n = (Number)id;
                return n.intValue();
            }
            Object sid = m.get("surfaceId");
            if (sid instanceof Number) {
                Number n2 = (Number)sid;
                return n2.intValue();
            }
        }
        throw new IllegalArgumentException("physics.body: surface must be surfaceId or SurfaceHandle");
    }
}

