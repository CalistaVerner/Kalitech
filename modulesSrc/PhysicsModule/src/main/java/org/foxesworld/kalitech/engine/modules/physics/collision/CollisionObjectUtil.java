/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.physics.collision;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;

public final class CollisionObjectUtil {
    private static final Field RBC_BODY_FIELD = CollisionObjectUtil.findRbcBodyField();
    private static final ClassValue<Accessors> ACCESSORS = new ClassValue<Accessors>(){

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
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    public static Object canonicalCollisionKey(Object collisionObject) {
        if (collisionObject == null) {
            return null;
        }
        if (collisionObject instanceof PhysicsRigidBody) {
            return collisionObject;
        }
        if (collisionObject instanceof RigidBodyControl) {
            RigidBodyControl rbc = (RigidBodyControl)collisionObject;
            PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rbc);
            return prb != null ? prb : rbc;
        }
        PhysicsRigidBody prb = ACCESSORS.get(collisionObject.getClass()).tryGetRigidBody(collisionObject);
        if (prb != null) {
            return prb;
        }
        return collisionObject;
    }

    public static Object collisionKeyFromHandle(PhysicsBodyHandle h) {
        if (h == null) {
            return null;
        }
        RigidBodyControl raw = h.__raw();
        if (raw instanceof RigidBodyControl) {
            RigidBodyControl rbc = raw;
            PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rbc);
            return prb != null ? prb : rbc;
        }
        return raw;
    }

    public static PhysicsRigidBody extractPhysicsRigidBody(RigidBodyControl rb) {
        if (rb == null) {
            return null;
        }
        if (RBC_BODY_FIELD != null) {
            try {
                Object v = RBC_BODY_FIELD.get(rb);
                if (v instanceof PhysicsRigidBody) {
                    PhysicsRigidBody prb = (PhysicsRigidBody)v;
                    return prb;
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return ACCESSORS.get(rb.getClass()).tryGetRigidBody(rb);
    }

    public static Object tryGetUserObject(Object collisionObject) {
        if (collisionObject == null) {
            return null;
        }
        return ACCESSORS.get(collisionObject.getClass()).tryGetUserObject(collisionObject);
    }

    public static void trySetUserObjectIfEmpty(Object collisionObject, Object value) {
        if (collisionObject == null || value == null) {
            return;
        }
        Accessors a = ACCESSORS.get(collisionObject.getClass());
        Object cur = a.tryGetUserObject(collisionObject);
        if (cur != null) {
            return;
        }
        a.trySetUserObject(collisionObject, value);
    }

    public static void tryBindUserObject(RigidBodyControl rb, Object userObject) {
        if (rb == null || userObject == null) {
            return;
        }
        CollisionObjectUtil.trySetUserObjectIfEmpty(rb, userObject);
        PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
        if (prb != null) {
            CollisionObjectUtil.trySetUserObjectIfEmpty(prb, userObject);
        }
    }

    private static final class Accessors {
        private final Method getRigidBody;
        private final Method getBody;
        private final Method getUserObject;
        private final Method setUserObject;

        Accessors(Class<?> type) {
            this.getRigidBody = Accessors.findNoArg(type, "getRigidBody");
            this.getBody = Accessors.findNoArg(type, "getBody");
            this.getUserObject = Accessors.findNoArg(type, "getUserObject");
            this.setUserObject = Accessors.findSetter(type, "setUserObject", Object.class);
        }

        private static Object invokeNoArg(Object target, Method m) {
            if (m == null) {
                return null;
            }
            try {
                return m.invoke(target, new Object[0]);
            }
            catch (Throwable ignored) {
                return null;
            }
        }

        private static Method findNoArg(Class<?> type, String name) {
            try {
                Method m = type.getMethod(name, new Class[0]);
                m.setAccessible(true);
                return m;
            }
            catch (Throwable ignored) {
                return null;
            }
        }

        private static Method findSetter(Class<?> type, String name, Class<?> param) {
            try {
                Method m = type.getMethod(name, param);
                m.setAccessible(true);
                return m;
            }
            catch (Throwable ignored) {
                return null;
            }
        }

        PhysicsRigidBody tryGetRigidBody(Object target) {
            Objects.requireNonNull(target, "target");
            Object v = Accessors.invokeNoArg(target, this.getRigidBody);
            if (v instanceof PhysicsRigidBody) {
                PhysicsRigidBody prb = (PhysicsRigidBody)v;
                return prb;
            }
            v = Accessors.invokeNoArg(target, this.getBody);
            if (v instanceof PhysicsRigidBody) {
                PhysicsRigidBody prb2 = (PhysicsRigidBody)v;
                return prb2;
            }
            return null;
        }

        Object tryGetUserObject(Object target) {
            Objects.requireNonNull(target, "target");
            return Accessors.invokeNoArg(target, this.getUserObject);
        }

        void trySetUserObject(Object target, Object value) {
            Objects.requireNonNull(target, "target");
            if (this.setUserObject == null) {
                return;
            }
            try {
                this.setUserObject.invoke(target, value);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }
}

