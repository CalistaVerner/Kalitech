// FILE: org/foxesworld/kalitech/engine/api/impl/physics/PhysicsApiImpl.java
package org.foxesworld.kalitech.engine.api.impl.physics;

import com.jme3.app.SimpleApplication;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.collision.shapes.*;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.impl.SurfaceRegistry;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.util.LongHashSet;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class PhysicsApiImpl implements PhysicsApi {

    private static final Logger log = LogManager.getLogger(PhysicsApiImpl.class);

    private final EngineApiImpl engine;
    private final SimpleApplication app;
    private final SurfaceRegistry surfaces;
    private final ScriptEventBus bus; // optional

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, PhysicsBodyHandle> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> bodyIdBySurface = new ConcurrentHashMap<>();
    private static final Field RBC_BODY_FIELD = findRbcBodyField();

    // ------------------------------------------------------------
    // Events: body lifecycle + deterministic "physics stepped"
    // ------------------------------------------------------------
    private final ConcurrentHashMap<RigidBodyControl, Integer> idByControl = new ConcurrentHashMap<>(1024);
    private final AtomicLong physicsStepCounter = new AtomicLong(0);

    // ------------------------------------------------------------
    // Collision pipeline (AAA contract): begin / stay / end + contact aggregation
    // ------------------------------------------------------------
    private final AtomicBoolean collisionListenerBound = new AtomicBoolean(false);
    private final AtomicBoolean tickListenerBound = new AtomicBoolean(false);

    /**
     * IMPORTANT: keys in this map MUST match objects returned by:
     *  - PhysicsCollisionEvent.getObjectA/B()
     *  - PhysicsRayTestResult.getCollisionObject()
     *
     * Therefore we index PhysicsRigidBody (preferred), fallback to RigidBodyControl.
     */
    private final ConcurrentHashMap<Object, Integer> bodyIdByCollisionObject = new ConcurrentHashMap<>(1024);

    /**
     * Collision pair presence tracking. Allocation-light, no boxing.
     * prevPairs = pairs from previous physics step
     * currPairs = pairs observed in current physics step (collected from Bullet collision callbacks)
     */
    private LongHashSet prevPairs = new LongHashSet(4096);
    private LongHashSet currPairs = new LongHashSet(4096);

    /**
     * Contact aggregation for the current step.
     * Key = pairKey(minBodyId,maxBodyId)
     */
    private final LongContactMap currContacts = new LongContactMap(4096);

    private static long pairKey(int a, int b) {
        if (a <= 0 || b <= 0) return 0L;
        int min = (a < b) ? a : b;
        int max = (a < b) ? b : a;
        long k = ((long) min << 32) | (max & 0xFFFFFFFFL);
        // IMPORTANT: our LongHashSet uses 0 as EMPTY sentinel; body ids start from 1, so k != 0.
        return k;
    }

    private static int keyA(long k) { return (int) (k >>> 32); }
    private static int keyB(long k) { return (int) (k & 0xFFFFFFFFL); }

    /**
     * Allocation-light contact accumulator.
     * We aggregate all contact points of the same pair within one physics step:
     *  - maxImpulse (max over contacts)
     *  - avgPoint (average of contact points)
     *  - avgNormal (average normal, normalized at emit time)
     *  - points (samples count)
     */
    private static final class ContactAgg {
        float maxImpulse;
        float sumPx, sumPy, sumPz;
        float sumNx, sumNy, sumNz;
        int points;

        void clear() {
            maxImpulse = 0f;
            sumPx = sumPy = sumPz = 0f;
            sumNx = sumNy = sumNz = 0f;
            points = 0;
        }

        void add(float impulse, Vector3f point, Vector3f normal) {
            if (Float.isFinite(impulse) && impulse > maxImpulse) maxImpulse = impulse;

            if (point != null) {
                sumPx += point.x;
                sumPy += point.y;
                sumPz += point.z;
            }
            if (normal != null) {
                sumNx += normal.x;
                sumNy += normal.y;
                sumNz += normal.z;
            }
            points++;
        }
    }

    /**
     * Open-addressing long->ContactAgg map (no boxing, stable memory).
     * Uses 0 as EMPTY sentinel in keys table.
     */
    private static final class LongContactMap {
        private static final long EMPTY = 0L;

        private long[] keys;
        private ContactAgg[] values;
        private int size;
        private int mask;
        private int resizeAt;

        LongContactMap(int initialCapacityPow2) {
            int cap = 1;
            while (cap < initialCapacityPow2) cap <<= 1;
            if (cap < 16) cap = 16;
            keys = new long[cap];
            values = new ContactAgg[cap];
            mask = cap - 1;
            resizeAt = (int) (cap * 0.65f);
            size = 0;
        }

        void clear() {
            Arrays.fill(keys, EMPTY);
            // values array kept; entries will be reused
            size = 0;
        }

        ContactAgg getOrCreate(long k) {
            if (k == EMPTY) return null;
            if (size >= resizeAt) rehash(keys.length << 1);

            int i = mix64to32(k) & mask;
            while (true) {
                long kk = keys[i];
                if (kk == EMPTY) {
                    keys[i] = k;
                    ContactAgg a = values[i];
                    if (a == null) values[i] = (a = new ContactAgg());
                    a.clear();
                    size++;
                    return a;
                }
                if (kk == k) {
                    ContactAgg a = values[i];
                    if (a == null) values[i] = (a = new ContactAgg());
                    return a;
                }
                i = (i + 1) & mask;
            }
        }

        ContactAgg get(long k) {
            if (k == EMPTY) return null;
            int i = mix64to32(k) & mask;
            while (true) {
                long kk = keys[i];
                if (kk == EMPTY) return null;
                if (kk == k) return values[i];
                i = (i + 1) & mask;
            }
        }

        private void rehash(int newCap) {
            long[] ok = keys;
            ContactAgg[] ov = values;

            long[] nk = new long[newCap];
            ContactAgg[] nv = new ContactAgg[newCap];
            int nm = newCap - 1;

            for (int i = 0; i < ok.length; i++) {
                long k = ok[i];
                if (k == EMPTY) continue;

                int idx = mix64to32(k) & nm;
                while (nk[idx] != EMPTY) idx = (idx + 1) & nm;
                nk[idx] = k;
                nv[idx] = ov[i];
            }

            keys = nk;
            values = nv;
            mask = nm;
            resizeAt = (int) (newCap * 0.65f);
            // size unchanged
        }

        private static int mix64to32(long z) {
            z ^= (z >>> 33);
            z *= 0xff51afd7ed558ccdL;
            z ^= (z >>> 33);
            z *= 0xc4ceb9fe1a85ec53L;
            z ^= (z >>> 33);
            return (int) z;
        }
    }

    // ------------------------------------------------------------
    // Collision object indexing (FIXED)
    // ------------------------------------------------------------

    private static Field findRbcBodyField() {
        try {
            Field f = RigidBodyControl.class.getDeclaredField("body");
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object collisionKeyFromHandle(PhysicsBodyHandle h) {
        if (h == null) return null;

        Object raw = h.__raw(); // typically RigidBodyControl
        if (raw instanceof RigidBodyControl rb) {
            PhysicsRigidBody prb = extractPhysicsRigidBody(rb);
            return (prb != null) ? prb : rb;
        }
        return raw;
    }

    private static PhysicsRigidBody extractPhysicsRigidBody(RigidBodyControl rb) {
        if (rb == null) return null;

        if (RBC_BODY_FIELD != null) {
            try {
                Object v = RBC_BODY_FIELD.get(rb);
                if (v instanceof PhysicsRigidBody prb) return prb;
            } catch (Throwable ignored) {}
        }

        try {
            var m = rb.getClass().getMethod("getRigidBody");
            Object v = m.invoke(rb);
            if (v instanceof PhysicsRigidBody prb) return prb;
        } catch (Throwable ignored) {}

        try {
            var m = rb.getClass().getMethod("getBody");
            Object v = m.invoke(rb);
            if (v instanceof PhysicsRigidBody prb) return prb;
        } catch (Throwable ignored) {}

        return null;
    }

    private void indexCollisionObject(PhysicsBodyHandle h) {
        Object key = collisionKeyFromHandle(h);
        if (key != null) bodyIdByCollisionObject.put(key, h.id);
    }

    private void unindexCollisionObject(PhysicsBodyHandle h) {
        Object key = collisionKeyFromHandle(h);
        if (key != null) bodyIdByCollisionObject.remove(key, h.id);
    }

    private int bodyIdFromCollisionObject(Object obj) {
        if (obj == null) return 0;

        Integer id = bodyIdByCollisionObject.get(obj);
        if (id != null) return id;

        if (obj instanceof RigidBodyControl rb) {
            PhysicsRigidBody prb = extractPhysicsRigidBody(rb);
            if (prb != null) {
                Integer id2 = bodyIdByCollisionObject.get(prb);
                if (id2 != null) return id2;
            }
        }

        return 0;
    }

    private static Map<String, Object> evt(Object... kv) {
        HashMap<String, Object> out = new HashMap<>();
        if (kv == null) return out;
        for (int i = 0; i + 1 < kv.length; i += 2) out.put(String.valueOf(kv[i]), kv[i + 1]);
        return out;
    }

    private Map<String, Object> contactPayload(ContactAgg agg) {
        if (agg == null || agg.points <= 0) {
            return evt(
                    "maxImpulse", 0f,
                    "points", 0,
                    "point", new PhysicsRayHit.Vec3(0, 0, 0),
                    "normal", new PhysicsRayHit.Vec3(0, 1, 0)
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
            nx *= invN; ny *= invN; nz *= invN;
        } else {
            nx = 0f; ny = 1f; nz = 0f;
        }

        return evt(
                "maxImpulse", agg.maxImpulse,
                "points", agg.points,
                "point", new PhysicsRayHit.Vec3(px, py, pz),
                "normal", new PhysicsRayHit.Vec3(nx, ny, nz)
        );
    }

    private void emitCollision(String topic, long step, float dt, long k, ContactAgg agg) {
        if (bus == null) return;
        int aId = keyA(k);
        int bId = keyB(k);

        PhysicsBodyHandle a = byId.get(aId);
        PhysicsBodyHandle b = byId.get(bId);
        if (a == null || b == null) return;

        bus.emit(topic, evt(
                "step", step,
                "dt", dt,
                "a", evt("bodyId", a.id, "surfaceId", a.surfaceId),
                "b", evt("bodyId", b.id, "surfaceId", b.surfaceId),
                "contact", contactPayload(agg)
        ));
    }

    private final LongHashSet.LongConsumer emitBeginConsumer = new LongHashSet.LongConsumer() {
        @Override public void accept(long k) {
            if (k == 0L) return;
            if (prevPairs.contains(k)) return;
            // begin includes contact state from current step (available)
            emitCollision("engine.physics.collision.begin", physicsStepCounter.get() + 1, lastDt, k, currContacts.get(k));
        }
    };

    private final LongHashSet.LongConsumer emitStayConsumer = new LongHashSet.LongConsumer() {
        @Override public void accept(long k) {
            if (k == 0L) return;
            emitCollision("engine.physics.collision.stay", physicsStepCounter.get() + 1, lastDt, k, currContacts.get(k));
        }
    };

    private final LongHashSet.LongConsumer emitEndConsumer = new LongHashSet.LongConsumer() {
        @Override public void accept(long k) {
            if (k == 0L) return;
            if (currPairs.contains(k)) return;
            // end has no meaningful contact for this step
            emitCollision("engine.physics.collision.end", physicsStepCounter.get() + 1, lastDt, k, null);
        }
    };

    /**
     * Stored timestep from PhysicsTickListener for payload.
     * Accessed only on render/physics thread.
     */
    private volatile float lastDt = 0f;

    private void ensureCollisionListenerBound(PhysicsSpace sp) {
        if (sp == null) return;
        if (!collisionListenerBound.compareAndSet(false, true)) return;

        sp.addCollisionListener(new PhysicsCollisionListener() {
            @Override
            public void collision(PhysicsCollisionEvent e) {
                if (e == null) return;

                int a = bodyIdFromCollisionObject(e.getObjectA());
                int b = bodyIdFromCollisionObject(e.getObjectB());
                long key = pairKey(a, b);
                if (key == 0L) return;

                currPairs.add(key);

                // Aggregate contact
                ContactAgg agg = currContacts.getOrCreate(key);
                if (agg == null) return;

                float impulse = 0f;
                Vector3f point = null;
                Vector3f normal = null;

                try { impulse = e.getAppliedImpulse(); } catch (Throwable ignored) { /* older bullet */ }

                try {
                    Vector3f pa = e.getPositionWorldOnA();
                    Vector3f pb = e.getPositionWorldOnB();
                    if (pa != null && pb != null) point = pa.add(pb).multLocal(0.5f);
                    else point = (pa != null) ? pa : pb;
                } catch (Throwable ignored) { }

                try { normal = e.getNormalWorldOnB(); } catch (Throwable ignored) { }

                agg.add(impulse, point, normal);
            }
        });
    }

    /**
     * Commit collision sets for the step:
     *  - begin = curr - prev
     *  - stay  = curr (every step while contact exists)
     *  - end   = prev - curr
     * Also emits engine.physics.postStep({step, dt})
     *
     * Must be called AFTER physics step when collision callbacks already fired.
     */
    private void flushCollisionInternal(float timeStep) {
        lastDt = timeStep;

        // step increments once per flush
        long step = physicsStepCounter.incrementAndGet();

        // begin: curr - prev
        currPairs.forEach(emitBeginConsumer);
        // stay: all curr
        currPairs.forEach(emitStayConsumer);
        // end: prev - curr
        prevPairs.forEach(emitEndConsumer);

        // swap + clear
        LongHashSet tmp = prevPairs;
        prevPairs = currPairs;
        currPairs = tmp;
        currPairs.clear();
        currContacts.clear();

        if (bus != null) bus.emit("engine.physics.postStep", evt("step", step, "dt", timeStep));
    }

    private void ensureTickListenerBound(PhysicsSpace sp) {
        if (sp == null) return;
        if (!tickListenerBound.compareAndSet(false, true)) return;

        sp.addTickListener(new com.jme3.bullet.PhysicsTickListener() {
            @Override
            public void prePhysicsTick(PhysicsSpace space, float timeStep) {
                // тела (ground/player/etc) должны попасть в space ДО шага
                flushPendingAdd();
            }

            @Override
            public void physicsTick(PhysicsSpace space, float timeStep) {
                // после шага: коммитим begin/stay/end
                try {
                    flushCollisionInternal(timeStep);
                } catch (Throwable t) {
                    log.error("[physics] physicsTick collision flush failed", t);
                }
            }
        });

        log.info("[physics] tick listener bound (collision begin/stay/end + postStep)");
    }

    // ------------------------------------------------------------
    // Batched add to PhysicsSpace
    // ------------------------------------------------------------
    private final ConcurrentLinkedQueue<RigidBodyControl> pendingAdd = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean addFlushScheduled = new AtomicBoolean(false);
    private static final int ADD_FLUSH_MAX_PER_TICK = 128;

    // ------------------------------------------------------------
    // CollisionShape caching
    // ------------------------------------------------------------
    private final ConcurrentHashMap<ShapeKey, CollisionShape> shapeCache = new ConcurrentHashMap<>();
    private record ShapeKey(Mesh mesh, boolean dynamic) { }

    public PhysicsApiImpl(EngineApiImpl engine, SurfaceRegistry surfaces) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = Objects.requireNonNull(engine.getApp(), "app");
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
        this.bus = engine.getBus();
    }

    private void emit(String topic, java.util.Map<String, Object> payload) {
        if (bus == null) return;
        try { bus.emit(topic, payload); } catch (Throwable ignored) {}
    }

    private PhysicsSpace space() {
        PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            throw new IllegalStateException("[physics] PhysicsSpace not bound. RuntimeAppState must attach BulletAppState and call engineApi.__setPhysicsSpace(space).");
        }
        ensureCollisionListenerBound(s);
        ensureTickListenerBound(s);
        return s;
    }

    private PhysicsBodyHandle requireHandle(Object handleOrId, String where) {
        int id = resolveBodyId(handleOrId);
        if (id <= 0) throw new IllegalArgumentException(where + ": body id/handle required");
        PhysicsBodyHandle h = byId.get(id);
        if (h == null) throw new IllegalArgumentException(where + ": unknown bodyId=" + id);
        return h;
    }

    // ------------------------------------------------------------
    // Batched add internals
    // ------------------------------------------------------------

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

                Integer id = idByControl.get(rb);
                if (id != null) {
                    PhysicsBodyHandle h = byId.get(id);
                    if (h != null) {
                        if (bus != null) bus.emit("engine.physics.body.added", evt(
                                "bodyId", h.id,
                                "surfaceId", h.surfaceId
                        ));
                    }
                }
            } catch (Throwable t) {
                log.error("[physics] addToSpace failed", t);
            }
            n++;
        }
    }

    // ------------------------------------------------------------
    // CollisionShape selection (fast path)
    // ------------------------------------------------------------

    private static float clampPositive(float v, float min) {
        return (Float.isFinite(v) && v > min) ? v : min;
    }

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

    // ------------------------------------------------------------
    // Ray helpers
    // ------------------------------------------------------------

    private static boolean isFinite(float v) { return Float.isFinite(v); }

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

    private PhysicsBodyHandle findHandleByCollisionObject(Object obj) {
        int id = bodyIdFromCollisionObject(obj);
        if (id > 0) return byId.get(id);

        if (obj == null) return null;
        for (PhysicsBodyHandle h : byId.values()) {
            if (h == null) continue;

            Object key = collisionKeyFromHandle(h);
            if (key == obj) return h;

            try {
                if (h.__raw() == obj) return h;
            } catch (Throwable ignored) {}
        }
        return null;
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

    private boolean passesMaskFilter(RigidBodyControl rb, int mask) {
        if (mask == 0) return true;
        try {
            return (rb.getCollideWithGroups() & mask) != 0;
        } catch (Throwable ignored) {
            return true;
        }
    }

    // ------------------------------------------------------------
    // API
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public PhysicsBodyHandle body(Object cfg) {
        // ensure PhysicsSpace exists + listeners are bound early
        space();

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
            shape = defaultShapeForSpatial(spatial, dynamic);
        } else {
            if (colliderCfg instanceof Value v && v.hasMembers() && v.hasMember("type")) {
                String t = String.valueOf(v.getMember("type"));
                if ("mesh".equalsIgnoreCase(t) && dynamic) {
                    throw new IllegalArgumentException(
                            "physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). " +
                                    "Use collider.type='dynamicMesh' or primitive collider."
                    );
                }
            }
            if (colliderCfg instanceof java.util.Map<?, ?> m) {
                Object tObj = m.get("type");
                String t = (tObj != null) ? String.valueOf(tObj) : "";
                if ("mesh".equalsIgnoreCase(t) && dynamic) {
                    throw new IllegalArgumentException(
                            "physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). " +
                                    "Use collider.type='dynamicMesh' or primitive collider."
                    );
                }
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

        // ✅ CCD for dynamics (fixes tunneling on large dt spikes)
        if (dynamic && !kinematic) {
            float ccdMotionThreshold = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ccdMotionThreshold"), 0.001);
            float ccdRadius = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ccdSweptSphereRadius"), 0.20);
            rb.setCcdMotionThreshold(Math.max(0.0f, ccdMotionThreshold));
            rb.setCcdSweptSphereRadius(Math.max(0.0f, ccdRadius));
        }

        spatial.addControl(rb);

        enqueueAddToSpace(rb);

        int id = ids.getAndIncrement();
        PhysicsBodyHandle handle = new PhysicsBodyHandle(id, surfaceId, rb);
        byId.put(id, handle);
        bodyIdBySurface.put(surfaceId, id);
        idByControl.put(rb, id);

        indexCollisionObject(handle);

        if (bus != null) bus.emit("engine.physics.body.create", evt(
                "bodyId", id,
                "surfaceId", surfaceId,
                "mass", mass,
                "kinematic", kinematic,
                "lockRotation", lockRot
        ));

        log.debug("[physics] body created id={} surfaceId={} mass={} kinematic={} lockRotation={}",
                id, surfaceId, mass, kinematic, lockRot);

        return handle;
    }

    @HostAccess.Export
    @Override
    public void remove(Object handleOrId) {
        int id = resolveBodyId(handleOrId);
        if (id <= 0) return;

        PhysicsBodyHandle h = byId.remove(id);
        if (h == null) return;

        unindexCollisionObject(h);

        if (bus != null) bus.emit("engine.physics.body.remove", evt(
                "bodyId", id,
                "surfaceId", h.surfaceId
        ));

        bodyIdBySurface.remove(h.surfaceId, id);

        Spatial spatial = surfaces.get(h.surfaceId);
        RigidBodyControl rb = h.__raw();

        idByControl.remove(rb);

        try { pendingAdd.remove(rb); } catch (Throwable ignored) {}

        PhysicsSpace space = engine.__getPhysicsSpaceOrNull();
        if (space != null) {
            try { space.remove(rb); } catch (Throwable ignored) {}
        }

        try { if (spatial != null) spatial.removeControl(rb); } catch (Throwable ignored) {}

        log.debug("[physics] body removed id={} surfaceId={}", id, h.surfaceId);
    }

    @HostAccess.Export
    @Override
    public PhysicsRayHit raycast(Object cfg) {
        flushPendingAdd();
        PhysicsSpace space = space();
        if (cfg == null) throw new IllegalArgumentException("physics.raycast(cfg) cfg required");

        Vector3f from = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "from"), 0, 0, 0);
        Vector3f to   = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "to"),   0, 0, 0);

        List<PhysicsRayTestResult> hits = space.rayTest(from, to);
        if (hits == null || hits.isEmpty()) return null;

        PhysicsRayTestResult best = null;
        float bestFrac = Float.POSITIVE_INFINITY;
        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (f < bestFrac) { bestFrac = f; best = r; }
        }
        if (best == null) return null;

        int bodyId = 0;
        int surfaceId = 0;

        Object obj = best.getCollisionObject();
        PhysicsBodyHandle h = findHandleByCollisionObject(obj);
        if (h != null) {
            bodyId = h.id;
            surfaceId = h.surfaceId;
        }

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
        Vector3f to   = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "to"),   0, 0, 0);

        int ignoreBodyId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreBodyId"), 0);
        int ignoreSurfaceId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreSurfaceId"), 0);

        boolean staticOnly  = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "staticOnly"), false);
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

            Object obj = r.getCollisionObject();
            PhysicsBodyHandle h = findHandleByCollisionObject(obj);
            if (h == null) continue;

            if (ignoreBodyId > 0 && h.id == ignoreBodyId) continue;
            if (ignoreSurfaceId > 0 && h.surfaceId == ignoreSurfaceId) continue;

            RigidBodyControl rb = h.__raw();
            if (!passesStaticDynamicFilter(rb, staticOnly, dynamicOnly)) continue;
            if (!passesMaskFilter(rb, mask)) continue;

            if (f < bestFrac) { bestFrac = f; best = r; }
        }

        if (best == null) {
            return hitObj(false, 0, 0, 0f, 0f, from, null);
        }

        PhysicsBodyHandle bh = findHandleByCollisionObject(best.getCollisionObject());
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
        Vector3f to   = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "to"),   0, 0, 0);

        int ignoreBodyId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreBodyId"), 0);
        int ignoreSurfaceId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreSurfaceId"), 0);

        boolean staticOnly  = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "staticOnly"), false);
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

            PhysicsBodyHandle h = findHandleByCollisionObject(r.getCollisionObject());
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

            PhysicsBodyHandle h = findHandleByCollisionObject(r.getCollisionObject());
            int bodyId = (h != null) ? h.id : 0;
            int surfaceId = (h != null) ? h.surfaceId : 0;

            Vector3f hitPoint = from.add(dir.mult(frac));
            float distance = rayLen * frac;

            out[i] = hitObj(true, bodyId, surfaceId, frac, distance, hitPoint, r.getHitNormalLocal());
        }

        return out;
    }

    // ------------------------------------------------------------
    // controller helpers
    // ------------------------------------------------------------

    @HostAccess.Export
    public Object position(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.position()");
        Vector3f p = h.__raw().getPhysicsLocation();
        return new PhysicsRayHit.Vec3(p.x, p.y, p.z);
    }

    @HostAccess.Export
    public void warp(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.warp(pos)");
        Vector3f p = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        RigidBodyControl rb = h.__raw();
        rb.setPhysicsLocation(p);
        rb.setLinearVelocity(Vector3f.ZERO);
        rb.setAngularVelocity(Vector3f.ZERO);
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
        try { rb.activate(); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------
    // extras
    // ------------------------------------------------------------

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

    // ------------------------------------------------------------
    // debug/gravity
    // ------------------------------------------------------------

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

    // ------------------------------------------------------------
    // integration helpers (internal)
    // ------------------------------------------------------------

    public void __cleanupSurface(int surfaceId) {
        if (surfaceId <= 0) return;
        Integer id = bodyIdBySurface.get(surfaceId);
        if (id != null) remove(id);
    }

    // ------------------------------------------------------------
    // parsing
    // ------------------------------------------------------------

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

        if (s instanceof java.util.Map<?, ?> m) {
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

        if (handleOrId instanceof java.util.Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        return 0;
    }

    public void __clearAll() {
        pendingAdd.clear();
        shapeCache.clear();

        currPairs.clear();
        prevPairs.clear();
        bodyIdByCollisionObject.clear();

        PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            byId.clear();
            bodyIdBySurface.clear();
            return;
        }

        for (PhysicsBodyHandle h : byId.values()) {
            if (h == null) continue;

            int surfaceId = h.surfaceId;
            RigidBodyControl rb = h.__raw();

            try { s.remove(rb); } catch (Throwable ignored) {}

            try {
                Spatial sp = surfaces.get(surfaceId);
                if (sp != null) sp.removeControl(rb);
            } catch (Throwable ignored) {}
        }

        byId.clear();
        bodyIdBySurface.clear();

        log.info("[physics] cleared all bodies");
    }
}