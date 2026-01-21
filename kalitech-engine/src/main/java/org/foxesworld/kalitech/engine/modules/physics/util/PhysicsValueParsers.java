// FILE: org/foxesworld/kalitech/engine/modules/physics/util/PhysicsValueParsers.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.graalvm.polyglot.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fast value parsers for JS/Map payloads.
 */
public final class PhysicsValueParsers {

    private PhysicsValueParsers() {
    }


    /**
     * Parses quaternion-like input into a JME Quaternion.
     * <p>
     * Supported inputs:
     * - JS object: {x,y,z,w}
     * - JS array: [x,y,z,w]
     * - Polyglot Value with members x/y/z/w
     */
    public static Quaternion quat(Object v, float fx, float fy, float fz, float fw) {
        if (v == null) return new Quaternion(fx, fy, fz, fw);

        if (v instanceof Quaternion q) {
            return q.clone();
        }

        if (v instanceof Value val) {
            if (val.hasArrayElements() && val.getArraySize() >= 4) {
                return new Quaternion(
                        (float) asDouble(val.getArrayElement(0), fx),
                        (float) asDouble(val.getArrayElement(1), fy),
                        (float) asDouble(val.getArrayElement(2), fz),
                        (float) asDouble(val.getArrayElement(3), fw)
                );
            }
            if (val.hasMembers()) {
                return new Quaternion(
                        (float) asDouble(member(val, "x"), fx),
                        (float) asDouble(member(val, "y"), fy),
                        (float) asDouble(member(val, "z"), fz),
                        (float) asDouble(member(val, "w"), fw)
                );
            }
        }

        if (v instanceof Map<?, ?> m) {
            return new Quaternion(
                    (float) asDouble(m.get("x"), fx),
                    (float) asDouble(m.get("y"), fy),
                    (float) asDouble(m.get("z"), fz),
                    (float) asDouble(m.get("w"), fw)
            );
        }

        return new Quaternion(fx, fy, fz, fw);
    }

    /**
     * Returns a JS-friendly quaternion object {x,y,z,w}.
     */
    public static Map<String, Object> quatOut(Quaternion q) {
        final Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("x", (double) q.getX());
        out.put("y", (double) q.getY());
        out.put("z", (double) q.getZ());
        out.put("w", (double) q.getW());
        return out;
    }

    private static Value member(Value v, String name) {
        return (v != null && v.hasMember(name)) ? v.getMember(name) : null;
    }

    private static double asDouble(Object v, double fb) {
        if (v == null) return fb;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Value val) {
            if (val.isNumber()) return val.asDouble();
        }
        return fb;
    }

    public static void quatInto(Object v, Quaternion out, float fx, float fy, float fz, float fw) {
        if (out == null) return;

        if (v == null) {
            out.set(fx, fy, fz, fw);
            return;
        }

        if (v instanceof Quaternion q) {
            out.set(q);
            return;
        }

        if (v instanceof Value val) {
            if (val.hasArrayElements() && val.getArraySize() >= 4) {
                out.set(
                        (float) asDouble(val.getArrayElement(0), fx),
                        (float) asDouble(val.getArrayElement(1), fy),
                        (float) asDouble(val.getArrayElement(2), fz),
                        (float) asDouble(val.getArrayElement(3), fw)
                );
                return;
            }
            if (val.hasMembers()) {
                out.set(
                        (float) asDouble(member(val, "x"), fx),
                        (float) asDouble(member(val, "y"), fy),
                        (float) asDouble(member(val, "z"), fz),
                        (float) asDouble(member(val, "w"), fw)
                );
                return;
            }
        }

        if (v instanceof Map<?, ?> m) {
            out.set(
                    (float) asDouble(m.get("x"), fx),
                    (float) asDouble(m.get("y"), fy),
                    (float) asDouble(m.get("z"), fz),
                    (float) asDouble(m.get("w"), fw)
            );
            return;
        }

        out.set(fx, fy, fz, fw);
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