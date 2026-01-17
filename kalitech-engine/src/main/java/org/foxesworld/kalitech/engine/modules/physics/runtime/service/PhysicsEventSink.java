// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/PhysicsEventSink.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;

/**
 * Abstraction for physics-related event publishing.
 */
public interface PhysicsEventSink {

    void emitBodyCreated(PhysicsBodyHandle h, float mass, boolean kinematic, boolean lockRotation, String entity);

    void emitBodyRemoved(PhysicsBodyHandle h, String entity);

    void emitBodyAdded(PhysicsBodyHandle h, SurfaceRegistry surfaces, String entity);
}