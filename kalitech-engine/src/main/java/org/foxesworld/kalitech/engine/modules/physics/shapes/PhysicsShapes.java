// FILE: org/foxesworld/kalitech/engine/modules/physics/shapes/PhysicsShapes.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.shapes;

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

import static org.foxesworld.kalitech.engine.modules.physics.util.PhysicsMath.clampPositive;

/**
 * Collision shape factory and cache.
 */
public final class PhysicsShapes {

    private final ShapeCache cache;

    public PhysicsShapes(ShapeCache cache) {
        this.cache = cache;
    }

    public CollisionShape defaultShapeForSpatial(Spatial spatial, boolean dynamic) {
        if (spatial instanceof Geometry g) {
            CollisionShape prim = primitiveShapeFromGeometry(g);
            if (prim != null) return prim;

            Mesh mesh = g.getMesh();
            if (mesh != null) {
                ShapeKey key = new ShapeKey(mesh, dynamic);
                return cache.getOrCompute(key, () -> dynamic
                        ? CollisionShapeFactory.createDynamicMeshShape(g)
                        : CollisionShapeFactory.createMeshShape(g));
            }
        }

        return dynamic
                ? CollisionShapeFactory.createDynamicMeshShape(spatial)
                : CollisionShapeFactory.createMeshShape(spatial);
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
}