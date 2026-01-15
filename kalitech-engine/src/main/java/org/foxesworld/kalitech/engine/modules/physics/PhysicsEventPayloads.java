// FILE: org/foxesworld/kalitech/engine/modules/physics/PhysicsEventPayloads.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;

import java.util.Map;

/**
 * Helpers for building stable JS-friendly payloads for ScriptEventBus.
 */
public final class PhysicsEventPayloads {

    private PhysicsEventPayloads() {
    }

    public static String entityOfSpatial(Spatial sp) {
        if (sp == null) return null;

        Object v;
        try {
            v = sp.getUserData("entityUuid");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        try {
            v = sp.getUserData("entityId");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        try {
            v = sp.getUserData("uuid");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        return null;
    }

    public static Map<String, Object> vec3(Vector3f v) {
        if (v == null) return null;
        return PhysicsState.evt("x", v.x, "y", v.y, "z", v.z);
    }

    public static Map<String, Object> quat(Quaternion q) {
        if (q == null) return null;
        return PhysicsState.evt("x", q.getX(), "y", q.getY(), "z", q.getZ(), "w", q.getW());
    }

    public static boolean hasCollision(RigidBodyControl rb) {
        if (rb == null) return false;
        try {
            return rb.getCollisionShape() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isHardSurface(RigidBodyControl rb, Spatial sp) {
        if (!hasCollision(rb)) return false;

        if (sp != null) {
            try {
                Boolean hard = sp.getUserData("hardSurface");
                if (hard != null) return hard.booleanValue();
            } catch (Throwable ignored) {
            }
        }

        try {
            if (rb.isKinematic()) return true;
        } catch (Throwable ignored) {
        }

        try {
            float m = rb.getMass();
            if (Float.isFinite(m) && m <= 0f) return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    public static float relativeSpeedApprox(RigidBodyControl a, RigidBodyControl b) {
        if (a == null || b == null) return 0f;
        try {
            Vector3f va = a.getLinearVelocity();
            Vector3f vb = b.getLinearVelocity();
            if (va == null || vb == null) return 0f;
            float dx = va.x - vb.x;
            float dy = va.y - vb.y;
            float dz = va.z - vb.z;
            float s2 = dx * dx + dy * dy + dz * dz;
            return (s2 > 0f) ? (float) Math.sqrt(s2) : 0f;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    public static float massSafe(RigidBodyControl rb) {
        if (rb == null) return 0f;
        try {
            float m = rb.getMass();
            return Float.isFinite(m) ? m : 0f;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    public static boolean isKinematicSafe(RigidBodyControl rb) {
        if (rb == null) return false;
        try {
            return rb.isKinematic();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Map<String, Object> groupsSafe(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            return PhysicsState.evt(
                    "group", rb.getCollisionGroup(),
                    "mask", rb.getCollideWithGroups()
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Map<String, Object> bodySnapshot(PhysicsState S, PhysicsBodyHandle h) {
        if (S == null || h == null) return null;

        RigidBodyControl rb;
        try {
            rb = h.__raw();
        } catch (Throwable t) {
            rb = null;
        }

        Spatial sp = null;
        try {
            sp = S.surfaces.get(h.surfaceId);
        } catch (Throwable ignored) {
        }

        Vector3f p = null;
        Quaternion q = null;
        Vector3f lv = null;
        Vector3f av = null;
        boolean active = false;

        if (rb != null) {
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
            try {
                active = rb.isActive();
            } catch (Throwable ignored) {
                active = true;
            }
        }

        return PhysicsState.evt(
                "bodyId", h.id,
                "surfaceId", h.surfaceId,
                "entity", entityOfSpatial(sp),
                "name", (sp != null ? sp.getName() : null),
                "pos", vec3(p),
                "rot", quat(q),
                "vel", vec3(lv),
                "angVel", vec3(av),
                "active", active,
                "mass", massSafe(rb),
                "kinematic", isKinematicSafe(rb),
                "groups", groupsSafe(rb)
        );
    }

    public static float reducedMassSafe(float ma, float mb) {
        if (!(Float.isFinite(ma) && Float.isFinite(mb))) return 0f;
        if (ma <= 0f || mb <= 0f) return 0f;
        float sum = ma + mb;
        if (!(sum > 1e-6f)) return 0f;
        return (ma * mb) / sum;
    }
}