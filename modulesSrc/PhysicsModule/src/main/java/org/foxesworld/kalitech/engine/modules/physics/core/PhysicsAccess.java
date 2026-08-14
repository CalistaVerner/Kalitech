/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 */
package org.foxesworld.kalitech.engine.modules.physics.core;

import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

public final class PhysicsAccess {
    private PhysicsAccess() {
    }

    public static PhysicsRigidBody body(PhysicsCollisionObject obj) {
        PhysicsRigidBody rb;
        return obj instanceof PhysicsRigidBody ? (rb = (PhysicsRigidBody)obj) : null;
    }

    public static float mass(PhysicsCollisionObject obj) {
        PhysicsRigidBody rb = PhysicsAccess.body(obj);
        return rb != null ? rb.getMass() : 0.0f;
    }

    public static Vector3f location(PhysicsCollisionObject obj, Vector3f store) {
        PhysicsRigidBody rb = PhysicsAccess.body(obj);
        if (rb == null) {
            return store.set(0.0f, 0.0f, 0.0f);
        }
        return rb.getPhysicsLocation(store);
    }

    public static Vector3f velocity(PhysicsCollisionObject obj, Vector3f store) {
        PhysicsRigidBody rb = PhysicsAccess.body(obj);
        if (rb == null) {
            return store.set(0.0f, 0.0f, 0.0f);
        }
        return rb.getLinearVelocity(store);
    }

    public static boolean isDynamic(PhysicsCollisionObject obj) {
        PhysicsRigidBody rb = PhysicsAccess.body(obj);
        return rb != null && rb.getMass() > 0.0f;
    }
}

