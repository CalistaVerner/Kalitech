/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.bounding.BoundingBox
 *  com.jme3.bounding.BoundingVolume
 *  com.jme3.math.Vector3f
 *  com.jme3.scene.Geometry
 *  com.jme3.scene.Mesh
 *  com.jme3.scene.Spatial
 *  com.jme3.scene.shape.Box
 *  com.jme3.scene.shape.Cylinder
 *  com.jme3.scene.shape.Sphere
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsColliderFactory;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.body.PhysicsBodyConfig;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.collision.CollisionShapeProvider;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsMath;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class CachedCollisionShapeProvider
implements CollisionShapeProvider {
    private static final float MIN_EXTENT = 0.001f;
    private static final ThreadLocal<Vector3f> TL_EXTENT = ThreadLocal.withInitial(Vector3f::new);
    private final ConcurrentHashMap<Mesh, CollisionShape> staticMeshCache = new ConcurrentHashMap();
    private final ConcurrentHashMap<Mesh, CollisionShape> dynamicMeshCache = new ConcurrentHashMap();

    public CachedCollisionShapeProvider(Logger log) {
        Objects.requireNonNull(log, "log");
    }

    private static CollisionShape tryPrimitive(Geometry g) {
        Vector3f he;
        Mesh mesh = g.getMesh();
        if (mesh == null) {
            return null;
        }
        if (mesh instanceof Box && (he = CachedCollisionShapeProvider.extentFrom(g, mesh)) != null) {
            return new BoxCollisionShape(he);
        }
        if (mesh instanceof Sphere && (he = CachedCollisionShapeProvider.extentFrom(g, mesh)) != null) {
            float r = Math.max(he.x, Math.max(he.y, he.z));
            r = PhysicsMath.clampPositive(r, 0.001f);
            return new SphereCollisionShape(r);
        }
        if (mesh instanceof Cylinder && (he = CachedCollisionShapeProvider.extentFrom(g, mesh)) != null) {
            return new CylinderCollisionShape(he);
        }
        he = CachedCollisionShapeProvider.extentFrom(g, mesh);
        if (he != null) {
            return new BoxCollisionShape(he);
        }
        return null;
    }

    private static Vector3f extentFrom(Geometry g, Mesh mesh) {
        Vector3f tmp = TL_EXTENT.get();
        BoundingVolume bv = mesh.getBound();
        if (bv instanceof BoundingBox) {
            BoundingBox bb = (BoundingBox)bv;
            return CachedCollisionShapeProvider.sanitizeExtent(bb.getExtent(tmp));
        }
        BoundingVolume w = g.getWorldBound();
        if (w instanceof BoundingBox) {
            BoundingBox wb = (BoundingBox)w;
            return CachedCollisionShapeProvider.sanitizeExtent(wb.getExtent(tmp));
        }
        return null;
    }

    private static Vector3f sanitizeExtent(Vector3f he) {
        if (he == null) {
            return null;
        }
        he.x = PhysicsMath.clampPositive(he.x, 0.001f);
        he.y = PhysicsMath.clampPositive(he.y, 0.001f);
        he.z = PhysicsMath.clampPositive(he.z, 0.001f);
        return he;
    }

    private static String colliderTypeOf(Object colliderCfg) {
        if (colliderCfg == null) {
            return null;
        }
        if (colliderCfg instanceof LuaValueRef) {
            LuaValueRef v = (LuaValueRef)colliderCfg;
            try {
                if (v.hasMembers() && v.hasMember("type")) {
                    LuaValueRef t = v.getMember("type");
                    return t == null ? null : t.asString();
                }
            }
            catch (Throwable t) {
                // empty catch block
            }
        }
        if (colliderCfg instanceof Map) {
            Map m = (Map)colliderCfg;
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
            return this.defaultShapeForSpatial(spatial, cfg.isDynamic());
        }
        String type = CachedCollisionShapeProvider.colliderTypeOf(colliderCfg);
        if (cfg.isDynamic() && "mesh".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). Use collider.type='dynamicMesh' or primitive collider.");
        }
        return PhysicsColliderFactory.create(colliderCfg, spatial);
    }

    @Override
    public void clear() {
        this.staticMeshCache.clear();
        this.dynamicMeshCache.clear();
    }

    private CollisionShape defaultShapeForSpatial(Spatial spatial, boolean dynamic) {
        if (spatial instanceof Geometry) {
            Geometry g = (Geometry)spatial;
            CollisionShape prim = CachedCollisionShapeProvider.tryPrimitive(g);
            if (prim != null) {
                return prim;
            }
            Mesh mesh = g.getMesh();
            if (mesh != null) {
                ConcurrentHashMap<Mesh, CollisionShape> cache = dynamic ? this.dynamicMeshCache : this.staticMeshCache;
                CollisionShape cached = cache.get(mesh);
                if (cached != null) {
                    return cached;
                }
                CollisionShape created = dynamic ? CollisionShapeFactory.createDynamicMeshShape((Spatial)g) : CollisionShapeFactory.createMeshShape((Spatial)g);
                CollisionShape prev = cache.putIfAbsent(mesh, created);
                return prev != null ? prev : created;
            }
        }
        return dynamic ? CollisionShapeFactory.createDynamicMeshShape(spatial) : CollisionShapeFactory.createMeshShape(spatial);
    }
}

