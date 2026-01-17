// FILE: org/foxesworld/kalitech/engine/api/impl/PhysicsApiImpl.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.BodyStateStore;
import org.foxesworld.kalitech.engine.modules.physics.ContactAgg;
import org.foxesworld.kalitech.engine.modules.physics.LongContactMap;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsColliderFactory;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsRegistry;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.util.LongHashSet;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.foxesworld.kalitech.engine.modules.physics.PhysicsJs.*;
import static org.foxesworld.kalitech.engine.modules.physics.collision.CollisionPairKey.*;
import static org.foxesworld.kalitech.engine.modules.physics.util.PhysicsMath.clampPositive;
import static org.foxesworld.kalitech.engine.modules.physics.util.PhysicsMath.isFinite;

public final class PhysicsApiImpl extends AbstractApiModule implements PhysicsApi {

    private static final Logger log = LogManager.getLogger(PhysicsApiImpl.class);

    private static final int ADD_FLUSH_MAX_PER_TICK = 128;

    // Body state tracking
    private static final float MOVE_POS_EPS = 0.0025f;
    private static final float MOVE_ROT_EPS = 0.0010f;
    private static final float MOVE_VEL_EPS = 0.01f;
    private static final int MOVE_EVENT_MAX_PER_STEP = 512;

    // Impact thresholds
    private static final float IMPACT_MIN_IMPULSE = 0.25f;
    private static final float IMPACT_MIN_REL_SPEED = 0.20f;

    private final AtomicLong physicsStepCounter = new AtomicLong(0);
    private final AtomicBoolean collisionListenerBound = new AtomicBoolean(false);
    private final AtomicBoolean tickListenerBound = new AtomicBoolean(false);

    private volatile float lastDt = 0f;

    private SimpleApplication app;
    private SurfaceRegistry surfaces;

    // Centralized registry/state (no boxing / identity indexing)
    private final PhysicsRegistry registry = new PhysicsRegistry(log);
    private final BodyStateStore bodyStates = new BodyStateStore(2048);

    // Batched add to PhysicsSpace
    private final ConcurrentLinkedQueue<RigidBodyControl> pendingAdd = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean addFlushScheduled = new AtomicBoolean(false);

    // Collision aggregation per step
    private final LongContactMap currContacts = new LongContactMap(4096);
    private LongHashSet currPairs = new LongHashSet(4096);
    // Shape cache (mesh -> shape)
    private final ConcurrentHashMap<ShapeKey, CollisionShape> shapeCache = new ConcurrentHashMap<>();
    private LongHashSet prevPairs = new LongHashSet(4096);

    private final LongHashSet.LongConsumer emitStayConsumer = k -> {
        if (k == 0L) return;
        emitCollision("engine.physics.collision.stay", physicsStepCounter.get() + 1, lastDt, k, currContacts.get(k));
    };

    private final LongHashSet.LongConsumer emitEndConsumer = k -> {
        if (k == 0L) return;
        if (currPairs.contains(k)) return;
        emitCollision("engine.physics.collision.end", physicsStepCounter.get() + 1, lastDt, k, null);
    };
    private final LongHashSet.LongConsumer emitBeginConsumer = k -> {
        if (k == 0L) return;
        if (prevPairs.contains(k)) return;

        ContactAgg agg = currContacts.get(k);
        emitCollision("engine.physics.collision.begin", physicsStepCounter.get() + 1, lastDt, k, agg);
        emitImpact(physicsStepCounter.get() + 1, lastDt, k, agg);
    };

    public PhysicsApiImpl() {
        super("physics", "Physics", "1.0.0");
    }

    public PhysicsApiImpl(EngineApiImpl engine, SurfaceRegistry surfaces) {
        this();
        if (engine == null) throw new NullPointerException("engine");
        if (surfaces == null) throw new NullPointerException("surfaces");
        super.attach(new ApiContext(engine));
        this.app = engine.getApp();
        this.surfaces = surfaces;
    }

    private static Map<String, Object> hitObj(
            boolean hit,
            int bodyId,
            int surfaceId,
            float fraction,
            float distance,
            Vector3f point,
            Vector3f normal
    ) {
        Map<String, Object> m = new HashMap<>();
        m.put("hit", hit);
        m.put("bodyId", bodyId);
        m.put("surfaceId", surfaceId);
        m.put("fraction", fraction);
        m.put("distance", distance);
        m.put("point", new PhysicsRayHit.Vec3(point.x, point.y, point.z));
        m.put("normal", normal == null
                ? new PhysicsRayHit.Vec3(0, 1, 0)
                : new PhysicsRayHit.Vec3(normal.x, normal.y, normal.z));
        return m;
    }

