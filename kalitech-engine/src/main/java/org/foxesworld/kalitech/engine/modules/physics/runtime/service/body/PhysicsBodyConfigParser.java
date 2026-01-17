// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/PhysicsBodyConfigParser.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service.body;

import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.graalvm.polyglot.Value;

import java.util.Map;

/**
 * Parses JS/Map-based configs into a strongly typed {@link PhysicsBodyConfig}.
 */
public final class PhysicsBodyConfigParser {

    private static int resolveIdFromValue(Value v, String... members) {
        if (v == null || members == null) return 0;
        for (String m : members) {
            try {
                if (!v.hasMember(m)) continue;
                Value mv = v.getMember(m);
                if (mv == null) continue;
                if (mv.isNumber()) return mv.asInt();
                if (mv.canExecute()) {
                    Value r = mv.execute();
                    if (r != null && r.isNumber()) return r.asInt();
                }
            } catch (Throwable ignored) {
                // no-op
            }
        }
        return 0;
    }

    PhysicsBodyConfig parse(Object cfg) {
        if (cfg == null) throw new IllegalArgumentException("physics.body(cfg) cfg is required");

        int surfaceId = resolveSurfaceId(cfg);
        if (surfaceId <= 0) throw new IllegalArgumentException("physics.body: surface id is required");

        float mass = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mass"), 0.0);
        float friction = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "friction"), PhysicsBodyConfig.DEFAULT_FRICTION);
        float restitution = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "restitution"), PhysicsBodyConfig.DEFAULT_RESTITUTION);

        float dampingLinear = PhysicsBodyConfig.DEFAULT_DAMPING_LINEAR;
        float dampingAngular = PhysicsBodyConfig.DEFAULT_DAMPING_ANGULAR;

        Object damping = PhysicsValueParsers.member(cfg, "damping");
        if (damping != null) {
            dampingLinear = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(damping, "linear"), dampingLinear);
            dampingAngular = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(damping, "angular"), dampingAngular);
        }

        boolean kinematic = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "kinematic"), false);
        boolean lockRot = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "lockRotation"), false);

        float ccdMotionThreshold = (float) PhysicsValueParsers.asNum(
                PhysicsValueParsers.member(cfg, "ccdMotionThreshold"),
                PhysicsBodyConfig.DEFAULT_CCD_MOTION_THRESHOLD
        );

        float ccdRadius = (float) PhysicsValueParsers.asNum(
                PhysicsValueParsers.member(cfg, "ccdSweptSphereRadius"),
                PhysicsBodyConfig.DEFAULT_CCD_SWEPT_SPHERE_RADIUS
        );

        Object colliderCfg = PhysicsValueParsers.member(cfg, "collider");

        return new PhysicsBodyConfig(
                surfaceId,
                mass,
                friction,
                restitution,
                dampingLinear,
                dampingAngular,
                kinematic,
                lockRot,
                Math.max(0.0f, ccdMotionThreshold),
                Math.max(0.0f, ccdRadius),
                colliderCfg
        );
    }

    private int resolveSurfaceId(Object cfg) {
        Object s = PhysicsValueParsers.member(cfg, "surface");
        if (s == null) return 0;

        if (s instanceof Number n) return n.intValue();

        if (s instanceof Value v) {
            try {
                if (v.isNumber()) return v.asInt();
            } catch (Throwable ignored) {
                // no-op
            }
            int id = resolveIdFromValue(v, "id", "surfaceId");
            if (id > 0) return id;
        }

        if (s instanceof SurfaceApi.SurfaceHandle h) return h.id;

        if (s instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
            Object sid = m.get("surfaceId");
            if (sid instanceof Number n2) return n2.intValue();
        }

        throw new IllegalArgumentException("physics.body: surface must be surfaceId or SurfaceHandle");
    }
}