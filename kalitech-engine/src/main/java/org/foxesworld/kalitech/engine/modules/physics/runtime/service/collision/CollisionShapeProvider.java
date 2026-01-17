// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/CollisionShapeProvider.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service.collision;

import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.body.PhysicsBodyConfig;

/**
 * Strategy for resolving collision shapes for bodies.
 */
public interface CollisionShapeProvider {
    CollisionShape resolveShape(PhysicsBodyConfig cfg, Spatial spatial);
}