    private static String colliderTypeOf(Object colliderCfg) {
        if (colliderCfg == null) return null;

        if (colliderCfg instanceof Value v) {
            try {
                if (v.hasMembers() && v.hasMember("type")) {
                    Value t = v.getMember("type");
                    return t == null ? null : t.asString();
                }
            } catch (Throwable ignored) {
            }
        }

        if (colliderCfg instanceof Map<?, ?> m) {
            Object t = m.get("type");
            return t == null ? null : String.valueOf(t);
        }

        return null;
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.app = ctx.app;
        this.surfaces = ctx.engine.getSurfaceRegistry();
    }

    @Override
    public void detach() {
        try {
            __clearAll();
        } catch (Throwable ignored) {
        }
        this.app = null;
        this.surfaces = null;
        super.detach();
    }

    // ----------------------------------------------------------------------
    // Safety + helpers
    // ----------------------------------------------------------------------

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

    private static float safeImpulseApprox(ProxyObject contact) {
        try {
            Object mi = contact.getMember("maxImpulse");
            if (mi instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }
        return 0f;
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

    private ScriptEventBus bus() {
        return engine.getBus();
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

    private PhysicsSpace space() {
        PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            throw new IllegalStateException(
                    "[physics] PhysicsSpace not bound. RuntimeAppState must attach BulletAppState and call engineApi.__setPhysicsSpace(space)."
            );
        }
        ensureCollisionListenerBound(s);
        ensureTickListenerBound(s);
        return s;
    }

    // ----------------------------------------------------------------------
    // Pending adds
    // ----------------------------------------------------------------------

    private void enqueueAddToSpace(RigidBodyControl rb) {
        if (rb == null) return;
        pendingAdd.add(rb);
        scheduleAddFlush();
    }

    private void scheduleAddFlush() {
        if (!addFlushScheduled.compareAndSet(false, true)) return;

        app.enqueue(() -> {
            try {
                flushPendingAdd();
            } finally {
                addFlushScheduled.set(false);
                if (!pendingAdd.isEmpty()) scheduleAddFlush();
            }
            return null;
        });
    }

    private String entityOfSurface(int surfaceId) {
        if (surfaceId <= 0 || surfaces == null) return null;
        Spatial sp = surfaces.get(surfaceId);
        return entityOfSpatial(sp);
    }

    // ----------------------------------------------------------------------
    // Collision listeners + tick flush
    // ----------------------------------------------------------------------

    private void flushPendingAdd() {
        PhysicsSpace sp = engine.__getPhysicsSpaceOrNull();
        if (sp == null) return;

        ensureCollisionListenerBound(sp);
        ensureTickListenerBound(sp);

        int n = 0;
        RigidBodyControl rb;
        while (n < ADD_FLUSH_MAX_PER_TICK && (rb = pendingAdd.poll()) != null) {
            try {
                sp.add(rb);

                Integer id = registry.idOfControl(rb);
                if (id != null) {
                    PhysicsBodyHandle h = registry.get(id);
                    if (h != null) {
                        Spatial s = surfaces != null ? surfaces.get(h.surfaceId) : null;
                        Vector3f p = (s != null) ? s.getWorldTranslation() : null;

                        bus().emit("engine.physics.body.added", evtJs(
                                "bodyId", h.id,
                                "surfaceId", h.surfaceId,
                                "entity", entityOfSurface(h.surfaceId),
                                "pos", (p == null ? null : jsVec3Live(p))
                        ));
                    }
                }
            } catch (Throwable t) {
                log.error("[physics] addToSpace failed", t);
            }
            n++;
        }
    }

    private void ensureCollisionListenerBound(PhysicsSpace sp) {
        if (sp == null) return;
        if (!collisionListenerBound.compareAndSet(false, true)) return;

        sp.addCollisionListener(new PhysicsCollisionListener() {
            @Override
            public void collision(PhysicsCollisionEvent e) {
                if (e == null) return;

                int a = registry.bodyIdFromCollisionObject(e.getObjectA());
                int b = registry.bodyIdFromCollisionObject(e.getObjectB());
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

    private long flushCollisionInternal(float timeStep) {
        lastDt = timeStep;

        long step = physicsStepCounter.incrementAndGet();

        currPairs.forEach(emitBeginConsumer);
        currPairs.forEach(emitStayConsumer);
        prevPairs.forEach(emitEndConsumer);

        LongHashSet tmp = prevPairs;
        prevPairs = currPairs;
        currPairs = tmp;
        currPairs.clear();
        currContacts.clear();

        bus().emit("engine.physics.postStep", evtJs("step", step, "dt", timeStep));
        return step;
    }

    // ----------------------------------------------------------------------
    // Collision emit payload
    // ----------------------------------------------------------------------

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

    private void ensureTickListenerBound(PhysicsSpace sp) {
        if (sp == null) return;
        if (!tickListenerBound.compareAndSet(false, true)) return;

        sp.addTickListener(new PhysicsTickListener() {
            @Override
            public void prePhysicsTick(PhysicsSpace space, float timeStep) {
                flushPendingAdd();
            }

            @Override
            public void physicsTick(PhysicsSpace space, float timeStep) {
                long step;
                try {
                    step = flushCollisionInternal(timeStep);
                } catch (Throwable t) {
                    log.error("[physics] physicsTick collision flush failed", t);
                    return;
                }

                try {
                    emitBodyStateEvents(step, timeStep);
                } catch (Throwable t) {
                    log.error("[physics] physicsTick body state emit failed", t);
                }
            }
        });

        log.info("[physics] tick listener bound (collision begin/stay/end + postStep + body state)");
    }

    private void emitCollision(String topic, long step, float dt, long k, ContactAgg agg) {
        int aId = keyA(k);
        int bId = keyB(k);

        PhysicsBodyHandle a = registry.get(aId);
        PhysicsBodyHandle b = registry.get(bId);
        if (a == null || b == null) return;

        RigidBodyControl ra = null;
        RigidBodyControl rb = null;
        Spatial sa = null;
        Spatial sb = null;

        try {
            ra = a.__raw();
        } catch (Throwable ignored) {
        }
        try {
            rb = b.__raw();
        } catch (Throwable ignored) {
        }

        if (surfaces != null) {
            try {
                sa = surfaces.get(a.surfaceId);
            } catch (Throwable ignored) {
            }
            try {
                sb = surfaces.get(b.surfaceId);
            } catch (Throwable ignored) {
            }
        }

        String aEnt = entityOfSpatial(sa);
        String bEnt = entityOfSpatial(sb);

        ProxyObject contact = contactPayload(agg);

        ProxyObject aObj = evtJs(
                "bodyId", a.id,
                "surfaceId", a.surfaceId,
                "entity", aEnt,
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
                "entity", bEnt,
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
                "impulseApprox", safeImpulseApprox(contact)
        ));
    }

    // ----------------------------------------------------------------------
    // Body state events
    // ----------------------------------------------------------------------

    private void emitImpact(long step, float dt, long k, ContactAgg agg) {
        if (agg == null || agg.points <= 0) return;

        int aId = keyA(k);
        int bId = keyB(k);

        PhysicsBodyHandle a = registry.get(aId);
        PhysicsBodyHandle b = registry.get(bId);
        if (a == null || b == null) return;

        RigidBodyControl ra = null;
        RigidBodyControl rb = null;
        Spatial sa = null;
        Spatial sb = null;

        try {
            ra = a.__raw();
        } catch (Throwable ignored) {
        }
        try {
            rb = b.__raw();
        } catch (Throwable ignored) {
        }

        if (surfaces != null) {
            try {
                sa = surfaces.get(a.surfaceId);
            } catch (Throwable ignored) {
            }
            try {
                sb = surfaces.get(b.surfaceId);
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

        String aEnt = entityOfSpatial(sa);
        String bEnt = entityOfSpatial(sb);

        ProxyObject aObj = evtJs(
                "bodyId", a.id,
                "surfaceId", a.surfaceId,
                "entity", aEnt,
                "name", (sa != null ? sa.getName() : null),
                "pos", jsVec3SafePos(ra),
                "rot", jsQuatSafe(ra),
                "vel", jsVec3SafeVel(ra),
                "angVel", jsVec3SafeAngVel(ra),
                "active", isActiveSafe(ra),
                "mass", ma,
                "kinematic", isKinematicSafe(ra),
                "groups", groupsSafe(ra)
        );

        ProxyObject bObj = evtJs(
                "bodyId", b.id,
                "surfaceId", b.surfaceId,
                "entity", bEnt,
                "name", (sb != null ? sb.getName() : null),
                "pos", jsVec3SafePos(rb),
                "rot", jsQuatSafe(rb),
                "vel", jsVec3SafeVel(rb),
                "angVel", jsVec3SafeAngVel(rb),
                "active", isActiveSafe(rb),
                "mass", mb,
                "kinematic", isKinematicSafe(rb),
                "groups", groupsSafe(rb)
        );

        ProxyObject contact = contactPayload(agg);

        bus().emit("engine.physics.impact", evtJs(
                "step", step,
                "dt", dt,
                "pairKey", k,
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

    // ----------------------------------------------------------------------
    // Public API: debug/gravity
    // ----------------------------------------------------------------------

    private void emitBodyStateEvents(long step, float dt) {
        int emitted = 0;

        for (var e : registry.entries()) {
            PhysicsBodyHandle h = e.value();
            if (h == null) continue;

            RigidBodyControl rb;
            try {
                rb = h.__raw();
            } catch (Throwable ignored) {
                continue;
            }
            if (rb == null) continue;

            //boolean changed = bodyStates.updateAndCheckChanged(h.id, rb, MOVE_POS_EPS, MOVE_ROT_EPS, MOVE_VEL_EPS);
            //if (!changed) continue;

            if (emitted++ >= MOVE_EVENT_MAX_PER_STEP) return;

            bus().emit("engine.physics.body.move", evtJs(
                    "step", step,
                    "dt", dt,
                    "bodyId", h.id,
                    "surfaceId", h.surfaceId,
                    "entity", entityOfSurface(h.surfaceId),
                    "pos", jsVec3SafePos(rb),
                    "rot", jsQuatSafe(rb),
                    "vel", jsVec3SafeVel(rb),
                    "angVel", jsVec3SafeAngVel(rb),
                    "active", isActiveSafe(rb)
            ));
        }
    }

    @HostAccess.Export
    @Override
    public void debug(boolean enabled) {
        BulletAppState b = app.getStateManager().getState(BulletAppState.class);
        if (b == null) {
            log.warn("[physics] debug({}) ignored: BulletAppState not attached", enabled);
            return;
        }
        b.setDebugEnabled(enabled);
    }

    @HostAccess.Export
    @Override
    public void gravity(Object vec3) {
        PhysicsSpace space = space();
        Vector3f g = PhysicsValueParsers.vec3(vec3, 0, -9.81f, 0);
        space.setGravity(g);
    }

    // ----------------------------------------------------------------------
    // Public API: handles/lifecycle
    // ----------------------------------------------------------------------

    public void __cleanupSurface(int surfaceId) {
        if (surfaceId <= 0) return;
        int id = registry.bodyOfSurface(surfaceId);
        if (id > 0) remove(id);
    }

    @HostAccess.Export
    public int bodyOfSurface(int surfaceId) {
        return registry.bodyOfSurface(surfaceId);
    }

    @HostAccess.Export
    public PhysicsBodyHandle handle(int bodyId) {
        return registry.get(bodyId);
    }

    @HostAccess.Export
    public boolean exists(int bodyId) {
        return registry.exists(bodyId);
    }

    private PhysicsBodyHandle requireHandle(Object handleOrId, String where) {
        return registry.requireHandle(handleOrId, where);
    }

    @HostAccess.Export
    @Override
    public PhysicsBodyHandle body(Object cfg) {
        space();

        if (cfg == null) throw new IllegalArgumentException("physics.body(cfg) cfg is required");

        int surfaceId = resolveSurfaceId(cfg);
        if (surfaceId <= 0) throw new IllegalArgumentException("physics.body: surface id is required");

        Spatial spatial = surfaces.get(surfaceId);
        if (spatial == null) throw new IllegalStateException("physics.body: unknown surfaceId=" + surfaceId);

        PhysicsBodyHandle existing = registry.getExistingBySurface(surfaceId);
        if (existing != null) return existing;

        float mass = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mass"), 0.0);
        boolean dynamic = mass > 0f;

        Object colliderCfg = PhysicsValueParsers.member(cfg, "collider");

        CollisionShape shape;
        if (colliderCfg == null) {
            shape = defaultShapeForSpatial(spatial, dynamic);
        } else {
            String type = colliderTypeOf(colliderCfg);
            if (dynamic && "mesh".equalsIgnoreCase(type)) {
                throw new IllegalArgumentException(
                        "physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). Use collider.type='dynamicMesh' or primitive collider."
                );
            }
            shape = PhysicsColliderFactory.create(colliderCfg, spatial);
        }

        RigidBodyControl rb = new RigidBodyControl(shape, mass);

        rb.setFriction((float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "friction"), 0.8));
        rb.setRestitution((float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "restitution"), 0.1));

        Object damping = PhysicsValueParsers.member(cfg, "damping");
        if (damping != null) {
            double ld = PhysicsValueParsers.asNum(PhysicsValueParsers.member(damping, "linear"), 0.0);
            double ad = PhysicsValueParsers.asNum(PhysicsValueParsers.member(damping, "angular"), 0.0);
            rb.setDamping((float) ld, (float) ad);
        } else {
            rb.setDamping(0.05f, 0.1f);
        }

        boolean kinematic = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "kinematic"), false);
        rb.setKinematic(kinematic);

        boolean lockRot = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "lockRotation"), false);
        if (lockRot) rb.setAngularFactor(0f);

        if (dynamic && !kinematic) {
            float ccdMotionThreshold = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ccdMotionThreshold"), 0.001);
            float ccdRadius = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ccdSweptSphereRadius"), 0.20);
            rb.setCcdMotionThreshold(Math.max(0.0f, ccdMotionThreshold));
            rb.setCcdSweptSphereRadius(Math.max(0.0f, ccdRadius));
        }

        spatial.addControl(rb);
        enqueueAddToSpace(rb);

        int id = registry.nextId();
        PhysicsBodyHandle handle = new PhysicsBodyHandle(id, surfaceId, rb);

        registry.put(handle);
        registry.indexCollisionObject(handle);

        bus().emit("engine.physics.body.create", evtJs(
                "bodyId", id,
                "surfaceId", surfaceId,
                "entity", entityOfSurface(surfaceId),
                "mass", mass,
                "kinematic", kinematic,
                "lockRotation", lockRot
        ));

        log.debug("[physics] body created id={} surfaceId={} mass={} kinematic={} lockRotation={}", id, surfaceId, mass, kinematic, lockRot);
        return handle;
    }

    @HostAccess.Export
    @Override
    public void remove(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.remove(body)");
        remove(h.id);
    }

    // ----------------------------------------------------------------------
    // Public API: queries/motion
    // ----------------------------------------------------------------------

    @HostAccess.Export
    public void remove(int id) {
        if (id <= 0) return;

        PhysicsBodyHandle h = registry.remove(id);
        if (h == null) return;

        registry.unindexCollisionObject(h);
        registry.removeSurfaceBinding(h.surfaceId, h.id);
        bodyStates.remove(h.id);

        bus().emit("engine.physics.body.remove", evtJs(
                "bodyId", h.id,
                "surfaceId", h.surfaceId,
                "entity", entityOfSurface(h.surfaceId)
        ));

        RigidBodyControl rb;
        try {
            rb = h.__raw();
        } catch (Throwable ignored) {
            rb = null;
        }

        if (rb != null) {
            try {
                pendingAdd.remove(rb);
            } catch (Throwable ignored) {
            }

            PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
            if (s != null) {
                try {
                    s.remove(rb);
                } catch (Throwable ignored) {
                }
            }

            try {
                Spatial sp = surfaces != null ? surfaces.get(h.surfaceId) : null;
                if (sp != null) sp.removeControl(rb);
            } catch (Throwable ignored) {
            }
        }

        log.debug("[physics] body removed id={} surfaceId={}", id, h.surfaceId);
    }

    @HostAccess.Export
    public Object position(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.position()");
        Vector3f p = h.__raw().getPhysicsLocation();
        return new PhysicsRayHit.Vec3(p.x, p.y, p.z);
    }

    @HostAccess.Export
    public Object velocity(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.velocity()");
        Vector3f v = h.__raw().getLinearVelocity();
        return new PhysicsRayHit.Vec3(v.x, v.y, v.z);
    }

    @HostAccess.Export
    public void velocity(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.velocity(v)");
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().setLinearVelocity(v);
    }

    @HostAccess.Export
    public void yaw(Object handleOrId, double yaw) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.yaw(yaw)");
        RigidBodyControl rb = h.__raw();

        Quaternion q = new Quaternion();
        q.fromAngles(0f, (float) yaw, 0f);

        rb.setPhysicsRotation(q);
        rb.setAngularVelocity(Vector3f.ZERO);
    }

    @HostAccess.Export
    public void applyImpulse(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.applyImpulse(impulse)");
        Vector3f imp = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyImpulse(imp, Vector3f.ZERO);
    }

    @HostAccess.Export
    public void lockRotation(Object handleOrId, boolean lock) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.lockRotation(lock)");
        RigidBodyControl rb = h.__raw();
        if (lock) {
            rb.setAngularFactor(0f);
            rb.setAngularVelocity(Vector3f.ZERO);
        } else {
            rb.setAngularFactor(1f);
        }
    }

    @HostAccess.Export
    public void setKinematic(Object handleOrId, boolean kinematic) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.setKinematic(kinematic)");
        RigidBodyControl rb = h.__raw();
        rb.setKinematic(kinematic);
        try {
            rb.activate();
        } catch (Throwable ignored) {
        }
    }

    @HostAccess.Export
    public void collisionGroups(Object handleOrId, int group, int mask) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.collisionGroups(group,mask)");
        RigidBodyControl rb = h.__raw();
        rb.setCollisionGroup(group);
        rb.setCollideWithGroups(mask);
    }

