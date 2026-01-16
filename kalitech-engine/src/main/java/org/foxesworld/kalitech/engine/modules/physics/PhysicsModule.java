// FILE: org/foxesworld/kalitech/engine/modules/physics/PhysicsModule.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.collision.CollisionObjectUtil;
import org.foxesworld.kalitech.engine.modules.physics.collision.PhysicsColliderFactory;
import org.foxesworld.kalitech.engine.modules.physics.collision.PhysicsCollisionTracker;
import org.foxesworld.kalitech.engine.modules.physics.query.PhysicsRaycasts;
import org.foxesworld.kalitech.engine.modules.physics.shapes.PhysicsShapes;
import org.foxesworld.kalitech.engine.modules.physics.shapes.ShapeCache;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.foxesworld.kalitech.engine.modules.physics.collision.CollisionPairKey.keyA;
import static org.foxesworld.kalitech.engine.modules.physics.collision.CollisionPairKey.keyB;
import static org.foxesworld.kalitech.engine.modules.physics.js.PhysicsJs.*;
import static org.foxesworld.kalitech.engine.modules.physics.util.PhysicsMath.isFinite;

/**
 * Internal physics module container.
 * <p>
 * This class is designed to keep {@code @HostAccess.Export} entrypoints in {@code PhysicsApiImpl}
 * untouched, while moving the heavy logic into cohesive modules.
 */
public final class PhysicsModule {

    private static final Logger log = LogManager.getLogger(PhysicsModule.class);

    private static final int ADD_FLUSH_MAX_PER_TICK = 128;

    private static final float MOVE_POS_EPS = 0.0025f;
    private static final float MOVE_ROT_EPS = 0.0010f;
    private static final float MOVE_VEL_EPS = 0.01f;
    private static final int MOVE_EVENT_MAX_PER_STEP = 512;

    private static final float IMPACT_MIN_IMPULSE = 0.25f;
    private static final float IMPACT_MIN_REL_SPEED = 0.20f;

    private final EngineApiImpl engine;
    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, PhysicsBodyHandle> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> bodyIdBySurface = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RigidBodyControl, Integer> idByControl = new ConcurrentHashMap<>(1024);
    private final ConcurrentHashMap<Object, Integer> bodyIdByCollisionObject = new ConcurrentHashMap<>(1024);
    private final AtomicLong physicsStepCounter = new AtomicLong(0);
    private final AtomicBoolean collisionListenerBound = new AtomicBoolean(false);
    private final AtomicBoolean tickListenerBound = new AtomicBoolean(false);
    private final BodyStateStore bodyState = new BodyStateStore(2048);
    private final ConcurrentLinkedQueue<RigidBodyControl> pendingAdd = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean addFlushScheduled = new AtomicBoolean(false);
    private final ShapeCache shapeCache = new ShapeCache(4096);
    private final PhysicsShapes shapes = new PhysicsShapes(shapeCache);
    // AAA collision tracking (correct begin/stay/end + aggregated contacts)
    private final PhysicsCollisionTracker collisions = new PhysicsCollisionTracker(4096, 4096);
    private SimpleApplication app;
    private SurfaceRegistry surfaces;
    private volatile float lastDt = 0f;
    private volatile boolean dbg = false;

    // ------------------------------------------------------------
    // Debug / diagnostics
    // ------------------------------------------------------------
    private volatile int dbgEverySteps = 120; // default ~2s at 60Hz
    private volatile int dbgEveryAddFlush = 60;
    private long dbgLastStepLogged = 0;
    private int dbgBodiesTotal = 0;
    private int dbgBodiesActive = 0;
    private int dbgMovesEmitted = 0;
    private int dbgCollBegin = 0;
    private int dbgCollStay = 0;
    private int dbgCollEnd = 0;
    private int dbgImpacts = 0;
    private int dbgRaycasts = 0;
    private int dbgPendingAddFlushed = 0;
    private int dbgPendingAddFailed = 0;
    private final PhysicsRaycasts raycasts = new PhysicsRaycasts(new PhysicsRaycasts.Access() {
        @Override
        public void flushPendingAdd() {
            PhysicsModule.this.flushPendingAdd();
        }

        @Override
        public PhysicsSpace requireSpace() {
            return PhysicsModule.this.requireSpace();
        }

        @Override
        public PhysicsBodyHandle findHandleByCollisionObject(Object collisionObject) {
            return PhysicsModule.this.findHandleByCollisionObject(collisionObject);
        }
    });

