// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/EnginePhysicsSpaceProvider.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import com.jme3.bullet.PhysicsSpace;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;

import java.util.Objects;

final class EnginePhysicsSpaceProvider implements PhysicsSpaceProvider {

    private final EngineApiImpl engine;

    EnginePhysicsSpaceProvider(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public PhysicsSpace getSpaceOrNull() {
        return engine.__getPhysicsSpaceOrNull();
    }

    @Override
    public PhysicsSpace requireSpace() {
        PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            throw new IllegalStateException(
                    "[physics] PhysicsSpace not bound. RuntimeAppState must attach BulletAppState and call engineApi.__setPhysicsSpace(space)."
            );
        }
        return s;
    }
}
