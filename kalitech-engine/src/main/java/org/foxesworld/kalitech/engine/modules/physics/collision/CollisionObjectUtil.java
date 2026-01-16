// FILE: org/foxesworld/kalitech/engine/modules/physics/collision/CollisionObjectUtil.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.collision;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

    /**
     * Returns a canonical key for any Bullet/JME collision object.
     *
     * <p>We prefer {@link PhysicsRigidBody} because it is what most collision callbacks
     * return and it remains stable across wrappers. For objects that can expose an underlying
     * rigid body via {@code getRigidBody()} or {@code getBody()}, that value is used.
     * </p>
     */
    public static Object canonicalCollisionKey(Object collisionObject) {
        if (collisionObject == null) return null;

        if (collisionObject instanceof PhysicsRigidBody) return collisionObject;

        if (collisionObject instanceof RigidBodyControl rb) {
            PhysicsRigidBody prb = extractPhysicsRigidBody(rb);
            return (prb != null) ? prb : rb;
        }

        // Some callbacks may return a collision object wrapper that can expose the rigid body.
        try {
            Method m = collisionObject.getClass().getMethod("getRigidBody");
            Object v = m.invoke(collisionObject);
            if (v instanceof PhysicsRigidBody prb) return prb;
        } catch (Throwable ignored) {
        }

        try {
            Method m = collisionObject.getClass().getMethod("getBody");
            Object v = m.invoke(collisionObject);
            if (v instanceof PhysicsRigidBody prb) return prb;
        } catch (Throwable ignored) {
        }

        return collisionObject;
    }

    /**
     * Returns a stable key representing the underlying Bullet body.
     * Prefers PhysicsRigidBody if accessible; otherwise falls back to RigidBodyControl.
     */
    public static Object collisionKeyFromHandle(PhysicsBodyHandle h) {
        if (h == null) return null;

        Object raw = h.__raw(); // usually RigidBodyControl
        if (raw instanceof RigidBodyControl rb) {
            PhysicsRigidBody prb = extractPhysicsRigidBody(rb);
            return (prb != null) ? prb : rb;
        }
        return raw;
    }

    /**
     * Best-effort extraction of PhysicsRigidBody from RigidBodyControl.
     */
    public static PhysicsRigidBody extractPhysicsRigidBody(RigidBodyControl rb) {
        if (rb == null) return null;

        if (RBC_BODY_FIELD != null) {
            try {
                Object v = RBC_BODY_FIELD.get(rb);
                if (v instanceof PhysicsRigidBody prb) return prb;
            } catch (Throwable ignored) {
            }
        }

        try {
            Method m = rb.getClass().getMethod("getRigidBody");
            Object v = m.invoke(rb);
            if (v instanceof PhysicsRigidBody prb) return prb;
        } catch (Throwable ignored) {
        }

        try {
            Method m = rb.getClass().getMethod("getBody");
            Object v = m.invoke(rb);
            if (v instanceof PhysicsRigidBody prb) return prb;
        } catch (Throwable ignored) {
        }

        return null;
    }

    /**
     * Reads userObject from any Bullet/JME collision object if such method exists.
     */
    public static Object tryGetUserObject(Object collisionObject) {
        if (collisionObject == null) return null;
        try {
            Method m = collisionObject.getClass().getMethod("getUserObject");
            return m.invoke(collisionObject);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Sets userObject only if object supports it and current value is null.
     * Does not override existing userObject to avoid breaking external logic.
     */
    public static void trySetUserObjectIfEmpty(Object collisionObject, Object value) {
        if (collisionObject == null || value == null) return;

        try {
            Method get = collisionObject.getClass().getMethod("getUserObject");
            Object cur = get.invoke(collisionObject);
            if (cur != null) return;

            Method set = collisionObject.getClass().getMethod("setUserObject", Object.class);
            set.invoke(collisionObject, value);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Ensures both RBC and underlying PRB carry userObject (if empty).
     * This increases probability that collision events can be resolved deterministically.
     */
    public static void tryBindUserObject(RigidBodyControl rb, Object userObject) {
        if (rb == null || userObject == null) return;

        trySetUserObjectIfEmpty(rb, userObject);

        PhysicsRigidBody prb = extractPhysicsRigidBody(rb);
        if (prb != null) {
            trySetUserObjectIfEmpty(prb, userObject);
        }
    }
}