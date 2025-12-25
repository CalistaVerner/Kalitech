// FILE: org/foxesworld/kalitech/engine/api/impl/PhysicsColliderFactory.java
package org.foxesworld.kalitech.engine.api.impl.physics;

import com.jme3.bullet.collision.shapes.*;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.graalvm.polyglot.Value;

import java.util.Map;

final class PhysicsColliderFactory {
    private PhysicsColliderFactory() {}

    static CollisionShape create(Object colliderCfg, Spatial spatial) {
        if (colliderCfg == null) {
            // default: mesh (accurate, heavier)
            return CollisionShapeFactory.createMeshShape(spatial);
        }

        // JS Value
        if (colliderCfg instanceof Value v) {
            String type = str(v, "type", "mesh");

            return switch (type) {
                case "box" -> {
                    Vector3f he = PhysicsValueParsers.vec3(member(v, "halfExtents"), 0, 0, 0);
                    if (he.lengthSquared() > 0) yield new BoxCollisionShape(he);
                    // fallback from bounds
                    yield CollisionShapeFactory.createDynamicMeshShape(spatial); // stable fallback
                }
                case "sphere" -> new SphereCollisionShape((float) num(v, "radius", 1.0));
                case "capsule" -> new CapsuleCollisionShape((float) num(v, "radius", 0.5), (float) num(v, "height", 1.0));
                case "cylinder" -> {
                    Vector3f he2 = PhysicsValueParsers.vec3(member(v, "halfExtents"), 0.5f, 0.5f, 0.5f);
                    yield new CylinderCollisionShape(he2);
                }
                case "mesh" -> CollisionShapeFactory.createMeshShape(spatial);
                case "dynamicMesh" -> CollisionShapeFactory.createDynamicMeshShape(spatial);
                default -> throw new IllegalArgumentException("Unknown collider.type: " + type);
            };
        }

// Map
        if (colliderCfg instanceof Map<?, ?> m) {

            Object typeObj = m.get("type");
            String type = (typeObj != null)
                    ? String.valueOf(typeObj)
                    : "mesh";

            return switch (type) {

                case "box" -> {
                    Object heObj = m.get("halfExtents");
                    Vector3f he = PhysicsValueParsers.vec3(heObj, 0, 0, 0);
                    if (he.lengthSquared() > 0) {
                        yield new BoxCollisionShape(he);
                    }
                    yield CollisionShapeFactory.createDynamicMeshShape(spatial);
                }

                case "sphere" -> new SphereCollisionShape(
                        (float) PhysicsValueParsers.asNum(m.get("radius"), 1.0)
                );

                case "capsule" -> new CapsuleCollisionShape(
                        (float) PhysicsValueParsers.asNum(m.get("radius"), 0.5),
                        (float) PhysicsValueParsers.asNum(m.get("height"), 1.0)
                );

                case "cylinder" -> {
                    Vector3f he2 = PhysicsValueParsers.vec3(
                            m.get("halfExtents"),
                            0.5f, 0.5f, 0.5f
                    );
                    yield new CylinderCollisionShape(he2);
                }

                case "mesh" ->
                        CollisionShapeFactory.createMeshShape(spatial);

                case "dynamicMesh" ->
                        CollisionShapeFactory.createDynamicMeshShape(spatial);

                default ->
                        throw new IllegalArgumentException("Unknown collider.type: " + type);
            };
        }

        throw new IllegalArgumentException("collider must be object/value/map: " + colliderCfg);
    }

    private static Value member(Value v, String k) {
        try { return (v != null && v.hasMember(k)) ? v.getMember(k) : null; }
        catch (Throwable t) { return null; }
    }

    private static String str(Value v, String k, String def) {
        try {
            if (v == null || v.isNull()) return def;
            if (!v.hasMember(k)) return def;
            Value m = v.getMember(k);
            if (m == null || m.isNull()) return def;
            return m.isString() ? m.asString() : String.valueOf(m);
        } catch (Throwable t) {
            return def;
        }
    }

    private static double num(Value v, String k, double def) {
        try {
            if (v == null || v.isNull()) return def;
            if (!v.hasMember(k)) return def;
            Value m = v.getMember(k);
            if (m == null || m.isNull()) return def;
            return m.isNumber() ? m.asDouble() : def;
        } catch (Throwable t) {
            return def;
        }
    }
}