// FILE: org/foxesworld/kalitech/engine/modules/physics/core/PhysicsAccess.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.core;

import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

/**
 * Fast, allocation-free accessors for physics objects.
 */
public final class PhysicsAccess {

    private PhysicsAccess() {
    }

    public static PhysicsRigidBody body(PhysicsCollisionObject obj) {
        return (obj instanceof PhysicsRigidBody rb) ? rb : null;
    }

    public static float mass(PhysicsCollisionObject obj) {
        PhysicsRigidBody rb = body(obj);
        return rb != null ? rb.getMass() : 0f;
    }

    public static Vector3f location(PhysicsCollisionObject obj, Vector3f store) {
        PhysicsRigidBody rb = body(obj);
        if (rb == null) return store.set(0f, 0f, 0f);
        return rb.getPhysicsLocation(store);
    }

    public static Vector3f velocity(PhysicsCollisionObject obj, Vector3f store) {
        PhysicsRigidBody rb = body(obj);
        if (rb == null) return store.set(0f, 0f, 0f);
        return rb.getLinearVelocity(store);
    }

    public static boolean isDynamic(PhysicsCollisionObject obj) {
        PhysicsRigidBody rb = body(obj);
        return rb != null && rb.getMass() > 0f;
    }
}