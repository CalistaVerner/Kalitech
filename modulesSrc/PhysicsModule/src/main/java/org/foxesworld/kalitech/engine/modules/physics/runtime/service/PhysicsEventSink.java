/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;

public interface PhysicsEventSink {
    public void emitBodyCreated(PhysicsBodyHandle var1, float var2, boolean var3, boolean var4, String var5);

    public void emitBodyRemoved(PhysicsBodyHandle var1, String var2);

    public void emitBodyAdded(PhysicsBodyHandle var1, SurfaceRegistry var2, String var3);
}

