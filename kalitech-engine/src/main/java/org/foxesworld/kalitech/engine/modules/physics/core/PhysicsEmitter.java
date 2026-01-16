// FILE: org/foxesworld/kalitech/engine/modules/physics/internal/PhysicsEmitter.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.core;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.ContactAgg;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.modules.physics.collision.CollisionPairKey.keyA;
import static org.foxesworld.kalitech.engine.modules.physics.collision.CollisionPairKey.keyB;
import static org.foxesworld.kalitech.engine.modules.physics.js.PhysicsJs.*;
import static org.foxesworld.kalitech.engine.modules.physics.util.PhysicsMath.isFinite;

/**
 * Event emission and JS payload building.
 *
 * <p>Designed to be allocation-light and resilient against missing bodies/spatials and transient engine state.</p>
 */
public final class PhysicsEmitter {

    public static final String TOPIC_BODY_MOVE = "engine.physics.body.move";
    public static final String TOPIC_BODY_TELEPORT = "engine.physics.body.teleport";
    public static final String TOPIC_BODY_ADDED = "engine.physics.body.added";
    public static final String TOPIC_BODY_REMOVED = "engine.physics.body.removed";
    public static final String TOPIC_COLL_BEGIN = "engine.physics.collision.begin";
    public static final String TOPIC_COLL_STAY = "engine.physics.collision.stay";
    public static final String TOPIC_COLL_END = "engine.physics.collision.end";
    public static final String TOPIC_IMPACT = "engine.physics.impact";

    private static final float IMPACT_MIN_IMPULSE = 0.25f;
    private static final float IMPACT_MIN_REL_SPEED = 0.20f;

    private final EngineApiImpl engine;
    private final PhysicsRegistry registry;
    private volatile SurfaceRegistry surfaces;