    public PhysicsModule(EngineApiImpl engine, SimpleApplication app, SurfaceRegistry surfaces) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = Objects.requireNonNull(app, "app");
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
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

    public void attach(SimpleApplication app, SurfaceRegistry surfaces) {
        this.app = app;
        this.surfaces = surfaces;
    }

    public void detach() {
        try {
            clearAll();
        } catch (Throwable ignored) {
        }
        this.app = null;
        this.surfaces = null;
    }

    public EngineApiImpl engine() {
        return engine;
    }

    public ScriptEventBus bus() {
        return engine.getBus();
    }

    public void setDebug(boolean enabled) {
        this.dbg = enabled;
        if (enabled && log.isInfoEnabled()) {
            log.info("[physics][dbg] enabled dbgEverySteps={} dbgEveryAddFlush={}", dbgEverySteps, dbgEveryAddFlush);
        }
    }

    public void setDebug(boolean enabled, int everySteps) {
        this.dbg = enabled;
        this.dbgEverySteps = Math.max(1, everySteps);
        if (enabled && log.isInfoEnabled()) {
            log.info("[physics][dbg] enabled dbgEverySteps={} dbgEveryAddFlush={}", dbgEverySteps, dbgEveryAddFlush);
        }
    }

    public int bodyOfSurface(int surfaceId) {
        if (surfaceId <= 0) return 0;
        Integer id = bodyIdBySurface.get(surfaceId);
        return (id == null) ? 0 : id;
    }

    public PhysicsBodyHandle handle(int bodyId) {
        if (bodyId <= 0) return null;
        return byId.get(bodyId);
    }

    public boolean exists(int bodyId) {
        return bodyId > 0 && byId.containsKey(bodyId);
    }

    public void cleanupSurface(int surfaceId) {
        if (surfaceId <= 0) return;
        Integer id = bodyIdBySurface.get(surfaceId);
        if (id != null) remove(id);
    }

    // ---------------------------------------------------------------------
    // Public actions (called from PhysicsApiImpl export points)
    // ---------------------------------------------------------------------

