// FILE: org/foxesworld/kalitech/engine/api/impl/PhysicsValueParsers.java
package org.foxesworld.kalitech.engine.api.impl.physics;

import com.jme3.math.Vector3f;
import org.graalvm.polyglot.Value;

import java.util.Map;

public final class PhysicsValueParsers {
    private PhysicsValueParsers() {}

    public static Vector3f vec3(Object v, float dx, float dy, float dz) {
        try {
            if (v == null) return new Vector3f(dx, dy, dz);

            if (v instanceof Vector3f vv) return vv;

            if (v instanceof Value val) {
                if (val.isNull()) return new Vector3f(dx, dy, dz);

                if (val.hasArrayElements() && val.getArraySize() >= 3) {
                    float x = (float) val.getArrayElement(0).asDouble();
                    float y = (float) val.getArrayElement(1).asDouble();
                    float z = (float) val.getArrayElement(2).asDouble();
                    return new Vector3f(x, y, z);
                }
                float x = (float) num(val, "x", dx);
                float y = (float) num(val, "y", dy);
                float z = (float) num(val, "z", dz);
                return new Vector3f(x, y, z);
            }

            if (v instanceof Map<?, ?> m) {
                float x = (float) asNum(m.get("x"), dx);
                float y = (float) asNum(m.get("y"), dy);
                float z = (float) asNum(m.get("z"), dz);
                return new Vector3f(x, y, z);
            }
        } catch (Throwable ignored) {}
        return new Vector3f(dx, dy, dz);
    }

    static double asNum(Object v, double def) {
        try {
            if (v == null) return def;
            if (v instanceof Number n) return n.doubleValue();
            if (v instanceof Value val) return val.isNumber() ? val.asDouble() : def;
            return Double.parseDouble(String.valueOf(v));
        } catch (Throwable t) {
            return def;
        }
    }

    static boolean asBool(Object v, boolean def) {
        try {
            if (v == null) return def;
            if (v instanceof Boolean b) return b;
            if (v instanceof Number n) return n.doubleValue() != 0.0;
            if (v instanceof Value val) {
                if (val.isBoolean()) return val.asBoolean();
                if (val.isNumber()) return val.asDouble() != 0.0;
                if (val.isString()) {
                    String s = val.asString().trim();
                    if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equals("1")) return true;
                    if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") || s.equals("0")) return false;
                }
            }
        } catch (Throwable ignored) {}
        return def;
    }

    static Object member(Object cfg, String key) {
        try {
            if (cfg instanceof Value v) {
                if (v.hasMember(key)) return v.getMember(key);
            } else if (cfg instanceof Map<?, ?> m) {
                return m.get(key);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    static double num(Value v, String k, double def) {
        try {
            if (v == null || v.isNull()) return def;
            if (!v.hasMember(k)) return def;
            Value m = v.getMember(k);
            if (m == null || m.isNull()) return def;
            return m.isNumber() ? m.asDouble() : def;
        } catch (Throwable t) {
            return def;
        }
    }
}