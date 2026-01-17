// FILE: org/foxesworld/kalitech/engine/modules/physics/collision/CollisionObjectUtil.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.collision;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Bullet/JME collision object identity helpers.
 *
 * <p>Hot-path optimized: uses per-class cached reflect accessors (ClassValue),
 * avoiding reflective lookups on every call.</p>
 */
public final class CollisionObjectUtil {

    private static final Field RBC_BODY_FIELD = findRbcBodyField();

    private static final ClassValue<Accessors> ACCESSORS = new ClassValue<>() {
        @Override
        protected Accessors computeValue(Class<?> type) {
            return new Accessors(type);
        }
    };

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
     */
    public static Object canonicalCollisionKey(Object collisionObject) {
        if (collisionObject == null) return null;

        if (collisionObject instanceof PhysicsRigidBody) return collisionObject;

        if (collisionObject instanceof RigidBodyControl rbc) {
            PhysicsRigidBody prb = extractPhysicsRigidBody(rbc);
            return (prb != null) ? prb : rbc;
        }

        PhysicsRigidBody prb = ACCESSORS.get(collisionObject.getClass()).tryGetRigidBody(collisionObject);
        if (prb != null) return prb;

        return collisionObject;
    }

    /**
     * Returns a stable key representing the underlying Bullet body.
     */
    public static Object collisionKeyFromHandle(PhysicsBodyHandle h) {
        if (h == null) return null;

        Object raw = h.__raw();
        if (raw instanceof RigidBodyControl rbc) {
            PhysicsRigidBody prb = extractPhysicsRigidBody(rbc);
            return (prb != null) ? prb : rbc;
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

        return ACCESSORS.get(rb.getClass()).tryGetRigidBody(rb);
    }

    /**
     * Reads userObject from any Bullet/JME collision object if such method exists.
     */
    public static Object tryGetUserObject(Object collisionObject) {
        if (collisionObject == null) return null;
        return ACCESSORS.get(collisionObject.getClass()).tryGetUserObject(collisionObject);
    }

    /**
     * Sets userObject only if object supports it and current value is null.
     */
    public static void trySetUserObjectIfEmpty(Object collisionObject, Object value) {
        if (collisionObject == null || value == null) return;

        Accessors a = ACCESSORS.get(collisionObject.getClass());
        Object cur = a.tryGetUserObject(collisionObject);
        if (cur != null) return;

        a.trySetUserObject(collisionObject, value);
    }

    /**
     * Ensures both RBC and underlying PRB carry userObject (if empty).
     */
    public static void tryBindUserObject(RigidBodyControl rb, Object userObject) {
        if (rb == null || userObject == null) return;

        trySetUserObjectIfEmpty(rb, userObject);

        PhysicsRigidBody prb = extractPhysicsRigidBody(rb);
        if (prb != null) {
            trySetUserObjectIfEmpty(prb, userObject);
        }
    }

    private static final class Accessors {
        private final Method getRigidBody;
        private final Method getBody;
        private final Method getUserObject;
        private final Method setUserObject;

        Accessors(Class<?> type) {
            this.getRigidBody = findNoArg(type, "getRigidBody");
            this.getBody = findNoArg(type, "getBody");
            this.getUserObject = findNoArg(type, "getUserObject");
            this.setUserObject = findSetter(type, "setUserObject", Object.class);
        }

        private static Object invokeNoArg(Object target, Method m) {
            if (m == null) return null;
            try {
                return m.invoke(target);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Method findNoArg(Class<?> type, String name) {
            try {
                Method m = type.getMethod(name);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Method findSetter(Class<?> type, String name, Class<?> param) {
            try {
                Method m = type.getMethod(name, param);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
                return null;
            }
        }

        PhysicsRigidBody tryGetRigidBody(Object target) {
            Objects.requireNonNull(target, "target");

            Object v = invokeNoArg(target, getRigidBody);
            if (v instanceof PhysicsRigidBody prb) return prb;

            v = invokeNoArg(target, getBody);
            if (v instanceof PhysicsRigidBody prb2) return prb2;

            return null;
        }

        Object tryGetUserObject(Object target) {
            Objects.requireNonNull(target, "target");
            return invokeNoArg(target, getUserObject);
        }

        void trySetUserObject(Object target, Object value) {
            Objects.requireNonNull(target, "target");
            if (setUserObject == null) return;
            try {
                setUserObject.invoke(target, value);
            } catch (Throwable ignored) {
            }
        }
    }
}