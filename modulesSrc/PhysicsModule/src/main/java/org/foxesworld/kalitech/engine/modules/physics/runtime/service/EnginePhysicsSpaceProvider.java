/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.api.EngineApiImpl
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import com.jme3.bullet.PhysicsSpace;
import java.util.Objects;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsSpaceProvider;

final class EnginePhysicsSpaceProvider
implements PhysicsSpaceProvider {
    private final EngineApiImpl engine;

    EnginePhysicsSpaceProvider(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public PhysicsSpace getSpaceOrNull() {
        return this.engine.__getPhysicsSpaceOrNull();
    }

    @Override
    public PhysicsSpace requireSpace() {
        PhysicsSpace s = this.engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            throw new IllegalStateException("[physics] PhysicsSpace not bound. RuntimeAppState must attach BulletAppState and call engineApi.__setPhysicsSpace(space).");
        }
        return s;
    }
}

