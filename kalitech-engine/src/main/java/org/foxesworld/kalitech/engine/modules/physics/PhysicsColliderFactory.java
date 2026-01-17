// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.collision.shapes.*;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.graalvm.polyglot.Value;

import java.util.Map;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

/**
 * Collider factory for cfg.collider. PhysicsApiImpl controls defaults by mass.
 */
public final class PhysicsColliderFactory {

    private PhysicsColliderFactory() {
    }

    public static CollisionShape create(Object colliderCfg, Spatial spatial) {
        if (colliderCfg == null) return CollisionShapeFactory.createMeshShape(spatial);

        if (colliderCfg instanceof Value v) {
            String type = str(v, "type", "mesh");

            return switch (type) {
                case "box" -> {
                    Vector3f he = PhysicsValueParsers.vec3(member(v, "halfExtents"), 0, 0, 0);
                    if (he.lengthSquared() > 0) yield new BoxCollisionShape(he);
                    yield CollisionShapeFactory.createDynamicMeshShape(spatial);
                }
                case "sphere" -> new SphereCollisionShape((float) num(v, "radius", 1.0));
                case "capsule" ->
                        new CapsuleCollisionShape((float) num(v, "radius", 0.5), (float) num(v, "height", 1.0));
                case "cylinder" ->
                        new CylinderCollisionShape(PhysicsValueParsers.vec3(member(v, "halfExtents"), 0.5f, 0.5f, 0.5f));
                case "mesh" -> CollisionShapeFactory.createMeshShape(spatial);
                case "dynamicMesh" -> CollisionShapeFactory.createDynamicMeshShape(spatial);
                default -> throw new IllegalArgumentException("Unknown collider.type: " + type);
            };
        }

        if (colliderCfg instanceof Map<?, ?> m) {
            Object typeObj = m.get("type");
            String type = (typeObj != null) ? String.valueOf(typeObj) : "mesh";

            return switch (type) {
                case "box" -> {
                    Vector3f he = PhysicsValueParsers.vec3(m.get("halfExtents"), 0, 0, 0);
                    if (he.lengthSquared() > 0) yield new BoxCollisionShape(he);
                    yield CollisionShapeFactory.createDynamicMeshShape(spatial);
                }
                case "sphere" -> new SphereCollisionShape((float) PhysicsValueParsers.asNum(m.get("radius"), 1.0));
                case "capsule" ->
                        new CapsuleCollisionShape((float) PhysicsValueParsers.asNum(m.get("radius"), 0.5), (float) PhysicsValueParsers.asNum(m.get("height"), 1.0));
                case "cylinder" ->
                        new CylinderCollisionShape(PhysicsValueParsers.vec3(m.get("halfExtents"), 0.5f, 0.5f, 0.5f));
                case "mesh" -> CollisionShapeFactory.createMeshShape(spatial);
                case "dynamicMesh" -> CollisionShapeFactory.createDynamicMeshShape(spatial);
                default -> throw new IllegalArgumentException("Unknown collider.type: " + type);
            };
        }

        throw new IllegalArgumentException("Unsupported collider cfg: " + colliderCfg.getClass().getName());
    }
}