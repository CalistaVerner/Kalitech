// FILE: org/foxesworld/kalitech/engine/modules/physics/shapes/ShapeKey.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.shapes;

import com.jme3.scene.Mesh;

/**
 * Cache key for collision shapes.
 */
public record ShapeKey(Mesh mesh, boolean dynamic) {
}