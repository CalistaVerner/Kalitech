// FILE: org/foxesworld/kalitech/engine/modules/physics/util/PhysicsApiUtils.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsValueParsers;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;

import static org.foxesworld.kalitech.engine.modules.physics.PhysicsJs.*;

public final class PhysicsApiUtils {

    private PhysicsApiUtils() {
    }

    public static String entityOfSpatial(Spatial sp) {
        if (sp == null) return null;

        Object v = safeUserData(sp, "entityUuid");
        if (v != null) return String.valueOf(v);

        v = safeUserData(sp, "entityId");
        if (v != null) return String.valueOf(v);

        v = safeUserData(sp, "uuid");
        if (v != null) return String.valueOf(v);

        return null;
    }

    private static Object safeUserData(Spatial sp, String key) {
        try {
            return sp.getUserData(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static ProxyObject jsVec3SafePos(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Vector3f v = rb.getPhysicsLocation();
            return (v == null) ? null : jsVec3(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static ProxyObject jsVec3SafeVel(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Vector3f v = rb.getLinearVelocity();
            return (v == null) ? null : jsVec3(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static ProxyObject jsVec3SafeAngVel(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Vector3f v = rb.getAngularVelocity();
            return (v == null) ? null : jsVec3(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static ProxyObject jsQuatSafe(RigidBodyControl rb) {
        if (rb == null) return null;
        try {
            Quaternion q = rb.getPhysicsRotation();
            return (q == null) ? null : jsQuat(q);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isActiveSafe(RigidBodyControl rb) {
        if (rb == null) return false;
        try {
            return rb.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static float massSafe(RigidBodyControl rb) {
        if (rb == null) return 0f;
        try {
            return rb.getMass();
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    public static boolean isKinematicSafe(RigidBodyControl rb) {
        if (rb == null) return false;
        try {
            return rb.isKinematic();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static ProxyObject groupsSafe(RigidBodyControl rb) {
        if (rb == null) return null;

        int group = 0;
        int mask = 0;

        try {
            group = rb.getCollisionGroup();
        } catch (Throwable ignored) {
        }
        try {
            mask = rb.getCollideWithGroups();
        } catch (Throwable ignored) {
        }

        if (group == 0 && mask == 0) return null;
        return evtJs("group", group, "mask", mask);
    }

    public static int resolveSurfaceId(Object cfg) {
        Object s = PhysicsValueParsers.member(cfg, "surface");
        if (s == null) return 0;

        if (s instanceof Number n) return n.intValue();

        if (s instanceof Value v) {
            if (v.isNumber()) return v.asInt();

            if (v.hasMember("id")) {
                Value id = v.getMember("id");
                if (id != null) {
                    if (id.isNumber()) return id.asInt();
                    if (id.canExecute()) {
                        Value r = id.execute();
                        if (r != null && r.isNumber()) return r.asInt();
                    }
                }
            }

            if (v.hasMember("surfaceId")) {
                Value sid = v.getMember("surfaceId");
                if (sid != null) {
                    if (sid.isNumber()) return sid.asInt();
                    if (sid.canExecute()) {
                        Value r = sid.execute();
                        if (r != null && r.isNumber()) return r.asInt();
                    }
                }
            }
        }

        if (s instanceof SurfaceApi.SurfaceHandle h) return h.id;

        if (s instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        throw new IllegalArgumentException("physics.body: surface must be surfaceId or SurfaceHandle");
    }

    public static int resolveBodyId(Object handleOrId) {
        if (handleOrId == null) return 0;

        if (handleOrId instanceof Number n) return n.intValue();
        if (handleOrId instanceof org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle h)
            return h.id;

        if (handleOrId instanceof Value v) {
            if (v.isNumber()) return v.asInt();

            if (v.hasMember("id")) {
                Value id = v.getMember("id");
                if (id != null) {
                    if (id.isNumber()) return id.asInt();
                    if (id.canExecute()) {
                        Value r = id.execute();
                        if (r != null && r.isNumber()) return r.asInt();
                    }
                }
            }

            if (v.hasMember("bodyId")) {
                Value bid = v.getMember("bodyId");
                if (bid != null) {
                    if (bid.isNumber()) return bid.asInt();
                    if (bid.canExecute()) {
                        Value r = bid.execute();
                        if (r != null && r.isNumber()) return r.asInt();
                    }
                }
            }
        }

        if (handleOrId instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        return 0;
    }
}