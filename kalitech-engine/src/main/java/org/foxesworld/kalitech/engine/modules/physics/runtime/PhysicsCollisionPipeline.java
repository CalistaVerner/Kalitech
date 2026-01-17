// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/PhysicsCollisionPipeline.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.ContactAgg;
import org.foxesworld.kalitech.engine.modules.physics.LongContactMap;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsRegistry;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsService;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.util.LongHashSet;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.foxesworld.kalitech.engine.modules.physics.PhysicsJs.*;
import static org.foxesworld.kalitech.engine.modules.physics.collision.CollisionPairKey.*;

public final class PhysicsCollisionPipeline {

    private static final float IMPACT_MIN_IMPULSE = 0.25f;
    private static final float IMPACT_MIN_REL_SPEED = 0.20f;

    private final Logger log;
    private final PhysicsService svc;

    private final AtomicLong stepCounter = new AtomicLong(0);
    private final AtomicBoolean collisionListenerBound = new AtomicBoolean(false);
    private final AtomicBoolean tickListenerBound = new AtomicBoolean(false);
    private final LongContactMap currContacts = new LongContactMap(4096);
    private volatile float lastDt = 0f;
    private volatile long currentStep = 0L;
    private final LongHashSet.LongConsumer emitStayConsumer = k -> {
        if (k == 0L) return;
        emitCollision("engine.physics.collision.stay", currentStep, lastDt, k, currContacts.get(k));
    };
    private volatile PhysicsBodyStateTracker bodyStateTracker;
    private LongHashSet currPairs = new LongHashSet(4096);
    private final LongHashSet.LongConsumer emitEndConsumer = k -> {
        if (k == 0L) return;
        if (currPairs.contains(k)) return;
        emitCollision("engine.physics.collision.end", currentStep, lastDt, k, null);
    };
    private LongHashSet prevPairs = new LongHashSet(4096);
    private final LongHashSet.LongConsumer emitBeginConsumer = k -> {
        if (k == 0L) return;
        if (prevPairs.contains(k)) return;

        ContactAgg agg = currContacts.get(k);
        emitCollision("engine.physics.collision.begin", currentStep, lastDt, k, agg);
        emitImpact(currentStep, lastDt, k, agg);
    };

    public PhysicsCollisionPipeline(PhysicsService svc, Logger log) {
        this.svc = Objects.requireNonNull(svc, "svc");
        this.log = Objects.requireNonNull(log, "log");
    }

