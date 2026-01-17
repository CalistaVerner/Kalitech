// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/PhysicsSpaceProvider.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import com.jme3.bullet.PhysicsSpace;

/**
 * Provides access to the active {@link PhysicsSpace}.
 */
public interface PhysicsSpaceProvider {

    /**
     * @return current space or {@code null} if not bound.
     */
    PhysicsSpace getSpaceOrNull();

    /**
     * @return current space, throws if not bound.
     */
    PhysicsSpace requireSpace();
}