    public PhysicsEmitter(EngineApiImpl engine, PhysicsRegistry registry, SurfaceRegistry surfaces) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
    }

    private static float relSpeedSafe(Vector3f va, Vector3f vb) {
        if (va == null || vb == null) return 0f;
        float dx = va.x - vb.x;
        float dy = va.y - vb.y;
        float dz = va.z - vb.z;
        float l2 = dx * dx + dy * dy + dz * dz;
        if (!Float.isFinite(l2) || l2 <= 0f) return 0f;
        float v = (float) Math.sqrt(l2);
        return Float.isFinite(v) ? v : 0f;
    }

    private static RigidBodyControl safeRaw(PhysicsBodyHandle h) {
        try {
            return h.__raw();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Vector3f safeLinVel(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            return rb.getLinearVelocity();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isHard(RigidBodyControl rb) {
        if (rb == null) return false;
        try {
            Object v = rb.getUserObject();
            if (v instanceof Map<?, ?> m) {
                Object hard = m.get("hard");
                if (hard instanceof Boolean b) return b;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static ProxyObject contactPayload(ContactAgg agg) {
        if (agg == null) return null;

        Vector3f mp = agg.maxPoint;
        Vector3f mn = agg.maxNormal;

        int samples = agg.points;

        ProxyObject avgPoint = null;
        ProxyObject avgNormal = null;

        if (samples > 0) {
            float inv = 1f / (float) samples;
            float apx = agg.sumPx * inv;
            float apy = agg.sumPy * inv;
            float apz = agg.sumPz * inv;

            float anx = agg.sumNx * inv;
            float any = agg.sumNy * inv;
            float anz = agg.sumNz * inv;

            if (Float.isFinite(apx) && Float.isFinite(apy) && Float.isFinite(apz)) {
                avgPoint = evtJs("x", apx, "y", apy, "z", apz);
            }
            if (Float.isFinite(anx) && Float.isFinite(any) && Float.isFinite(anz)) {
                avgNormal = evtJs("x", anx, "y", any, "z", anz);
            }
        }

        return evtJs(
                "impulse", agg.maxImpulse,
                "point", vec3Payload(mp),
                "normal", vec3Payload(mn),
                "avgPoint", avgPoint,
                "avgNormal", avgNormal,
                "samples", samples
        );
    }

    private static ProxyObject vec3Payload(Vector3f v) {
        if (v == null) return evtJs("x", 0.0, "y", 0.0, "z", 0.0);
        return evtJs("x", v.x, "y", v.y, "z", v.z);
    }

    private static boolean isActiveSafe(RigidBodyControl rb) {
        if (rb == null) return false;
        try {
            return rb.isActive();
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

    private static boolean isKinematicSafe(RigidBodyControl rb) {
        if (rb == null) return false;
        try {
            return rb.isKinematic();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int groupsSafe(RigidBodyControl rb) {
        if (rb == null) return 0;
        try {
            return rb.getCollisionGroup();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String entityOfSpatial(Spatial sp) {
        if (sp == null) return null;

        try {
            Object v = sp.getUserData("entityUuid");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        try {
            Object v = sp.getUserData("entityId");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        try {
            Object v = sp.getUserData("uuid");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        return null;
    }

    public void attach(SurfaceRegistry surfaces) {
        this.surfaces = surfaces;
    }

    public ScriptEventBus bus() {
        return engine.getBus();
    }

    public void emitTeleport(PhysicsBodyHandle h, Vector3f pos) {
        bus().emit(TOPIC_BODY_TELEPORT, evtJs(
                "bodyId", h.id,
                "surfaceId", h.surfaceId,
                "entity", entityOfSurface(h.surfaceId),
                "pos", jsVec3(pos)
        ));
    }

    public void emitBodyAdded(RigidBodyControl rb) {
        Integer id = registry.idOfControl(rb);
        if (id == null) return;

        PhysicsBodyHandle h = registry.get(id);
        if (h == null) return;

        SurfaceRegistry s = this.surfaces;
        Spatial entity = (s != null) ? s.get(h.surfaceId) : null;

        Vector3f p = (entity != null) ? entity.getWorldTranslation() : null;

        bus().emit(TOPIC_BODY_ADDED, evtJs(
                "bodyId", h.id,
                "surfaceId", h.surfaceId,
                "entity", entityOfSpatial(entity),
                "scale", (entity != null ? jsVec3Live(entity.getLocalScale()) : null),
                "pos", (p == null ? null : jsVec3Live(p))
        ));
    }

    public void emitBodyRemoved(PhysicsBodyHandle h) {
        bus().emit(TOPIC_BODY_REMOVED, evtJs(
                "bodyId", h.id,
                "surfaceId", h.surfaceId,
                "entity", entityOfSurface(h.surfaceId)
        ));
    }

    public void emitCollision(String topic, long step, float dt, long pairKey, ContactAgg agg) {
        int aId = keyA(pairKey);
        int bId = keyB(pairKey);

        PhysicsBodyHandle a = registry.get(aId);
        PhysicsBodyHandle b = registry.get(bId);
        if (a == null || b == null) return;

        RigidBodyControl ra = safeRaw(a);
        RigidBodyControl rb = safeRaw(b);

        SurfaceRegistry s = this.surfaces;
        Spatial sa = (s != null) ? s.get(a.surfaceId) : null;
        Spatial sb = (s != null) ? s.get(b.surfaceId) : null;

        ProxyObject contact = contactPayload(agg);

        ProxyObject aObj = bodyPayload(a, ra, sa, true);
        ProxyObject bObj = bodyPayload(b, rb, sb, true);

        bus().emit(topic, evtJs(
                "step", step,
                "dt", dt,
                "pairKey", pairKey,
                "a", aObj,
                "b", bObj,
                "contact", contact
        ));
    }

    public void emitImpact(long step, float dt, long pairKey, ContactAgg agg) {
        if (agg == null) return;

        float impulse = agg.maxImpulse;
        if (!isFinite(impulse) || impulse < IMPACT_MIN_IMPULSE) return;

        int aId = keyA(pairKey);
        int bId = keyB(pairKey);

        PhysicsBodyHandle a = registry.get(aId);
        PhysicsBodyHandle b = registry.get(bId);
        if (a == null || b == null) return;

        RigidBodyControl ra = safeRaw(a);
        RigidBodyControl rb = safeRaw(b);
        if (ra == null || rb == null) return;

        float ma = massSafe(ra);
        float mb = massSafe(rb);

        Vector3f va = safeLinVel(ra);
        Vector3f vb = safeLinVel(rb);

        float relSpeed = relSpeedSafe(va, vb);
        if (!isFinite(relSpeed) || relSpeed < IMPACT_MIN_REL_SPEED) return;

        float reducedMass = (ma > 0f && mb > 0f) ? ((ma * mb) / (ma + mb)) : Math.max(ma, mb);
        if (!isFinite(reducedMass) || reducedMass < 0f) reducedMass = 0f;

        float energyApprox = 0.5f * reducedMass * relSpeed * relSpeed;
        if (!isFinite(energyApprox) || energyApprox < 0f) energyApprox = 0f;

        boolean hardA = isHard(ra);
        boolean hardB = isHard(rb);

        SurfaceRegistry s = this.surfaces;
        Spatial sa = (s != null) ? s.get(a.surfaceId) : null;
        Spatial sb = (s != null) ? s.get(b.surfaceId) : null;

        ProxyObject contact = contactPayload(agg);

        ProxyObject aObj = bodyPayload(a, ra, sa, false);
        ProxyObject bObj = bodyPayload(b, rb, sb, false);

        bus().emit(TOPIC_IMPACT, evtJs(
                "step", step,
                "dt", dt,
                "pairKey", pairKey,
                "a", aObj,
                "b", bObj,
                "contact", contact,
                "impulse", impulse,
                "relSpeed", relSpeed,
                "reducedMass", reducedMass,
                "energyApprox", energyApprox,
                "hardA", hardA,
                "hardB", hardB,
                "hardSide", (hardA && hardB) ? "both" : (hardA ? "a" : "b")
        ));
    }

    private ProxyObject bodyPayload(PhysicsBodyHandle h, RigidBodyControl rb, Spatial sp, boolean extended) {
        String entity = entityOfSpatial(sp);
        String name = (sp != null) ? sp.getName() : null;

        if (extended) {
            return evtJs(
                    "bodyId", h.id,
                    "surfaceId", h.surfaceId,
                    "entity", entity,
                    "name", name,
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

        return evtJs(
                "bodyId", h.id,
                "surfaceId", h.surfaceId,
                "entity", entity,
                "name", name,
                "pos", jsVec3SafePos(rb),
                "vel", jsVec3SafeVel(rb),
                "mass", massSafe(rb),
                "kinematic", isKinematicSafe(rb),
                "groups", groupsSafe(rb)
        );
    }

    private Object jsVec3SafePos(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Vector3f v = rb.getPhysicsLocation();
            if (v != null) return jsVec3(v);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object jsQuatSafe(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Quaternion q = rb.getPhysicsRotation();
            if (q != null) return jsQuat(q);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object jsVec3SafeVel(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Vector3f v = rb.getLinearVelocity();
            if (v != null) return jsVec3(v);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object jsVec3SafeAngVel(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Vector3f v = rb.getAngularVelocity();
            if (v != null) return jsVec3(v);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String entityOfSurface(int surfaceId) {
        SurfaceRegistry s = this.surfaces;
        if (surfaceId <= 0 || s == null) return null;
        Spatial sp = s.get(surfaceId);
        return entityOfSpatial(sp);
    }
}