    private static ProxyObject jsVec3SafePos(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Vector3f v = rb.getPhysicsLocation();
            return (v == null) ? null : jsVec3(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ProxyObject jsVec3SafeVel(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Vector3f v = rb.getLinearVelocity();
            return (v == null) ? null : jsVec3(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ProxyObject jsVec3SafeAngVel(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Vector3f v = rb.getAngularVelocity();
            return (v == null) ? null : jsVec3(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ProxyObject jsQuatSafe(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Quaternion q = rb.getPhysicsRotation();
            return (q == null) ? null : jsQuat(q);
        } catch (Throwable ignored) {
            return null;
        }
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

    private static float relativeSpeedApprox(RigidBodyControl a, RigidBodyControl b) {
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

    private static float reducedMassSafe(float ma, float mb) {
        if (!(Float.isFinite(ma) && Float.isFinite(mb))) return 0f;
        if (ma <= 0f || mb <= 0f) return 0f;
        float sum = ma + mb;
        if (!(sum > 1e-6f)) return 0f;
        return (ma * mb) / sum;
    }

    private static boolean hasCollision(RigidBodyControl rb) {
        if (rb == null) return false;
        try {
            return rb.getCollisionShape() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isHardSurface(RigidBodyControl rb, Spatial sp) {
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

    public long step() {
        return stepCounter.get();
    }

    public float lastDt() {
        return lastDt;
    }

    public void bind(PhysicsSpace sp) {
        Objects.requireNonNull(sp, "sp");
        ensureCollisionListenerBound(sp);
        ensureTickListenerBound(sp);
    }

    /**
     * Optional tracker to emit body state after each physics step.
     */
    public void setBodyStateTracker(PhysicsBodyStateTracker tracker) {
        this.bodyStateTracker = tracker;
    }

    private ScriptEventBus bus() {
        return svc.engine().getBus();
    }

    private PhysicsRegistry registry() {
        return svc.registry();
    }

    private SurfaceRegistry surfaces() {
        return svc.surfaces();
    }

    private void ensureCollisionListenerBound(PhysicsSpace sp) {
        if (!collisionListenerBound.compareAndSet(false, true)) return;

        sp.addCollisionListener(new PhysicsCollisionListener() {
            @Override
            public void collision(PhysicsCollisionEvent e) {
                if (e == null) return;

                int a = registry().bodyIdFromCollisionObject(e.getObjectA());
                int b = registry().bodyIdFromCollisionObject(e.getObjectB());
                long key = pairKey(a, b);
                if (key == 0L) return;

                currPairs.add(key);

                ContactAgg agg = currContacts.getOrCreate(key);
                if (agg == null) return;

                float impulse = 0f;
                Vector3f point = null;
                Vector3f normal = null;

                try {
                    impulse = e.getAppliedImpulse();
                } catch (Throwable ignored) {
                }

                try {
                    Vector3f pa = e.getPositionWorldOnA();
                    Vector3f pb = e.getPositionWorldOnB();
                    if (pa != null && pb != null) point = pa.add(pb).multLocal(0.5f);
                    else point = (pa != null) ? pa : pb;
                } catch (Throwable ignored) {
                }

                try {
                    normal = e.getNormalWorldOnB();
                } catch (Throwable ignored) {
                }

                agg.add(impulse, point, normal);
            }
        });
    }

    private void ensureTickListenerBound(PhysicsSpace sp) {
        if (!tickListenerBound.compareAndSet(false, true)) return;

        sp.addTickListener(new PhysicsTickListener() {
            @Override
            public void prePhysicsTick(PhysicsSpace space, float timeStep) {
                svc.flushPendingAddNow();
            }

            @Override
            public void physicsTick(PhysicsSpace space, float timeStep) {
                try {
                    flush(timeStep);
                } catch (Throwable t) {
                    log.error("[physics] collision pipeline flush failed", t);
                }
            }
        });
    }

    private void flush(float dt) {
        lastDt = dt;
        long step = stepCounter.incrementAndGet();
        currentStep = step;

        currPairs.forEach(emitBeginConsumer);
        currPairs.forEach(emitStayConsumer);
        prevPairs.forEach(emitEndConsumer);

        LongHashSet tmp = prevPairs;
        prevPairs = currPairs;
        currPairs = tmp;
        currPairs.clear();
        currContacts.clear();

        bus().emit("engine.physics.postStep", evtJs("step", step, "dt", dt));

        PhysicsBodyStateTracker tracker = this.bodyStateTracker;
        if (tracker != null) {
            try {
                tracker.emit(step, dt);
            } catch (Throwable t) {
                log.error("[physics] body state tracker emit failed", t);
            }
        }
    }

    private ProxyObject contactPayload(ContactAgg agg) {
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

    private void emitCollision(String topic, long step, float dt, long k, ContactAgg agg) {
        int aId = keyA(k);
        int bId = keyB(k);

        PhysicsBodyHandle a = registry().get(aId);
        PhysicsBodyHandle b = registry().get(bId);
        if (a == null || b == null) return;

        RigidBodyControl ra;
        RigidBodyControl rb;
        try {
            ra = a.__raw();
        } catch (Throwable ignored) {
            ra = null;
        }
        try {
            rb = b.__raw();
        } catch (Throwable ignored) {
            rb = null;
        }

        Spatial sa = null;
        Spatial sb = null;
        SurfaceRegistry sr = surfaces();
        if (sr != null) {
            try {
                sa = sr.get(a.surfaceId);
            } catch (Throwable ignored) {
            }
            try {
                sb = sr.get(b.surfaceId);
            } catch (Throwable ignored) {
            }
        }

        ProxyObject contact = contactPayload(agg);

        ProxyObject aObj = evtJs(
                "bodyId", a.id,
                "surfaceId", a.surfaceId,
                "entity", PhysicsEntityResolver.entityOfSpatial(sa),
                "name", (sa != null ? sa.getName() : null),
                "pos", jsVec3SafePos(ra),
                "rot", jsQuatSafe(ra),
                "vel", jsVec3SafeVel(ra),
                "angVel", jsVec3SafeAngVel(ra),
                "active", isActiveSafe(ra),
                "mass", massSafe(ra),
                "kinematic", isKinematicSafe(ra),
                "groups", groupsSafe(ra)
        );

        ProxyObject bObj = evtJs(
                "bodyId", b.id,
                "surfaceId", b.surfaceId,
                "entity", PhysicsEntityResolver.entityOfSpatial(sb),
                "name", (sb != null ? sb.getName() : null),
                "pos", jsVec3SafePos(rb),
                "rot", jsQuatSafe(rb),
                "vel", jsVec3SafeVel(rb),
                "angVel", jsVec3SafeAngVel(rb),
                "active", isActiveSafe(rb),
                "mass", massSafe(rb),
                "kinematic", isKinematicSafe(rb),
                "groups", groupsSafe(rb)
        );

        bus().emit(topic, evtJs(
                "step", step,
                "dt", dt,
                "pairKey", k,
                "a", aObj,
                "b", bObj,
                "contact", contact,
                "impulseApprox", agg == null ? 0f : agg.maxImpulse
        ));
    }

    private void emitImpact(long step, float dt, long k, ContactAgg agg) {
        if (agg == null || agg.points <= 0) return;

        int aId = keyA(k);
        int bId = keyB(k);

        PhysicsBodyHandle a = registry().get(aId);
        PhysicsBodyHandle b = registry().get(bId);
        if (a == null || b == null) return;

        RigidBodyControl ra;
        RigidBodyControl rb;
        try {
            ra = a.__raw();
        } catch (Throwable ignored) {
            ra = null;
        }
        try {
            rb = b.__raw();
        } catch (Throwable ignored) {
            rb = null;
        }

        Spatial sa = null;
        Spatial sb = null;
        SurfaceRegistry sr = surfaces();
        if (sr != null) {
            try {
                sa = sr.get(a.surfaceId);
            } catch (Throwable ignored) {
            }
            try {
                sb = sr.get(b.surfaceId);
            } catch (Throwable ignored) {
            }
        }

        boolean hardA = isHardSurface(ra, sa);
        boolean hardB = isHardSurface(rb, sb);
        if (!hardA && !hardB) return;

        float impulse = agg.maxImpulse;
        float relSpeed = relativeSpeedApprox(ra, rb);

        if (!(Float.isFinite(impulse) && impulse >= IMPACT_MIN_IMPULSE)) return;
        if (!(Float.isFinite(relSpeed) && relSpeed >= IMPACT_MIN_REL_SPEED)) return;

        float ma = massSafe(ra);
        float mb = massSafe(rb);
        float reducedMass = reducedMassSafe(ma, mb);
        float energyApprox = 0.5f * reducedMass * relSpeed * relSpeed;

        bus().emit("engine.physics.impact", evtJs(
                "step", step,
                "dt", dt,
                "pairKey", k,
                "a", evtJs("bodyId", a.id, "surfaceId", a.surfaceId),
                "b", evtJs("bodyId", b.id, "surfaceId", b.surfaceId),
                "contact", contactPayload(agg),
                "impulse", impulse,
                "relSpeed", relSpeed,
                "reducedMass", reducedMass,
                "energyApprox", energyApprox,
                "hardA", hardA,
                "hardB", hardB,
                "hardSide", (hardA && hardB) ? "both" : (hardA ? "a" : "b")
        ));
    }

    public void reset() {
        currPairs.clear();
        prevPairs.clear();
        currContacts.clear();
        lastDt = 0f;
        currentStep = 0L;
        stepCounter.set(0L);
    }
}