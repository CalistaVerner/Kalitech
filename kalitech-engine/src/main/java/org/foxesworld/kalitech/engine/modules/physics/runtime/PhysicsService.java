// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/PhysicsService.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime;

import com.jme3.app.SimpleApplication;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsColliderFactory;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsRegistry;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import static org.foxesworld.kalitech.engine.modules.physics.PhysicsJs.evtJs;
import static org.foxesworld.kalitech.engine.modules.physics.PhysicsJs.jsVec3Live;
import static org.foxesworld.kalitech.engine.modules.physics.util.PhysicsMath.clampPositive;

public final class PhysicsService {

    private static final int ADD_FLUSH_MAX_PER_TICK = 128;

    private final EngineApiImpl engine;
    private final Logger log;
    private final PhysicsRegistry registry;
    private final ConcurrentLinkedQueue<RigidBodyControl> pendingAdd = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean addFlushScheduled = new AtomicBoolean(false);
    private final ConcurrentHashMap<ShapeKey, CollisionShape> shapeCache = new ConcurrentHashMap<>();
    private volatile SimpleApplication app;
    private volatile SurfaceRegistry surfaces;
    private volatile IntConsumer onBodyRemoved;

    public PhysicsService(EngineApiImpl engine, Logger log) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.log = Objects.requireNonNull(log, "log");
        this.registry = new PhysicsRegistry(log);
    }

    private static int resolveIdFromValue(Value v, String... members) {
        if (v == null) return 0;
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
            }
        }
        return 0;
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

    /**
     * Sets a callback invoked after a body is removed from the registry.
     */
    public void setOnBodyRemoved(IntConsumer onBodyRemoved) {
        this.onBodyRemoved = onBodyRemoved;
    }

    public void bind(SimpleApplication app, SurfaceRegistry surfaces) {
        this.app = app;
        this.surfaces = surfaces;
    }

    public void unbind() {
        this.app = null;
        this.surfaces = null;
    }

    public EngineApiImpl engine() {
        return engine;
    }

    public SurfaceRegistry surfaces() {
        return surfaces;
    }

    public PhysicsRegistry registry() {
        return registry;
    }

    public PhysicsSpace requireSpace() {
        PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            throw new IllegalStateException(
                    "[physics] PhysicsSpace not bound. RuntimeAppState must attach BulletAppState and call engineApi.__setPhysicsSpace(space)."
            );
        }
        return s;
    }

    public void enqueueAddToSpace(RigidBodyControl rb) {
        if (rb == null) return;
        pendingAdd.add(rb);
        scheduleAddFlush();
    }

    private String entityOfSurface(int surfaceId) {
        SurfaceRegistry sr = this.surfaces;
        if (sr == null || surfaceId <= 0) return null;
        Spatial sp = sr.get(surfaceId);
        return PhysicsEntityResolver.entityOfSpatial(sp);
    }

    public void flushPendingAddNow() {
        PhysicsSpace sp = engine.__getPhysicsSpaceOrNull();
        if (sp == null) return;

        int n = 0;
        RigidBodyControl rb;
        while (n < ADD_FLUSH_MAX_PER_TICK && (rb = pendingAdd.poll()) != null) {
            try {
                sp.add(rb);
            } catch (Throwable t) {
                log.error("[physics] addToSpace failed", t);
            }

            try {
                Integer bodyId = registry.idOfControl(rb);
                PhysicsBodyHandle h = bodyId != null ? registry.get(bodyId) : null;
                if (h != null) {
                    Vector3f p = null;
                    SurfaceRegistry sr = this.surfaces;
                    if (sr != null) {
                        Spatial spx = sr.get(h.surfaceId);
                        if (spx != null) p = spx.getWorldTranslation();
                    }
                    engine.getBus().emit("engine.physics.body.added", evtJs(
                            "bodyId", h.id,
                            "surfaceId", h.surfaceId,
                            "entity", entityOfSurface(h.surfaceId),
                            "pos", p == null ? null : jsVec3Live(p)
                    ));
                }
            } catch (Throwable ignored) {
            }
            n++;
        }
    }

    private void scheduleAddFlush() {
        SimpleApplication a = this.app;
        if (a == null) return;
        if (!addFlushScheduled.compareAndSet(false, true)) return;

        a.enqueue(() -> {
            try {
                flushPendingAddNow();
            } finally {
                addFlushScheduled.set(false);
                if (!pendingAdd.isEmpty()) scheduleAddFlush();
            }
            return null;
        });
    }

    public PhysicsBodyHandle createBody(Object cfg) {
        requireSpace();

        if (cfg == null) throw new IllegalArgumentException("physics.body(cfg) cfg is required");

        int surfaceId = resolveSurfaceId(cfg);
        if (surfaceId <= 0) throw new IllegalArgumentException("physics.body: surface id is required");

        SurfaceRegistry sr = surfaces;
        if (sr == null) throw new IllegalStateException("physics.body: SurfaceRegistry not bound");

        Spatial spatial = sr.get(surfaceId);
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

        int id = registry.nextId();
        PhysicsBodyHandle handle = new PhysicsBodyHandle(id, surfaceId, rb);
        registry.put(handle);
        registry.indexCollisionObject(handle);

        try {
            engine.getBus().emit("engine.physics.body.create", evtJs(
                    "bodyId", id,
                    "surfaceId", surfaceId,
                    "entity", entityOfSurface(surfaceId),
                    "mass", mass,
                    "kinematic", kinematic,
                    "lockRotation", lockRot
            ));
        } catch (Throwable ignored) {
        }

        enqueueAddToSpace(rb);

        return handle;
    }

    public void removeBody(Object handleOrId) {
        int id = registry.resolveBodyId(handleOrId);
        if (id <= 0) return;
        removeBodyById(id);
    }

    public void removeBodyById(int id) {
        if (id <= 0) return;

        PhysicsBodyHandle h = registry.remove(id);
        if (h == null) return;

        registry.unindexCollisionObject(h);
        registry.removeSurfaceBinding(h.surfaceId, h.id);

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

            PhysicsSpace sp = engine.__getPhysicsSpaceOrNull();
            if (sp != null) {
                try {
                    sp.remove(rb);
                } catch (Throwable ignored) {
                }
            }

            SurfaceRegistry sr = surfaces;
            try {
                Spatial spx = sr != null ? sr.get(h.surfaceId) : null;
                if (spx != null) spx.removeControl(rb);
            } catch (Throwable ignored) {
            }
        }

        try {
            engine.getBus().emit("engine.physics.body.remove", evtJs(
                    "bodyId", h.id,
                    "surfaceId", h.surfaceId,
                    "entity", entityOfSurface(h.surfaceId)
            ));
        } catch (Throwable ignored) {
        }

        IntConsumer cb = this.onBodyRemoved;
        if (cb != null) {
            try {
                cb.accept(h.id);
            } catch (Throwable ignored) {
            }
        }
    }

    public void clearAll() {
        pendingAdd.clear();
        shapeCache.clear();

        PhysicsSpace sp = engine.__getPhysicsSpaceOrNull();
        SurfaceRegistry sr = this.surfaces;
        IntConsumer cb = this.onBodyRemoved;

        try {
            for (var e : registry.entries()) {
                PhysicsBodyHandle h = e.value();
                if (h == null) continue;

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
                    if (sp != null) {
                        try {
                            sp.remove(rb);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (sr != null) {
                        try {
                            Spatial spx = sr.get(h.surfaceId);
                            if (spx != null) spx.removeControl(rb);
                        } catch (Throwable ignored) {
                        }
                    }
                }

                if (cb != null) {
                    try {
                        cb.accept(h.id);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } finally {
            registry.clearAll();
        }
    }

    private int resolveSurfaceId(Object cfg) {
        Object s = PhysicsValueParsers.member(cfg, "surface");
        if (s == null) return 0;

        if (s instanceof Number n) return n.intValue();

        if (s instanceof Value v) {
            try {
                if (v.isNumber()) return v.asInt();
            } catch (Throwable ignored) {
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

    private CollisionShape defaultShapeForSpatial(Spatial spatial, boolean dynamic) {
        if (spatial instanceof Geometry g) {
            CollisionShape prim = PrimitiveShapeSelector.tryPrimitive(g);
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

    private static final class PrimitiveShapeSelector {
        private PrimitiveShapeSelector() {
        }

        static CollisionShape tryPrimitive(Geometry g) {
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