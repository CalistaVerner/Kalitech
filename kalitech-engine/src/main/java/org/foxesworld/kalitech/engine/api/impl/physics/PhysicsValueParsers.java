// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl.physics;

import com.jme3.math.Vector3f;
import org.graalvm.polyglot.Value;

import java.util.Map;

/**
 * Minimal coercion helpers for JS Value / Map / primitives.
 */
public final class PhysicsValueParsers {

    private PhysicsValueParsers() {}

    public static Object member(Object obj, String key) {
        if (obj == null) return null;
        if (obj instanceof Value v) return v.hasMember(key) ? v.getMember(key) : null;
        if (obj instanceof Map<?, ?> m) return m.get(key);
        return null;
    }

    public static boolean asBool(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        if (v instanceof Value val) return val.isBoolean() ? val.asBoolean() : def;
        return def;
    }

    public static double asNum(Object v, double def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Value val) return val.isNumber() ? val.asDouble() : def;
        return def;
    }

    public static int asInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof Value val) return val.isNumber() ? val.asInt() : def;
        return def;
    }

    public static Vector3f vec3(Object v, float dx, float dy, float dz) {
        if (v == null) return new Vector3f(dx, dy, dz);

        if (v instanceof Vector3f vv) return vv;

        if (v instanceof Value val) {
            if (val.isNull()) return new Vector3f(dx, dy, dz);

            if (val.hasArrayElements() && val.getArraySize() >= 3) {
                return new Vector3f(
                        (float) asNum(val.getArrayElement(0), dx),
                        (float) asNum(val.getArrayElement(1), dy),
                        (float) asNum(val.getArrayElement(2), dz)
                );
            }

            if (val.hasMembers()) {
                return new Vector3f(
                        (float) asNum(member(val, "x"), dx),
                        (float) asNum(member(val, "y"), dy),
                        (float) asNum(member(val, "z"), dz)
                );
            }
        }

        if (v instanceof Map<?, ?> m) {
            Object arr = m.get("0");
            if (arr != null) return vec3(arr, dx, dy, dz);

            return new Vector3f(
                    (float) asNum(m.get("x"), dx),
                    (float) asNum(m.get("y"), dy),
                    (float) asNum(m.get("z"), dz)
            );
        }

        return new Vector3f(dx, dy, dz);
    }
}