    @HostAccess.Export
    public void applyCentralForce(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.applyCentralForce(force)");
        Vector3f f = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyCentralForce(f);
    }

    @HostAccess.Export
    public void applyTorque(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.applyTorque(torque)");
        Vector3f t = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyTorque(t);
    }

    @HostAccess.Export
    public Object angularVelocity(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.angularVelocity()");
        Vector3f v = h.__raw().getAngularVelocity();
        return new PhysicsRayHit.Vec3(v.x, v.y, v.z);
    }

    @HostAccess.Export
    public void angularVelocity(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.angularVelocity(v)");
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().setAngularVelocity(v);
    }

    @HostAccess.Export
    public void clearForces(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.clearForces()");
        RigidBodyControl rb = h.__raw();
        rb.clearForces();
        rb.setAngularVelocity(Vector3f.ZERO);
        rb.setLinearVelocity(Vector3f.ZERO);
    }

    // ----------------------------------------------------------------------
    // Ray API
    // ----------------------------------------------------------------------

    @HostAccess.Export
    public void warp(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.warp(pos)");
        Vector3f p = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        RigidBodyControl rb = h.__raw();
        rb.setPhysicsLocation(p);
        rb.setLinearVelocity(Vector3f.ZERO);
        rb.setAngularVelocity(Vector3f.ZERO);

        bus().emit("engine.physics.body.teleport", evtJs(
                "bodyId", h.id,
                "surfaceId", h.surfaceId,
                "entity", entityOfSurface(h.surfaceId),
                "pos", jsVec3(p)
        ));
    }