    public PhysicsSpace requireSpace() {
        PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            throw new IllegalStateException("[physics] PhysicsSpace not bound. RuntimeAppState must attach BulletAppState and call engineApi.__setPhysicsSpace(space).");
        }
        ensureCollisionListenerBound(s);
        ensureTickListenerBound(s);
        return s;
    }

    public Object raycastAll(Object cfg) {
        dbgRaycasts++;
        return raycasts.raycastAll(cfg);
    }

    public PhysicsRayHit raycast(Object cfg) {
        dbgRaycasts++;
        return raycasts.raycast(cfg);
    }

    public Object raycastEx(Object cfg) {
        dbgRaycasts++;
        return raycasts.raycastEx(cfg);
    }

    public Object position(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.position()");
        Vector3f p = h.__raw().getPhysicsLocation();
        return jsVec3(p);
    }

    public Object velocity(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.velocity()");
        Vector3f v = h.__raw().getLinearVelocity();
        return jsVec3(v);
    }

    public void velocity(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.velocity(v)");
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().setLinearVelocity(v);
        try {
            h.__raw().activate();
        } catch (Throwable ignored) {
        }
    }

    public void yaw(Object handleOrId, double yaw) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.yaw(yaw)");
        RigidBodyControl rb = h.__raw();

        Quaternion q = new Quaternion();
        q.fromAngles(0f, (float) yaw, 0f);

        rb.setPhysicsRotation(q);
        rb.setAngularVelocity(Vector3f.ZERO);
        try {
            rb.activate();
        } catch (Throwable ignored) {
        }
    }

    public void applyImpulse(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.applyImpulse(impulse)");
        Vector3f imp = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyImpulse(imp, Vector3f.ZERO);
        try {
            h.__raw().activate();
        } catch (Throwable ignored) {
        }
    }

    public void lockRotation(Object handleOrId, boolean lock) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.lockRotation(lock)");
        RigidBodyControl rb = h.__raw();
        if (lock) {
            rb.setAngularFactor(0f);
            rb.setAngularVelocity(Vector3f.ZERO);
        } else {
            rb.setAngularFactor(1f);
        }
        try {
            rb.activate();
        } catch (Throwable ignored) {
        }
    }

    public void setKinematic(Object handleOrId, boolean kinematic) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.setKinematic(kinematic)");
        RigidBodyControl rb = h.__raw();
        rb.setKinematic(kinematic);
        try {
            rb.activate();
        } catch (Throwable ignored) {
        }
    }

    public void collisionGroups(Object handleOrId, int group, int mask) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.collisionGroups(group,mask)");
        RigidBodyControl rb = h.__raw();
        rb.setCollisionGroup(group);
        rb.setCollideWithGroups(mask);
        try {
            rb.activate();
        } catch (Throwable ignored) {
        }
    }

    public void applyCentralForce(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.applyCentralForce(force)");
        Vector3f f = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyCentralForce(f);
        try {
            h.__raw().activate();
        } catch (Throwable ignored) {
        }
    }

    public void applyTorque(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.applyTorque(torque)");
        Vector3f t = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyTorque(t);
        try {
            h.__raw().activate();
        } catch (Throwable ignored) {
        }
    }

    public Object angularVelocity(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.angularVelocity()");
        Vector3f v = h.__raw().getAngularVelocity();
        return new PhysicsRayHit.Vec3(v.x, v.y, v.z);
    }

    public void angularVelocity(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.angularVelocity(v)");
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().setAngularVelocity(v);
        try {
            h.__raw().activate();
        } catch (Throwable ignored) {
        }
    }

    public void clearForces(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.clearForces()");
        RigidBodyControl rb = h.__raw();
        rb.clearForces();
        rb.setAngularVelocity(Vector3f.ZERO);
        rb.setLinearVelocity(Vector3f.ZERO);
        try {
            rb.activate();
        } catch (Throwable ignored) {
        }
    }

    public void gravity(Object vec3) {
        PhysicsSpace space = requireSpace();
        Vector3f g = PhysicsValueParsers.vec3(vec3, 0, -9.81f, 0);
        space.setGravity(g);
    }

    public void warp(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.warp(pos)");
        Vector3f p = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        RigidBodyControl rb = h.__raw();
        rb.setPhysicsLocation(p);
        rb.setLinearVelocity(Vector3f.ZERO);
        rb.setAngularVelocity(Vector3f.ZERO);
        try {
            rb.activate();
        } catch (Throwable ignored) {
        }

        bus().emit("engine.physics.body.teleport", evtJs(
                "bodyId", h.id,
                "surfaceId", h.surfaceId,
                "entity", entityOfSurface(h.surfaceId),
                "pos", jsVec3(p)
        ));
    }

    public PhysicsBodyHandle body(Object cfg) {
        requireSpace();

        if (cfg == null) throw new IllegalArgumentException("physics.body(cfg) cfg is required");

        int surfaceId = resolveSurfaceId(cfg);
        if (surfaceId <= 0) throw new IllegalArgumentException("physics.body: surface id is required");

        Spatial spatial = surfaces.get(surfaceId);
        if (spatial == null) throw new IllegalStateException("physics.body: unknown surfaceId=" + surfaceId);

        Integer existing = bodyIdBySurface.get(surfaceId);
        if (existing != null) {
            PhysicsBodyHandle h = byId.get(existing);
            if (h != null) return h;
        }

        float mass = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mass"), 0.0);
        boolean dynamic = mass > 0f;

        Object colliderCfg = PhysicsValueParsers.member(cfg, "collider");

        CollisionShape shape;
        if (colliderCfg == null) {
            shape = shapes.defaultShapeForSpatial(spatial, dynamic);
        } else {
            // Factory also validates mesh-for-dynamic; keep local validation for explicit message parity.
            validateColliderTypeForMass(colliderCfg, dynamic);
            shape = PhysicsColliderFactory.create(colliderCfg, spatial, dynamic);
        }

        RigidBodyControl rb = new RigidBodyControl(shape, mass);
        rb.setKinematic(PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "kinematic"), false));
        rb.setFriction((float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "friction"), 0.8));
        rb.setRestitution((float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "restitution"), 0.0));

        boolean lockRotation = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "lockRotation"), false);
        if (lockRotation) rb.setAngularFactor(0f);

        int group = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "group"), 1);
        int mask = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mask"), -1);
        rb.setCollisionGroup(group);
        rb.setCollideWithGroups(mask);

        spatial.addControl(rb);

        int id = ids.getAndIncrement();
        PhysicsBodyHandle h = new PhysicsBodyHandle(id, surfaceId, rb);

        byId.put(id, h);
        bodyIdBySurface.put(surfaceId, id);
        idByControl.put(rb, id);

        indexCollisionObject(h);
        enqueueAddToSpace(rb);

        if (dbg && log.isDebugEnabled()) {
            log.debug("[physics][body] created id={} surfaceId={} mass={} dyn={} group={} mask={} name={}",
                    id, surfaceId, mass, dynamic, group, mask, spatial.getName());
        }

        return h;
    }

    private void validateColliderTypeForMass(Object colliderCfg, boolean dynamic) {
        if (!dynamic) return;

        if (colliderCfg instanceof Value v && v.hasMembers() && v.hasMember("type")) {
            String t = String.valueOf(v.getMember("type"));
            if ("mesh".equalsIgnoreCase(t)) {
                throw new IllegalArgumentException(
                        "physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). " +
                                "Use collider.type='dynamicMesh' or primitive collider."
                );
            }
            return;
        }

        if (colliderCfg instanceof Map<?, ?> m) {
            Object tObj = m.get("type");
            String t = (tObj != null) ? String.valueOf(tObj) : "";
            if ("mesh".equalsIgnoreCase(t)) {
                throw new IllegalArgumentException(
                        "physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). " +
                                "Use collider.type='dynamicMesh' or primitive collider."
                );
            }
        }
    }

    public void remove(Object handleOrId) {
        int id = resolveBodyId(handleOrId);
        if (id <= 0) return;

        PhysicsBodyHandle h = byId.remove(id);
        if (h == null) return;

        bodyState.remove(id);
        bodyIdBySurface.remove(h.surfaceId, id);

        RigidBodyControl rb = null;
        try {
            rb = h.__raw();
        } catch (Throwable ignored) {
        }

        if (rb != null) {
            idByControl.remove(rb, id);
        }

        unindexCollisionObject(h);

        try {
            PhysicsSpace sp = engine.__getPhysicsSpaceOrNull();
            if (sp != null && rb != null) sp.remove(rb);
        } catch (Throwable ignored) {
        }

        try {
            Spatial sp = surfaces.get(h.surfaceId);
            if (sp != null) sp.removeControl(RigidBodyControl.class);
        } catch (Throwable ignored) {
        }

        if (dbg && log.isDebugEnabled()) {
            log.debug("[physics][body] removed id={} surfaceId={}", h.id, h.surfaceId);
        }

        bus().emit("engine.physics.body.removed", evtJs(
                "bodyId", h.id,
                "surfaceId", h.surfaceId,
                "entity", entityOfSurface(h.surfaceId)
        ));
    }

    public void clearAll() {
        for (Integer id : byId.keySet()) {
            try {
                remove(id);
            } catch (Throwable ignored) {
            }
        }
        byId.clear();
        bodyIdBySurface.clear();
        idByControl.clear();
        bodyIdByCollisionObject.clear();
        bodyState.clear();
        pendingAdd.clear();
        shapeCache.clear();
        collisions.clearAll();
        collisionListenerBound.set(false);
        tickListenerBound.set(false);

        dbgBodiesTotal = dbgBodiesActive = dbgMovesEmitted = 0;
        dbgCollBegin = dbgCollStay = dbgCollEnd = dbgImpacts = 0;
        dbgRaycasts = dbgPendingAddFlushed = dbgPendingAddFailed = 0;
        dbgLastStepLogged = 0;

        if (dbg && log.isInfoEnabled()) {
            log.info("[physics][dbg] clearAll done");
        }
    }

    private void ensureCollisionListenerBound(PhysicsSpace s) {
        if (!collisionListenerBound.compareAndSet(false, true)) return;

        s.addCollisionListener(new PhysicsCollisionListener() {
            @Override
            public void collision(PhysicsCollisionEvent event) {
                if (event == null) return;
                collisions.onCollision(event, PhysicsModule.this::bodyIdFromCollisionObject);
            }
        });

        if (dbg && log.isInfoEnabled()) {
            log.info("[physics][dbg] collision listener bound");
        }
    }

    private void ensureTickListenerBound(PhysicsSpace s) {
        if (!tickListenerBound.compareAndSet(false, true)) return;

        s.addTickListener(new PhysicsTickListener() {
            @Override
            public void prePhysicsTick(PhysicsSpace space, float timeStep) {
            }

            @Override
            public void physicsTick(PhysicsSpace space, float timeStep) {
                lastDt = timeStep;
                long step = physicsStepCounter.incrementAndGet();

                beginStepCounters();

                try {
                    flushCollisionInternal(step, timeStep);
                } catch (Throwable t) {
                    log.error("[physics] flushCollisionInternal failed", t);
                }

                try {
                    emitBodyStateEvents(step, timeStep);
                } catch (Throwable t) {
                    log.error("[physics] emitBodyStateEvents failed", t);
                }

                try {
                    maybeLogStep(step, timeStep, space);
                } catch (Throwable t) {
                    log.error("[physics] dbg step log failed", t);
                }
            }
        });

        if (dbg && log.isInfoEnabled()) {
            log.info("[physics][dbg] tick listener bound");
        }
    }

    private void flushCollisionInternal(long step, float dt) {
        collisions.flush(step, dt, new PhysicsCollisionTracker.Emitter() {
            @Override
            public void onBegin(long s, float d, long pairKey, ContactAgg agg) {
                dbgCollBegin++;
                emitCollision("engine.physics.collision.begin", s, d, pairKey, agg);
                emitImpact(s, d, pairKey, agg);
            }

            @Override
            public void onStay(long s, float d, long pairKey, ContactAgg agg) {
                dbgCollStay++;
                emitCollision("engine.physics.collision.stay", s, d, pairKey, agg);
            }

            @Override
            public void onEnd(long s, float d, long pairKey) {
                dbgCollEnd++;
                emitCollision("engine.physics.collision.end", s, d, pairKey, null);
            }
        });
    }

    private void emitBodyStateEvents(long step, float dt) {
        int emitted = 0;
        int total = 0;
        int active = 0;

        for (PhysicsBodyHandle h : byId.values()) {
            if (h == null) continue;
            total++;

            RigidBodyControl rb;
            try {
                rb = h.__raw();
            } catch (Throwable ignored) {
                continue;
            }
            if (rb == null) continue;

            if (isActiveSafe(rb)) active++;

            boolean changed = bodyState.updateAndCheckChanged(
                    h.id,
                    rb,
                    MOVE_POS_EPS,
                    MOVE_ROT_EPS,
                    MOVE_VEL_EPS
            );

            if (!changed) continue;

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

            emitted++;
            if (emitted >= MOVE_EVENT_MAX_PER_STEP) break;
        }

        dbgBodiesTotal = total;
        dbgBodiesActive = active;
        dbgMovesEmitted = emitted;
    }

    private void enqueueAddToSpace(RigidBodyControl rb) {
        if (rb == null) return;
        pendingAdd.add(rb);
        scheduleAddFlush();
    }

    private void scheduleAddFlush() {
        if (!addFlushScheduled.compareAndSet(false, true)) return;

        if (app == null) {
            addFlushScheduled.set(false);
            return;
        }

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

                dbgPendingAddFlushed++;

                Integer id = idByControl.get(rb);
                if (id != null) {
                    PhysicsBodyHandle h = byId.get(id);
                    if (h != null) {
                        Spatial entity = engine.getSurfaceRegistry().get(h.surfaceId);
                        Vector3f p = (entity != null) ? entity.getWorldTranslation() : null;

                        bus().emit("engine.physics.body.added", evtJs(
                                "bodyId", h.id,
                                "surfaceId", h.surfaceId,
                                "entity", entityOfSurface(h.surfaceId),
                                "scale", (entity != null ? jsVec3Live(entity.getLocalScale()) : null),
                                "pos", (p == null ? null : jsVec3Live(p))
                        ));

                        if (dbg && log.isTraceEnabled()) {
                            log.trace("[physics][add] id={} surfaceId={} name={}", h.id, h.surfaceId, (entity != null ? entity.getName() : null));
                        }
                    }
                }
            } catch (Throwable t) {
                dbgPendingAddFailed++;
                log.error("[physics] addToSpace failed", t);
            }
            n++;
        }

        if (dbg && log.isDebugEnabled() && dbgEveryAddFlush > 0 && (dbgPendingAddFlushed % dbgEveryAddFlush) == 0) {
            log.debug("[physics][dbg] pendingAdd flushed={} failed={} remaining={}",
                    dbgPendingAddFlushed, dbgPendingAddFailed, pendingAdd.size());
        }
    }

    private int bodyIdFromCollisionObject(Object obj) {
        if (obj == null) return 0;

        Integer id = bodyIdByCollisionObject.get(obj);
        if (id != null) return id;

        if (obj instanceof RigidBodyControl rb) {
            PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
            if (prb != null) {
                Integer id2 = bodyIdByCollisionObject.get(prb);
                if (id2 != null) return id2;
            }
        }

        return 0;
    }

    private void indexCollisionObject(PhysicsBodyHandle h) {
        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null) bodyIdByCollisionObject.put(key, h.id);
    }

    private void unindexCollisionObject(PhysicsBodyHandle h) {
        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null) bodyIdByCollisionObject.remove(key, h.id);
    }

    private PhysicsBodyHandle findHandleByCollisionObject(Object obj) {
        int id = bodyIdFromCollisionObject(obj);
        if (id > 0) return byId.get(id);

        if (!log.isTraceEnabled()) return null;

        if (obj == null) return null;
        for (PhysicsBodyHandle h : byId.values()) {
            if (h == null) continue;
            Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
            if (key == obj) return h;
            try {
                if (h.__raw() == obj) return h;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void emitCollision(String topic, long step, float dt, long k, ContactAgg agg) {
        int aId = keyA(k);
        int bId = keyB(k);

        PhysicsBodyHandle a = byId.get(aId);
        PhysicsBodyHandle b = byId.get(bId);
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
                "contact", contact
        ));
    }

    private void emitImpact(long step, float dt, long k, ContactAgg agg) {
        if (agg == null) return;

        float impulse = agg.maxImpulse;
        if (!isFinite(impulse) || impulse < IMPACT_MIN_IMPULSE) return;

        int aId = keyA(k);
        int bId = keyB(k);

        PhysicsBodyHandle a = byId.get(aId);
        PhysicsBodyHandle b = byId.get(bId);
        if (a == null || b == null) return;

        RigidBodyControl ra;
        RigidBodyControl rb;
        try {
            ra = a.__raw();
            rb = b.__raw();
        } catch (Throwable ignored) {
            return;
        }

        if (ra == null || rb == null) return;

        Vector3f va = null;
        Vector3f vb = null;
        float ma = 0f;
        float mb = 0f;

        try {
            va = ra.getLinearVelocity();
        } catch (Throwable ignored) {
        }
        try {
            vb = rb.getLinearVelocity();
        } catch (Throwable ignored) {
        }
        try {
            ma = ra.getMass();
        } catch (Throwable ignored) {
        }
        try {
            mb = rb.getMass();
        } catch (Throwable ignored) {
        }

        Vector3f rel = (va != null && vb != null) ? va.subtract(vb) : null;
        float relSpeed = (rel != null) ? rel.length() : 0f;

        if (!isFinite(relSpeed) || relSpeed < IMPACT_MIN_REL_SPEED) return;

        float reducedMass = (ma > 0f && mb > 0f) ? ((ma * mb) / (ma + mb)) : Math.max(ma, mb);
        if (!isFinite(reducedMass) || reducedMass < 0f) reducedMass = 0f;

        float energyApprox = 0.5f * reducedMass * relSpeed * relSpeed;
        if (!isFinite(energyApprox) || energyApprox < 0f) energyApprox = 0f;

        boolean hardA = isHard(ra);
        boolean hardB = isHard(rb);

        ProxyObject contact = contactPayload(agg);

        ProxyObject aObj = evtJs(
                "bodyId", a.id,
                "surfaceId", a.surfaceId,
                "entity", entityOfSurface(a.surfaceId),
                "pos", jsVec3SafePos(ra),
                "vel", jsVec3SafeVel(ra),
                "mass", massSafe(ra),
                "kinematic", isKinematicSafe(ra),
                "groups", groupsSafe(ra)
        );

        ProxyObject bObj = evtJs(
                "bodyId", b.id,
                "surfaceId", b.surfaceId,
                "entity", entityOfSurface(b.surfaceId),
                "pos", jsVec3SafePos(rb),
                "vel", jsVec3SafeVel(rb),
                "mass", massSafe(rb),
                "kinematic", isKinematicSafe(rb),
                "groups", groupsSafe(rb)
        );

        dbgImpacts++;

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

    private boolean isHard(RigidBodyControl rb) {
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

    private ProxyObject contactPayload(ContactAgg agg) {
        if (agg == null) return null;

        Vector3f mp = agg.maxPoint;
        Vector3f mn = agg.maxNormal;

        Vector3f ap = agg.avgPoint(null);
        Vector3f an = agg.avgNormal(null);

        return evtJs(
                "impulse", agg.maxImpulse,
                "point", evtJs("x", mp.x, "y", mp.y, "z", mp.z),
                "normal", evtJs("x", mn.x, "y", mn.y, "z", mn.z),
                "avgPoint", (ap == null ? null : evtJs("x", ap.x, "y", ap.y, "z", ap.z)),
                "avgNormal", (an == null ? null : evtJs("x", an.x, "y", an.y, "z", an.z)),
                "samples", agg.points
        );
    }

    private Object jsVec3SafePos(RigidBodyControl rb) {
        try {
            Vector3f v = rb.getPhysicsLocation();
            if (v != null) return jsVec3(v);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object jsQuatSafe(RigidBodyControl rb) {
        try {
            Quaternion q = rb.getPhysicsRotation();
            if (q != null) return jsQuat(q);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object jsVec3SafeVel(RigidBodyControl rb) {
        try {
            Vector3f v = rb.getLinearVelocity();
            if (v != null) return jsVec3(v);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object jsVec3SafeAngVel(RigidBodyControl rb) {
        try {
            Vector3f v = rb.getAngularVelocity();
            if (v != null) return jsVec3(v);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isActiveSafe(RigidBodyControl rb) {
        try {
            return rb.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private float massSafe(RigidBodyControl rb) {
        try {
            return rb.getMass();
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    private boolean isKinematicSafe(RigidBodyControl rb) {
        try {
            return rb.isKinematic();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int groupsSafe(RigidBodyControl rb) {
        try {
            return rb.getCollisionGroup();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private String entityOfSurface(int surfaceId) {
        if (surfaceId <= 0 || surfaces == null) return null;
        Spatial sp = surfaces.get(surfaceId);
        return entityOfSpatial(sp);
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

    private int resolveBodyId(Object handleOrId) {
        if (handleOrId == null) return 0;

        if (handleOrId instanceof Number n) return n.intValue();
        if (handleOrId instanceof PhysicsBodyHandle h) return h.id;

        if (handleOrId instanceof Value v) {
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

            if (v.hasMember("bodyId")) {
                Value bid = v.getMember("bodyId");
                if (bid != null) {
                    if (bid.isNumber()) return bid.asInt();
                    if (bid.canExecute()) {
                        Value r = bid.execute();
                        if (r != null && r.isNumber()) return r.asInt();
                    }
                }
            }
        }

        if (handleOrId instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        return 0;
    }

    private PhysicsBodyHandle requireHandle(Object handleOrId, String where) {
        int id = resolveBodyId(handleOrId);
        if (id <= 0) throw new IllegalArgumentException(where + ": body id/handle required");
        PhysicsBodyHandle h = byId.get(id);
        if (h == null) throw new IllegalArgumentException(where + ": unknown bodyId=" + id);
        return h;
    }

    private void beginStepCounters() {
        dbgCollBegin = 0;
        dbgCollStay = 0;
        dbgCollEnd = 0;
        dbgImpacts = 0;
        dbgMovesEmitted = 0;
        dbgRaycasts = 0;
    }

    private void maybeLogStep(long step, float dt, PhysicsSpace space) {
        if (!dbg || !log.isInfoEnabled()) return;

        if (step - dbgLastStepLogged < dbgEverySteps) return;
        dbgLastStepLogged = step;

        int bodies = dbgBodiesTotal;
        int active = dbgBodiesActive;

        int pending = pendingAdd.size();
        Vector3f g = null;
        try {
            g = (space != null) ? space.getGravity(new Vector3f()) : null;
        } catch (Throwable ignored) {
        }

        log.info("[physics][dbg] step={} dt={} bodies={} active={} pendingAdd={} " +
                        "coll(beg/stay/end)={}/{}/{} impacts={} moves={} raycasts={} gravity={}",
                step, dt, bodies, active, pending,
                dbgCollBegin, dbgCollStay, dbgCollEnd, dbgImpacts,
                dbgMovesEmitted, dbgRaycasts,
                (g != null ? ("(" + g.x + "," + g.y + "," + g.z + ")") : "null"));
    }
}