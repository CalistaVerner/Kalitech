// FILE: org/foxesworld/kalitech/engine/api/impl/physics/PhysicsApiImpl.java
package org.foxesworld.kalitech.engine.api.impl.physics;

import com.jme3.app.SimpleApplication;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.*;
import com.jme3.bullet.control.RigidBodyControl;
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
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class PhysicsApiImpl implements PhysicsApi {

    private static final Logger log = LogManager.getLogger(PhysicsApiImpl.class);

    private final EngineApiImpl engine;
    private final SimpleApplication app;
    private final SurfaceRegistry surfaces;

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, PhysicsBodyHandle> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> bodyIdBySurface = new ConcurrentHashMap<>();

    // ------------------------------------------------------------
    // ✅ Batched add to PhysicsSpace (NO JS/API changes)
    // ------------------------------------------------------------
    private final ConcurrentLinkedQueue<RigidBodyControl> pendingAdd = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean addFlushScheduled = new AtomicBoolean(false);

    // Time slicing: limit how many adds per flush to avoid big frame spikes
    // (tune: 64/128 depending on your machine)
    private static final int ADD_FLUSH_MAX_PER_TICK = 128;

    // ------------------------------------------------------------
    // ✅ CollisionShape caching (NO JS/API changes)
    // ------------------------------------------------------------
    // Cache by Mesh identity + dynamic flag.
    // This gives a MASSIVE boost when you spawn many identical cubes/spheres/etc.
    private final ConcurrentHashMap<ShapeKey, CollisionShape> shapeCache = new ConcurrentHashMap<>();

    private record ShapeKey(Mesh mesh, boolean dynamic) { }

    public PhysicsApiImpl(EngineApiImpl engine, SurfaceRegistry surfaces) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = Objects.requireNonNull(engine.getApp(), "app");
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
    }

    private PhysicsSpace space() {
        PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            throw new IllegalStateException("[physics] PhysicsSpace not bound. RuntimeAppState must attach BulletAppState and call engineApi.__setPhysicsSpace(space).");
        }
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
    // ✅ Batched add internals
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

        int n = 0;
        RigidBodyControl rb;
        while (n < ADD_FLUSH_MAX_PER_TICK && (rb = pendingAdd.poll()) != null) {
            try { sp.add(rb); } catch (Throwable ignored) {}
            n++;
        }

        // If still queued — reschedule (we already do it in finally)
    }

    // ------------------------------------------------------------
    // ✅ CollisionShape selection (fast path)
    // ------------------------------------------------------------

    private static float clampPositive(float v, float min) {
        return (Float.isFinite(v) && v > min) ? v : min;
    }

    private CollisionShape primitiveShapeFromGeometry(Geometry g) {
        Mesh mesh = g.getMesh();
        if (mesh == null) return null;

        // 1) Fast primitives for known mesh types
        if (mesh instanceof Box) {
            // Use mesh bound extents (local)
            BoundingVolume bv = mesh.getBound();
            if (bv instanceof BoundingBox bb) {
                Vector3f he = bb.getExtent(null);
                he.x = clampPositive(he.x, 0.001f);
                he.y = clampPositive(he.y, 0.001f);
                he.z = clampPositive(he.z, 0.001f);
                return new BoxCollisionShape(he);
            }
            // fallback: try world bound (may be null if not updated)
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
            // Cylinder mesh bound -> half extents
            BoundingVolume bv = mesh.getBound();
            if (bv instanceof BoundingBox bb) {
                Vector3f he = bb.getExtent(null);
                he.x = clampPositive(he.x, 0.001f);
                he.y = clampPositive(he.y, 0.001f);
                he.z = clampPositive(he.z, 0.001f);
                // JME bullet cylinder shape uses half extents
                return new CylinderCollisionShape(he);
            }
        }

        // 2) Generic primitive fallback by bound
        BoundingVolume bv = mesh.getBound();
        if (bv instanceof BoundingBox bb) {
            Vector3f he = bb.getExtent(null);
            if (he != null) {
                he.x = clampPositive(he.x, 0.001f);
                he.y = clampPositive(he.y, 0.001f);
                he.z = clampPositive(he.z, 0.001f);
                // If it's close to cube-ish, box collider is a good default
                return new BoxCollisionShape(he);
            }
        }

        return null;
    }

    private CollisionShape defaultShapeForSpatial(Spatial spatial, boolean dynamic) {
        // If geometry -> try primitive collider (HUGE perf win)
        if (spatial instanceof Geometry g) {
            CollisionShape prim = primitiveShapeFromGeometry(g);
            if (prim != null) return prim;

            // If no primitive path: cache mesh shapes by Mesh identity
            Mesh mesh = g.getMesh();
            if (mesh != null) {
                ShapeKey key = new ShapeKey(mesh, dynamic);
                CollisionShape cached = shapeCache.get(key);
                if (cached != null) return cached;

                CollisionShape created = dynamic
                        ? CollisionShapeFactory.createDynamicMeshShape(g)
                        : CollisionShapeFactory.createMeshShape(g);

                // cache it
                shapeCache.putIfAbsent(key, created);
                return created;
            }
        }

        // Node or unknown: mesh shapes (expensive) but still can be cached by a pseudo-key if needed.
        // For now keep it safe: node mesh shape depends on subtree transforms.
        return dynamic
                ? CollisionShapeFactory.createDynamicMeshShape(spatial)
                : CollisionShapeFactory.createMeshShape(spatial);
    }

    // ------------------------------------------------------------
    // ✅ Ray helpers (AAA camera/tooling)
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
        if (obj == null) return null;
        for (PhysicsBodyHandle h : byId.values()) {
            if (h != null && h.__raw() == obj) return h;
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
        // mask==0 -> ignore filter
        if (mask == 0) return true;
        try {
            return (rb.getCollideWithGroups() & mask) != 0;
        } catch (Throwable ignored) {
            // if getter is unavailable in some build, do not block hits
            return true;
        }
    }

    // ------------------------------------------------------------
    // API
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public PhysicsBodyHandle body(Object cfg) {
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

        // ✅ mass FIRST
        float mass = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mass"), 0.0);
        boolean dynamic = mass > 0f;

        Object colliderCfg = PhysicsValueParsers.member(cfg, "collider");

        CollisionShape shape;
        if (colliderCfg == null) {
            // ✅ FAST default collider path
            shape = defaultShapeForSpatial(spatial, dynamic);
        } else {
            // ✅ if user explicitly requested "mesh" with mass>0 -> hard error with clear message
            if (colliderCfg instanceof Value v && v.hasMembers() && v.hasMember("type")) {
                String t = String.valueOf(v.getMember("type"));
                if ("mesh".equalsIgnoreCase(t) && dynamic) {
                    throw new IllegalArgumentException(
                            "physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). " +
                                    "Use collider.type='dynamicMesh' or a primitive collider (box/sphere/capsule/cylinder)."
                    );
                }
            }
            if (colliderCfg instanceof java.util.Map<?, ?> m) {
                Object tObj = m.get("type");
                String t = (tObj != null) ? String.valueOf(tObj) : "";
                if ("mesh".equalsIgnoreCase(t) && dynamic) {
                    throw new IllegalArgumentException(
                            "physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). " +
                                    "Use collider.type='dynamicMesh' or a primitive collider (box/sphere/capsule/cylinder)."
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

        spatial.addControl(rb);

        // ✅ batched apply to space
        enqueueAddToSpace(rb);

        int id = ids.getAndIncrement();
        PhysicsBodyHandle handle = new PhysicsBodyHandle(id, surfaceId, rb);
        byId.put(id, handle);
        bodyIdBySurface.put(surfaceId, id);

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

        bodyIdBySurface.remove(h.surfaceId, id);

        Spatial spatial = surfaces.get(h.surfaceId);
        RigidBodyControl rb = h.__raw();

        // Remove from pending queue (best effort)
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
        if (obj != null) {
            for (PhysicsBodyHandle h : byId.values()) {
                if (h != null && h.__raw() == obj) {
                    bodyId = h.id;
                    surfaceId = h.surfaceId;
                    break;
                }
            }
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

    // ------------------------------------------------------------
    // ✅ Extended raycasts for AAA camera (filters + multi-hit)
    // ------------------------------------------------------------

    @HostAccess.Export
    public Object raycastEx(Object cfg) {
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
    // ---- controller helpers ----
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

        if (log.isDebugEnabled()) {
            log.debug("[physics] yaw bodyId={} yaw(rad)={} yaw(deg)={}", h.id, yaw, Math.toDegrees(yaw));
        }

        Quaternion q = new Quaternion();
        q.fromAngles(0f, (float) yaw, 0f);

        rb.setPhysicsRotation(q);
        rb.setAngularVelocity(Vector3f.ZERO);

        if (log.isTraceEnabled()) {
            log.trace("[physics] yaw applied bodyId={} quat={}", h.id, q);
        }
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

    // ------------------------------------------------------------
    // ✅ AAA extras: forces/torque/angular velocity/collision groups
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
    // ---- debug/gravity ----
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
    // ---- integration helpers (internal) ----
    // ------------------------------------------------------------

    public void __cleanupSurface(int surfaceId) {
        if (surfaceId <= 0) return;
        Integer id = bodyIdBySurface.get(surfaceId);
        if (id != null) remove(id);
    }

    // ------------------------------------------------------------
    // ---- parsing ----
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