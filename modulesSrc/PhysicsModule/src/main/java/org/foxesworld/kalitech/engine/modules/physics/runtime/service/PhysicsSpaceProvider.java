/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import com.jme3.bullet.PhysicsSpace;

public interface PhysicsSpaceProvider {
    public PhysicsSpace getSpaceOrNull();

    public PhysicsSpace requireSpace();
}