    @HostAccess.Export
    @Override
    public PhysicsRayHit raycast(Object cfg) {
        flushPendingAdd();
        PhysicsSpace space = space();
        if (cfg == null) throw new IllegalArgumentException("physics.raycast(cfg) cfg required");

        Vector3f from = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "from"), 0, 0, 0);
        Vector3f to = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "to"), 0, 0, 0);

        List<PhysicsRayTestResult> hits = space.rayTest(from, to);
        if (hits == null || hits.isEmpty()) return null;

        PhysicsRayTestResult best = null;
        float bestFrac = Float.POSITIVE_INFINITY;
        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (f < bestFrac) {
                bestFrac = f;
                best = r;
            }
        }
        if (best == null) return null;

        PhysicsBodyHandle h = registry.findHandleByCollisionObject(best.getCollisionObject());
        int bodyId = (h != null) ? h.id : 0;
        int surfaceId = (h != null) ? h.surfaceId : 0;

        Vector3f dir = to.subtract(from);
        Vector3f hitPoint = from.add(dir.mult(bestFrac));
        Vector3f n = best.getHitNormalLocal();

        return new PhysicsRayHit(
                bodyId,
                surfaceId,
                bestFrac,
                new PhysicsRayHit.Vec3(hitPoint.x, hitPoint.y, hitPoint.z),
                n == null ? new PhysicsRayHit.Vec3(0, 1, 0) : new PhysicsRayHit.Vec3(n.x, n.y, n.z)
        );
    }

    @HostAccess.Export
    public Object raycastEx(Object cfg) {
        flushPendingAdd();
        PhysicsSpace space = space();
        if (cfg == null) throw new IllegalArgumentException("physics.raycastEx(cfg) cfg required");

        Vector3f from = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "from"), 0, 0, 0);
        Vector3f to = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "to"), 0, 0, 0);

        int ignoreBodyId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreBodyId"), 0);
        int ignoreSurfaceId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreSurfaceId"), 0);

        boolean staticOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "staticOnly"), false);
        boolean dynamicOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "dynamicOnly"), false);

        int mask = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mask"), 0);

        List<PhysicsRayTestResult> hits = space.rayTest(from, to);
        if (hits == null || hits.isEmpty()) {
            return hitObj(false, 0, 0, 0f, 0f, from, null);
        }

        PhysicsRayTestResult best = null;
        float bestFrac = Float.POSITIVE_INFINITY;

        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (!isFinite(f)) continue;

            PhysicsBodyHandle h = registry.findHandleByCollisionObject(r.getCollisionObject());
            if (h == null) continue;

            if (ignoreBodyId > 0 && h.id == ignoreBodyId) continue;
            if (ignoreSurfaceId > 0 && h.surfaceId == ignoreSurfaceId) continue;

            RigidBodyControl rb = h.__raw();
            if (!passesStaticDynamicFilter(rb, staticOnly, dynamicOnly)) continue;
            if (!passesMaskFilter(rb, mask)) continue;

            if (f < bestFrac) {
                bestFrac = f;
                best = r;
            }
        }

        if (best == null) {
            return hitObj(false, 0, 0, 0f, 0f, from, null);
        }

        PhysicsBodyHandle bh = registry.findHandleByCollisionObject(best.getCollisionObject());
        int bodyId = (bh != null) ? bh.id : 0;
        int surfaceId = (bh != null) ? bh.surfaceId : 0;

        Vector3f dir = to.subtract(from);
        float rayLen = dir.length();
        Vector3f hitPoint = from.add(dir.mult(bestFrac));
        float distance = rayLen * bestFrac;

        return hitObj(true, bodyId, surfaceId, bestFrac, distance, hitPoint, best.getHitNormalLocal());
    }

    @HostAccess.Export
    public Object raycastAll(Object cfg) {
        flushPendingAdd();
        PhysicsSpace space = space();
        if (cfg == null) throw new IllegalArgumentException("physics.raycastAll(cfg) cfg required");

        Vector3f from = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "from"), 0, 0, 0);
        Vector3f to = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "to"), 0, 0, 0);

        int ignoreBodyId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreBodyId"), 0);
        int ignoreSurfaceId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreSurfaceId"), 0);

        boolean staticOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "staticOnly"), false);
        boolean dynamicOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "dynamicOnly"), false);

        int mask = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mask"), 0);

        int maxHits = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "maxHits"), 16);
        if (maxHits <= 0) maxHits = 16;
        if (maxHits > 256) maxHits = 256;

        List<PhysicsRayTestResult> hits = space.rayTest(from, to);
        if (hits == null || hits.isEmpty()) return new Object[0];

        ArrayList<PhysicsRayTestResult> filtered = new ArrayList<>(hits.size());
        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (!isFinite(f)) continue;

            PhysicsBodyHandle h = registry.findHandleByCollisionObject(r.getCollisionObject());
            if (h == null) continue;

            if (ignoreBodyId > 0 && h.id == ignoreBodyId) continue;
            if (ignoreSurfaceId > 0 && h.surfaceId == ignoreSurfaceId) continue;

            RigidBodyControl rb = h.__raw();
            if (!passesStaticDynamicFilter(rb, staticOnly, dynamicOnly)) continue;
            if (!passesMaskFilter(rb, mask)) continue;

            filtered.add(r);
        }

        if (filtered.isEmpty()) return new Object[0];

        filtered.sort((a, b) -> Float.compare(a.getHitFraction(), b.getHitFraction()));

        Vector3f dir = to.subtract(from);
        float rayLen = dir.length();
        if (rayLen <= 1e-6f) rayLen = 1e-6f;

        int outN = Math.min(maxHits, filtered.size());
        Object[] out = new Object[outN];

        for (int i = 0; i < outN; i++) {
            PhysicsRayTestResult r = filtered.get(i);
            float frac = r.getHitFraction();

            PhysicsBodyHandle h = registry.findHandleByCollisionObject(r.getCollisionObject());
            int bodyId = (h != null) ? h.id : 0;
            int surfaceId = (h != null) ? h.surfaceId : 0;

            Vector3f hitPoint = from.add(dir.mult(frac));
            float distance = rayLen * frac;

            out[i] = hitObj(true, bodyId, surfaceId, frac, distance, hitPoint, r.getHitNormalLocal());
        }

        return out;
    }

    private boolean passesStaticDynamicFilter(RigidBodyControl rb, boolean staticOnly, boolean dynamicOnly) {
        if (rb == null) return false;
        float mass = rb.getMass();
        boolean dynamic = mass > 0f && !rb.isKinematic();
        boolean stat = !dynamic;
        if (staticOnly && !stat) return false;
        if (dynamicOnly && !dynamic) return false;
        return true;
    }

    // ----------------------------------------------------------------------
    // Parsing: surface id
    // ----------------------------------------------------------------------

    private boolean passesMaskFilter(RigidBodyControl rb, int mask) {
        if (mask == 0) return true;
        try {
            return (rb.getCollideWithGroups() & mask) != 0;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private int resolveSurfaceId(Object cfg) {
        Object s = PhysicsValueParsers.member(cfg, "surface");
        if (s == null) return 0;

        if (s instanceof Number n) return n.intValue();

        if (s instanceof Value v) {
            if (v.isNumber()) return v.asInt();

            if (v.hasMember("id")) {
                Value id = v.getMember("id");
                if (id != null) {
                    if (id.isNumber()) return id.asInt();
                    if (id.canExecute()) {
                        Value r = id.execute();
                        if (r != null && r.isNumber()) return r.asInt();
                    }
                }
            }

            if (v.hasMember("surfaceId")) {
                Value sid = v.getMember("surfaceId");
                if (sid != null) {
                    if (sid.isNumber()) return sid.asInt();
                    if (sid.canExecute()) {
                        Value r = sid.execute();
                        if (r != null && r.isNumber()) return r.asInt();
                    }
                }
            }
        }

        if (s instanceof SurfaceApi.SurfaceHandle h) return h.id;

        if (s instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        throw new IllegalArgumentException("physics.body: surface must be surfaceId or SurfaceHandle");
    }

    // ----------------------------------------------------------------------
    // Shape selection + caching
    // ----------------------------------------------------------------------

    private CollisionShape primitiveShapeFromGeometry(Geometry g) {
        Mesh mesh = g.getMesh();
        if (mesh == null) return null;

        if (mesh instanceof Box) {
            BoundingVolume bv = mesh.getBound();
            if (bv instanceof BoundingBox bb) {
                Vector3f he = bb.getExtent(null);
                he.x = clampPositive(he.x, 0.001f);
                he.y = clampPositive(he.y, 0.001f);
                he.z = clampPositive(he.z, 0.001f);
                return new BoxCollisionShape(he);
            }
            BoundingVolume w = g.getWorldBound();
            if (w instanceof BoundingBox wb) {
                Vector3f he = wb.getExtent(null);
                he.x = clampPositive(he.x, 0.001f);
                he.y = clampPositive(he.y, 0.001f);
                he.z = clampPositive(he.z, 0.001f);
                return new BoxCollisionShape(he);
            }
        }

        if (mesh instanceof Sphere) {
            BoundingVolume bv = mesh.getBound();
            if (bv instanceof BoundingBox bb) {
                Vector3f he = bb.getExtent(null);
                float r = Math.max(he.x, Math.max(he.y, he.z));
                r = clampPositive(r, 0.001f);
                return new SphereCollisionShape(r);
            }
        }

        if (mesh instanceof Cylinder) {
            BoundingVolume bv = mesh.getBound();
            if (bv instanceof BoundingBox bb) {
                Vector3f he = bb.getExtent(null);
                he.x = clampPositive(he.x, 0.001f);
                he.y = clampPositive(he.y, 0.001f);
                he.z = clampPositive(he.z, 0.001f);
                return new CylinderCollisionShape(he);
            }
        }

        BoundingVolume bv = mesh.getBound();
        if (bv instanceof BoundingBox bb) {
            Vector3f he = bb.getExtent(null);
            if (he != null) {
                he.x = clampPositive(he.x, 0.001f);
                he.y = clampPositive(he.y, 0.001f);
                he.z = clampPositive(he.z, 0.001f);
                return new BoxCollisionShape(he);
            }
        }

        return null;
    }

    private CollisionShape defaultShapeForSpatial(Spatial spatial, boolean dynamic) {
        if (spatial instanceof Geometry g) {
            CollisionShape prim = primitiveShapeFromGeometry(g);
            if (prim != null) return prim;

            Mesh mesh = g.getMesh();
            if (mesh != null) {
                ShapeKey key = new ShapeKey(mesh, dynamic);
                CollisionShape cached = shapeCache.get(key);
                if (cached != null) return cached;

                CollisionShape created = dynamic
                        ? CollisionShapeFactory.createDynamicMeshShape(g)
                        : CollisionShapeFactory.createMeshShape(g);

                shapeCache.putIfAbsent(key, created);
                return created;
            }
        }

        return dynamic
                ? CollisionShapeFactory.createDynamicMeshShape(spatial)
                : CollisionShapeFactory.createMeshShape(spatial);
    }

    // ----------------------------------------------------------------------
    // Maintenance / cleanup
    // ----------------------------------------------------------------------

    public void __clearAll() {
        pendingAdd.clear();
        shapeCache.clear();

        currPairs.clear();
        prevPairs.clear();
        currContacts.clear();

        bodyStates.clear();

        PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            registry.clearAll();
            return;
        }

        for (var e : registry.entries()) {
            PhysicsBodyHandle h = e.value();
            if (h == null) continue;

            int surfaceId = h.surfaceId;
            RigidBodyControl rb;
            try {
                rb = h.__raw();
            } catch (Throwable ignored) {
                continue;
            }

            try {
                s.remove(rb);
            } catch (Throwable ignored) {
            }

            try {
                Spatial sp = surfaces != null ? surfaces.get(surfaceId) : null;
                if (sp != null) sp.removeControl(rb);
            } catch (Throwable ignored) {
            }
        }

        registry.clearAll();

        log.info("[physics] cleared all bodies");
    }

    private static final class ShapeKey {
        private final Mesh mesh;
        private final boolean dynamic;
        private final int hash;

        ShapeKey(Mesh mesh, boolean dynamic) {
            this.mesh = mesh;
            this.dynamic = dynamic;
            int h = System.identityHashCode(mesh);
            h = 31 * h + (dynamic ? 1 : 0);
            this.hash = h;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ShapeKey k)) return false;
            return mesh == k.mesh && dynamic == k.dynamic;
        }
    }
}