// FILE: org/foxesworld/kalitech/engine/modules/physics/CollisionObjectUtil.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;

import java.lang.reflect.Field;

/**
 * Bullet/JME collision object identity helpers.
 * Ensures map keys match objects returned by:
 * - PhysicsCollisionEvent.getObjectA/B()
 * - PhysicsRayTestResult.getCollisionObject()
 */
public final class CollisionObjectUtil {

    private static final Field RBC_BODY_FIELD = findRbcBodyField();

    private CollisionObjectUtil() {
    }

    private static Field findRbcBodyField() {
        try {
            Field f = RigidBodyControl.class.getDeclaredField("body");
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object collisionKeyFromHandle(PhysicsBodyHandle h) {
        if (h == null) return null;

        Object raw = h.__raw(); // usually RigidBodyControl
        if (raw instanceof RigidBodyControl rb) {
            PhysicsRigidBody prb = extractPhysicsRigidBody(rb);
            return (prb != null) ? prb : rb;
        }
        return raw;
    }

    public static PhysicsRigidBody extractPhysicsRigidBody(RigidBodyControl rb) {
        if (rb == null) return null;

        if (RBC_BODY_FIELD != null) {
            try {
                Object v = RBC_BODY_FIELD.get(rb);
                if (v instanceof PhysicsRigidBody prb) return prb;
            } catch (Throwable ignored) {
            }
        }

        // best-effort reflection for various jME builds/forks
        try {
            var m = rb.getClass().getMethod("getRigidBody");
            Object v = m.invoke(rb);
            if (v instanceof PhysicsRigidBody prb) return prb;
        } catch (Throwable ignored) {
        }

        try {
            var m = rb.getClass().getMethod("getBody");
            Object v = m.invoke(rb);
            if (v instanceof PhysicsRigidBody prb) return prb;
        } catch (Throwable ignored) {
        }

        return null;
    }
}