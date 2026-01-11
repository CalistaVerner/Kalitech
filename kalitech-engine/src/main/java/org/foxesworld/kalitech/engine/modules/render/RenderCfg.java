// FILE: org/foxesworld/kalitech/engine/modules/render/RenderCfg.java
package org.foxesworld.kalitech.engine.modules.render;

import org.graalvm.polyglot.Value;

public final class RenderCfg {

    private RenderCfg() {
    }

    public static Value member(Value v, String key) {
        if (v == null || v.isNull()) return null;
        if (!v.hasMember(key)) return null;
        Value m = v.getMember(key);
        if (m == null || m.isNull()) return null;
        return m;
    }

    public static boolean bool(Value v, String key, boolean def) {
        Value m = member(v, key);
        if (m == null) return def;
        if (!m.isBoolean()) throw new IllegalArgumentException("[render] cfg '" + key + "' must be boolean");
        return m.asBoolean();
    }

    public static double num(Value v, String key, double def) {
        Value m = member(v, key);
        if (m == null) return def;
        if (!m.fitsInDouble()) throw new IllegalArgumentException("[render] cfg '" + key + "' must be number");
        return m.asDouble();
    }

    public static double numPath(Value cfg, String objKey, String key, double def) {
        Value o = member(cfg, objKey);
        if (o == null) return def;
        return num(o, key, def);
    }

    public static float vec3x(Value v, float def) {
        if (v == null || v.isNull()) return def;
        if (v.hasMember("x")) {
            Value m = v.getMember("x");
            if (m != null && !m.isNull()) return (float) m.asDouble();
        }
        if (v.hasArrayElements() && v.getArraySize() > 0) {
            return (float) v.getArrayElement(0).asDouble();
        }
        return def;
    }

    public static float vec3y(Value v, float def) {
        if (v == null || v.isNull()) return def;
        if (v.hasMember("y")) {
            Value m = v.getMember("y");
            if (m != null && !m.isNull()) return (float) m.asDouble();
        }
        if (v.hasArrayElements() && v.getArraySize() > 1) {
            return (float) v.getArrayElement(1).asDouble();
        }
        return def;
    }

    public static float vec3z(Value v, float def) {
        if (v == null || v.isNull()) return def;
        if (v.hasMember("z")) {
            Value m = v.getMember("z");
            if (m != null && !m.isNull()) return (float) m.asDouble();
        }
        if (v.hasArrayElements() && v.getArraySize() > 2) {
            return (float) v.getArrayElement(2).asDouble();
        }
        return def;
    }

    public static boolean approx(float a, float b) {
        if (Float.isNaN(a) || Float.isNaN(b)) return false;
        return Math.abs(a - b) <= 1e-6f;
    }

    public static boolean approx3(float ax, float ay, float az, float bx, float by, float bz) {
        if (Float.isNaN(ax) || Float.isNaN(ay) || Float.isNaN(az)) return false;
        if (Float.isNaN(bx) || Float.isNaN(by) || Float.isNaN(bz)) return false;
        return Math.abs(ax - bx) <= 1e-6f && Math.abs(ay - by) <= 1e-6f && Math.abs(az - bz) <= 1e-6f;
    }

    public static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    public static float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}