// FILE: org/foxesworld/kalitech/engine/modules/physics/events/CollisionEventBuilder.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.events;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.ContactAgg;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsJs;
import org.graalvm.polyglot.proxy.ProxyObject;

import static org.foxesworld.kalitech.engine.modules.physics.PhysicsJs.evtJs;

/**
 * Builds unified collision/contact payloads for JS.
 * Eliminates duplicated emitCollision/emitImpact logic.
 */
public final class CollisionEventBuilder {

    private final SurfaceRegistry surfaces;

    public CollisionEventBuilder(SurfaceRegistry surfaces) {
        this.surfaces = surfaces;
    }

    private static boolean isActiveSafe(RigidBodyControl rb) {
        if (rb == null) return false;
        try {
            return rb.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isKinematicSafe(RigidBodyControl rb) {
        if (rb == null) return false;
        try {
            return rb.isKinematic();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static float massSafe(RigidBodyControl rb) {
        if (rb == null) return 0f;
        try {
            return rb.getMass();
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    private static ProxyObject groupsSafe(RigidBodyControl rb) {
        if (rb == null) return null;

        int group = 0;
        int mask = 0;

        try {
            group = rb.getCollisionGroup();
        } catch (Throwable ignored) {
        }
        try {
            mask = rb.getCollideWithGroups();
        } catch (Throwable ignored) {
        }

        if (group == 0 && mask == 0) return null;
        return evtJs("group", group, "mask", mask);
    }

    private String entityOfSurface(int surfaceId) {
        if (surfaces == null || surfaceId <= 0) return null;
        String uuid = surfaces.attachedEntityUuid(surfaceId);
        if (uuid == null || uuid.isBlank()) return null;
        return uuid;
    }

    private static ProxyObject jsVec3SafePos(RigidBodyControl rb) {
        Vector3f p;
        try {
            p = rb != null ? rb.getPhysicsLocation() : null;
        } catch (Throwable ignored) {
            p = null;
        }
        if (p == null) return evtJs("x", 0f, "y", 0f, "z", 0f);
        return PhysicsJs.jsVec3(p);
    }

    private static ProxyObject jsVec3SafeVel(RigidBodyControl rb) {
        Vector3f v;
        try {
            v = rb != null ? rb.getLinearVelocity() : null;
        } catch (Throwable ignored) {
            v = null;
        }
        if (v == null) return evtJs("x", 0f, "y", 0f, "z", 0f);
        return PhysicsJs.jsVec3(v);
    }

    private static ProxyObject jsVec3SafeAngVel(RigidBodyControl rb) {
        Vector3f v;
        try {
            v = rb != null ? rb.getAngularVelocity() : null;
        } catch (Throwable ignored) {
            v = null;
        }
        if (v == null) return evtJs("x", 0f, "y", 0f, "z", 0f);
        return PhysicsJs.jsVec3(v);
    }

    private static ProxyObject jsQuatSafe(RigidBodyControl rb) {
        Quaternion q;
        try {
            q = rb != null ? rb.getPhysicsRotation() : null;
        } catch (Throwable ignored) {
            q = null;
        }
        if (q == null) return evtJs("x", 0f, "y", 0f, "z", 0f, "w", 1f);
        return PhysicsJs.jsQuat(q);
    }

    public ProxyObject contactPayload(ContactAgg agg) {
        if (agg == null || agg.points <= 0) {
            return evtJs(
                    "maxImpulse", 0f,
                    "points", 0,
                    "point", evtJs("x", 0f, "y", 0f, "z", 0f),
                    "normal", evtJs("x", 0f, "y", 1f, "z", 0f)
            );
        }

        float inv = 1f / Math.max(1, agg.points);

        float px = agg.sumPx * inv;
        float py = agg.sumPy * inv;
        float pz = agg.sumPz * inv;

        float nx = agg.sumNx * inv;
        float ny = agg.sumNy * inv;
        float nz = agg.sumNz * inv;

        float nLen2 = nx * nx + ny * ny + nz * nz;
        if (nLen2 > 1e-12f) {
            float invN = 1f / (float) Math.sqrt(nLen2);
            nx *= invN;
            ny *= invN;
            nz *= invN;
        } else {
            nx = 0f;
            ny = 1f;
            nz = 0f;
        }

        return evtJs(
                "maxImpulse", agg.maxImpulse,
                "points", agg.points,
                "point", evtJs("x", px, "y", py, "z", pz),
                "normal", evtJs("x", nx, "y", ny, "z", nz)
        );
    }

    public ProxyObject bodyPayload(PhysicsBodyHandle h) {
        if (h == null) return null;

        RigidBodyControl rb;
        try {
            rb = h.__raw();
        } catch (Throwable ignored) {
            rb = null;
        }

        Spatial sp = null;
        if (surfaces != null) {
            try {
                sp = surfaces.get(h.surfaceId);
            } catch (Throwable ignored) {
            }
        }

        String ent = entityOfSurface(h.surfaceId);

        return evtJs(
                "bodyId", h.id,
                "surfaceId", h.surfaceId,
                "entity", ent,
                "name", (sp != null ? sp.getName() : null),
                "pos", jsVec3SafePos(rb),
                "rot", jsQuatSafe(rb),
                "vel", jsVec3SafeVel(rb),
                "angVel", jsVec3SafeAngVel(rb),
                "active", isActiveSafe(rb),
                "mass", massSafe(rb),
                "kinematic", isKinematicSafe(rb),
                "groups", groupsSafe(rb)
        );
    }
}
