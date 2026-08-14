/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 *  com.jme3.scene.Spatial
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.Map;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class PhysicsColliderFactory {
    private PhysicsColliderFactory() {
    }

    public static CollisionShape create(Object colliderCfg, Spatial spatial) {
        if (colliderCfg == null) {
            return CollisionShapeFactory.createMeshShape(spatial);
        }
        if (colliderCfg instanceof LuaValueRef) {
            String type;
            LuaValueRef v = (LuaValueRef)colliderCfg;
            return switch (type = LuaCfg.str((LuaValueRef)v, (String)"type", (String)"mesh")) {
                case "box" -> {
                    Vector3f he = PhysicsValueParsers.vec3(LuaCfg.member((LuaValueRef)v, (String)"halfExtents"), 0.0f, 0.0f, 0.0f);
                    if (he.lengthSquared() > 0.0f) {
                        yield new BoxCollisionShape(he);
                    }
                    yield CollisionShapeFactory.createDynamicMeshShape(spatial);
                }
                case "sphere" -> new SphereCollisionShape((float)LuaCfg.num((LuaValueRef)v, (String)"radius", (double)1.0));
                case "capsule" -> new CapsuleCollisionShape((float)LuaCfg.num((LuaValueRef)v, (String)"radius", (double)0.5), (float)LuaCfg.num((LuaValueRef)v, (String)"height", (double)1.0));
                case "cylinder" -> new CylinderCollisionShape(PhysicsValueParsers.vec3(LuaCfg.member((LuaValueRef)v, (String)"halfExtents"), 0.5f, 0.5f, 0.5f));
                case "mesh" -> CollisionShapeFactory.createMeshShape(spatial);
                case "dynamicMesh" -> CollisionShapeFactory.createDynamicMeshShape(spatial);
                default -> throw new IllegalArgumentException("Unknown collider.type: " + type);
            };
        }
        if (colliderCfg instanceof Map) {
            String type;
            Map m = (Map)colliderCfg;
            Object typeObj = m.get("type");
            return switch (type = typeObj != null ? String.valueOf(typeObj) : "mesh") {
                case "box" -> {
                    Vector3f he = PhysicsValueParsers.vec3(m.get("halfExtents"), 0.0f, 0.0f, 0.0f);
                    if (he.lengthSquared() > 0.0f) {
                        yield new BoxCollisionShape(he);
                    }
                    yield CollisionShapeFactory.createDynamicMeshShape(spatial);
                }
                case "sphere" -> new SphereCollisionShape((float)PhysicsValueParsers.asNum(m.get("radius"), 1.0));
                case "capsule" -> new CapsuleCollisionShape((float)PhysicsValueParsers.asNum(m.get("radius"), 0.5), (float)PhysicsValueParsers.asNum(m.get("height"), 1.0));
                case "cylinder" -> new CylinderCollisionShape(PhysicsValueParsers.vec3(m.get("halfExtents"), 0.5f, 0.5f, 0.5f));
                case "mesh" -> CollisionShapeFactory.createMeshShape(spatial);
                case "dynamicMesh" -> CollisionShapeFactory.createDynamicMeshShape(spatial);
                default -> throw new IllegalArgumentException("Unknown collider.type: " + type);
            };
        }
        throw new IllegalArgumentException("Unsupported collider cfg: " + colliderCfg.getClass().getName());
    }
}

