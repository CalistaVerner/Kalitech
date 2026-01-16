// FILE: org/foxesworld/kalitech/engine/modules/physics/PhysicsColliderFactory.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.modules.physics.collision;

import com.jme3.bullet.collision.shapes.*;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

/**
 * Collider factory for cfg.collider.
 * PhysicsModule controls defaults by mass and passes {@code dynamic}.
 */
public final class PhysicsColliderFactory {

    private PhysicsColliderFactory() {
    }

    /**
     * Creates a collision shape from collider config.
     *
     * @param colliderCfg collider configuration (Value/Map/null)
     * @param spatial     target spatial
     * @param dynamic     true if body has mass &gt; 0 (dynamic body)
     * @return collision shape
     */
    public static CollisionShape create(Object colliderCfg, Spatial spatial, boolean dynamic) {
        Objects.requireNonNull(spatial, "spatial");

        if (colliderCfg == null) {
            return dynamic
                    ? CollisionShapeFactory.createDynamicMeshShape(spatial)
                    : CollisionShapeFactory.createMeshShape(spatial);
        }

        final String type = readType(colliderCfg);

        if (dynamic && "mesh".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException(
                    "collider.type='mesh' is not allowed for dynamic bodies (mass>0). " +
                            "Use collider.type='dynamicMesh' or primitive collider."
            );
        }

        return switch (type) {
            case "box" -> createBox(colliderCfg, spatial, dynamic);
            case "sphere" -> new SphereCollisionShape((float) readNum(colliderCfg, "radius", 1.0));
            case "capsule" -> new CapsuleCollisionShape(
                    clampPositive((float) readNum(colliderCfg, "radius", 0.5), 0.001f),
                    clampPositive((float) readNum(colliderCfg, "height", 1.0), 0.001f)
            );
            case "cylinder" -> {
                Vector3f he = readHalfExtents(colliderCfg, 0.5f, 0.5f, 0.5f);
                he.x = clampPositive(he.x, 0.001f);
                he.y = clampPositive(he.y, 0.001f);
                he.z = clampPositive(he.z, 0.001f);
                yield new CylinderCollisionShape(he);
            }
            case "mesh" -> CollisionShapeFactory.createMeshShape(spatial);
            case "dynamicMesh" -> CollisionShapeFactory.createDynamicMeshShape(spatial);
            default -> throw new IllegalArgumentException("Unknown collider.type: " + type);
        };
    }

    private static CollisionShape createBox(Object cfg, Spatial spatial, boolean dynamic) {
        Vector3f he = readHalfExtents(cfg, 0f, 0f, 0f);
        if (he.lengthSquared() > 0f) {
            he.x = clampPositive(he.x, 0.001f);
            he.y = clampPositive(he.y, 0.001f);
            he.z = clampPositive(he.z, 0.001f);
            return new BoxCollisionShape(he);
        }
        // If halfExtents are missing/invalid, fallback by dynamic flag.
        return dynamic
                ? CollisionShapeFactory.createDynamicMeshShape(spatial)
                : CollisionShapeFactory.createMeshShape(spatial);
    }

    private static String readType(Object cfg) {
        if (cfg instanceof Value v) {
            return str(v, "type", "mesh");
        }
        if (cfg instanceof Map<?, ?> m) {
            Object t = m.get("type");
            return (t != null) ? String.valueOf(t) : "mesh";
        }
        throw new IllegalArgumentException("Unsupported collider cfg: " + cfg.getClass().getName());
    }

    private static double readNum(Object cfg, String key, double fallback) {
        if (cfg instanceof Value v) {
            return num(v, key, fallback);
        }
        if (cfg instanceof Map<?, ?> m) {
            return PhysicsValueParsers.asNum(m.get(key), fallback);
        }
        throw new IllegalArgumentException("Unsupported collider cfg: " + cfg.getClass().getName());
    }

    private static Vector3f readHalfExtents(Object cfg, float fx, float fy, float fz) {
        if (cfg instanceof Value v) {
            return PhysicsValueParsers.vec3(member(v, "halfExtents"), fx, fy, fz);
        }
        if (cfg instanceof Map<?, ?> m) {
            return PhysicsValueParsers.vec3(m.get("halfExtents"), fx, fy, fz);
        }
        throw new IllegalArgumentException("Unsupported collider cfg: " + cfg.getClass().getName());
    }

    private static float clampPositive(float v, float min) {
        if (!Float.isFinite(v)) return min;
        return (v < min) ? min : v;
    }
}