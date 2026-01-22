// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/CachedCollisionShapeProvider.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service.collision;

import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsColliderFactory;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.body.PhysicsBodyConfig;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.foxesworld.kalitech.engine.modules.physics.util.PhysicsMath.clampPositive;

public final class CachedCollisionShapeProvider implements CollisionShapeProvider {

    private static final float MIN_EXTENT = 0.001f;

    // Thread-local extent scratch (avoids per-call Vector3f in getExtent(null))
    private static final ThreadLocal<Vector3f> TL_EXTENT = ThreadLocal.withInitial(Vector3f::new);
    // No ShapeKey allocations: separate caches for static/dynamic
    private final ConcurrentHashMap<Mesh, CollisionShape> staticMeshCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Mesh, CollisionShape> dynamicMeshCache = new ConcurrentHashMap<>();

    public CachedCollisionShapeProvider(@SuppressWarnings("unused") Logger log) {
        Objects.requireNonNull(log, "log");
    }

    private static CollisionShape tryPrimitive(Geometry g) {
        Mesh mesh = g.getMesh();
        if (mesh == null) return null;

        if (mesh instanceof Box) {
            Vector3f he = extentFrom(g, mesh);
            if (he != null) return new BoxCollisionShape(he);
        }

        if (mesh instanceof Sphere) {
            Vector3f he = extentFrom(g, mesh);
            if (he != null) {
                float r = Math.max(he.x, Math.max(he.y, he.z));
                r = clampPositive(r, MIN_EXTENT);
                return new SphereCollisionShape(r);
            }
        }

        if (mesh instanceof Cylinder) {
            Vector3f he = extentFrom(g, mesh);
            if (he != null) return new CylinderCollisionShape(he);
        }

        Vector3f he = extentFrom(g, mesh);
        if (he != null) return new BoxCollisionShape(he);

        return null;
    }

    private static Vector3f extentFrom(Geometry g, Mesh mesh) {
        Vector3f tmp = TL_EXTENT.get();

        BoundingVolume bv = mesh.getBound();
        if (bv instanceof BoundingBox bb) {
            return sanitizeExtent(bb.getExtent(tmp));
        }

        BoundingVolume w = g.getWorldBound();
        if (w instanceof BoundingBox wb) {
            return sanitizeExtent(wb.getExtent(tmp));
        }

        return null;
    }

    private static Vector3f sanitizeExtent(Vector3f he) {
        if (he == null) return null;
        he.x = clampPositive(he.x, MIN_EXTENT);
        he.y = clampPositive(he.y, MIN_EXTENT);
        he.z = clampPositive(he.z, MIN_EXTENT);
        return he;
    }

    private static String colliderTypeOf(Object colliderCfg) {
        if (colliderCfg == null) return null;

        if (colliderCfg instanceof org.graalvm.polyglot.Value v) {
            try {
                if (v.hasMembers() && v.hasMember("type")) {
                    org.graalvm.polyglot.Value t = v.getMember("type");
                    return t == null ? null : t.asString();
                }
            } catch (Throwable ignored) {
                // no-op
            }
        }

        if (colliderCfg instanceof java.util.Map<?, ?> m) {
            Object t = m.get("type");
            return t == null ? null : String.valueOf(t);
        }

        return null;
    }

    @Override
    public CollisionShape resolveShape(PhysicsBodyConfig cfg, Spatial spatial) {
        Objects.requireNonNull(cfg, "cfg");
        Objects.requireNonNull(spatial, "spatial");

        Object colliderCfg = cfg.getColliderCfg();
        if (colliderCfg == null) {
            return defaultShapeForSpatial(spatial, cfg.isDynamic());
        }

        String type = colliderTypeOf(colliderCfg);
        if (cfg.isDynamic() && "mesh".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException(
                    "physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). Use collider.type='dynamicMesh' or primitive collider."
            );
        }

        return PhysicsColliderFactory.create(colliderCfg, spatial);
    }

    @Override
    public void clear() {
        staticMeshCache.clear();
        dynamicMeshCache.clear();
    }

    private CollisionShape defaultShapeForSpatial(Spatial spatial, boolean dynamic) {
        if (spatial instanceof Geometry g) {
            CollisionShape prim = tryPrimitive(g);
            if (prim != null) return prim;

            Mesh mesh = g.getMesh();
            if (mesh != null) {
                ConcurrentHashMap<Mesh, CollisionShape> cache = dynamic ? dynamicMeshCache : staticMeshCache;

                CollisionShape cached = cache.get(mesh);
                if (cached != null) return cached;

                CollisionShape created = dynamic
                        ? CollisionShapeFactory.createDynamicMeshShape(g)
                        : CollisionShapeFactory.createMeshShape(g);

                CollisionShape prev = cache.putIfAbsent(mesh, created);
                return (prev != null) ? prev : created;
            }
        }

        return dynamic
                ? CollisionShapeFactory.createDynamicMeshShape(spatial)
                : CollisionShapeFactory.createMeshShape(spatial);
    }
}