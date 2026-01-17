// FILE: org/foxesworld/kalitech/engine/modules/physics/util/PhysicsValueParsers.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

import com.jme3.math.Vector3f;
import org.graalvm.polyglot.Value;

import java.util.Map;

/**
 * Fast value parsers for JS/Map payloads.
 */
public final class PhysicsValueParsers {

    private PhysicsValueParsers() {
    }

    public static Object member(Object obj, String key) {
        if (obj == null) return null;
        if (obj instanceof Value v) return v.hasMember(key) ? v.getMember(key) : null;
        if (obj instanceof Map<?, ?> m) return m.get(key);
        return null;
    }

    public static boolean asBool(Object v, boolean def) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Value val) return val.isBoolean() ? val.asBoolean() : def;
        return def;
    }

    public static double asNum(Object v, double def) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Value val) return val.isNumber() ? val.asDouble() : def;
        return def;
    }

    public static float asFloat(Object v, float def) {
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof Value val) return val.isNumber() ? (float) val.asDouble() : def;
        return def;
    }

    public static int asInt(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof Value val) return val.isNumber() ? val.asInt() : def;
        return def;
    }

    public static void vec3Into(Object v, Vector3f out, float dx, float dy, float dz) {
        if (out == null) return;

        if (v == null) {
            out.set(dx, dy, dz);
            return;
        }

        if (v instanceof Vector3f vv) {
            out.set(vv);
            return;
        }

        if (v instanceof Value val) {
            if (val.isNull()) {
                out.set(dx, dy, dz);
                return;
            }

            if (val.hasArrayElements() && val.getArraySize() >= 3) {
                out.set(
                        asFloat(val.getArrayElement(0), dx),
                        asFloat(val.getArrayElement(1), dy),
                        asFloat(val.getArrayElement(2), dz)
                );
                return;
            }

            if (val.hasMembers()) {
                out.set(
                        asFloat(member(val, "x"), dx),
                        asFloat(member(val, "y"), dy),
                        asFloat(member(val, "z"), dz)
                );
                return;
            }
        }

        if (v instanceof Map<?, ?> m) {
            out.set(
                    asFloat(m.get("x"), dx),
                    asFloat(m.get("y"), dy),
                    asFloat(m.get("z"), dz)
            );
            return;
        }

        out.set(dx, dy, dz);
    }

    public static Vector3f vec3(Object v, float dx, float dy, float dz) {
        Vector3f out = new Vector3f();
        vec3Into(v, out, dx, dy, dz);
        return out;
    }
}