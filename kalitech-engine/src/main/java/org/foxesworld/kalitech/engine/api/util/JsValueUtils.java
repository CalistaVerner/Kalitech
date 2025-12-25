// FILE: org/foxesworld/kalitech/engine/api/util/JsValueUtils.java
package org.foxesworld.kalitech.engine.api.util;

import org.graalvm.polyglot.Value;

public final class JsValueUtils {
    private JsValueUtils() {}

    public static boolean isNull(Value v) {
        return v == null || v.isNull();
    }

    public static Value member(Value v, String key) {
        return (v != null && v.hasMember(key)) ? v.getMember(key) : null;
    }

    public static String str(Value v, String key, String def) {
        try {
            Value m = member(v, key);
            if (m == null || m.isNull()) return def;
            if (m.isString()) return m.asString();
            return String.valueOf(m);
        } catch (Throwable t) {
            return def;
        }
    }

    public static double num(Value v, String key, double def) {
        try {
            Value m = member(v, key);
            if (m == null || m.isNull()) return def;
            if (m.isNumber()) return m.asDouble();
            if (m.isBoolean()) return m.asBoolean() ? 1.0 : 0.0;
            return def;
        } catch (Throwable t) {
            return def;
        }
    }

    public static boolean bool(Value v, String key, boolean def) {
        try {
            Value m = member(v, key);
            if (m == null || m.isNull()) return def;
            if (m.isBoolean()) return m.asBoolean();
            if (m.isNumber()) return m.asDouble() != 0.0;
            if (m.isString()) {
                String s = m.asString().trim();
                if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equals("1")) return true;
                if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") || s.equals("0")) return false;
            }
            return def;
        } catch (Throwable t) {
            return def;
        }
    }

    public static int clampInt(double v, int min, int max) {
        int x = (int) Math.round(v);
        return Math.max(min, Math.min(max, x));
    }

    public static float clampFloat(double v, float min, float max) {
        float x = (float) v;
        return Math.max(min, Math.min(max, x));
    }
}