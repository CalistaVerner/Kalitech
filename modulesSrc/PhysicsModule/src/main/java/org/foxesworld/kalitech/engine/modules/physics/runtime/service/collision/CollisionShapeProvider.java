/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.scene.Spatial
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime.service.collision;

import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.body.PhysicsBodyConfig;

public interface CollisionShapeProvider {
    public CollisionShape resolveShape(PhysicsBodyConfig var1, Spatial var2);

    default public void clear() {
    }
}

