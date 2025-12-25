// FILE: org/foxesworld/kalitech/engine/api/impl/HudValueParsers.java
package org.foxesworld.kalitech.engine.api.impl.hud;

import org.graalvm.polyglot.Value;

import java.util.Map;

final class HudValueParsers {

    private HudValueParsers() {}

    static Object member(Object obj, String key) {
        if (obj == null || key == null) return null;

        if (obj instanceof Value v) {
            try {
                if (v.hasMember(key)) {
                    Value m = v.getMember(key);
                    if (m == null || m.isNull()) return null;
                    return m;
                }
            } catch (Throwable ignored) {}
            return null;
        }

        if (obj instanceof Map<?, ?> m) {
            try { return m.get(key); } catch (Throwable ignored) { return null; }
        }

        return null;
    }

    static double asNum(Object v, double def) {
        if (v == null) return def;

        if (v instanceof Number n) return n.doubleValue();

        if (v instanceof Value val) {
            try {
                if (val.isNumber()) return val.asDouble();
                if (val.isString()) {
                    String s = val.asString();
                    try { return Double.parseDouble(s); } catch (Exception ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        return def;
    }

    static boolean asBool(Object v, boolean def) {
        if (v == null) return def;

        if (v instanceof Boolean b) return b;

        if (v instanceof Value val) {
            try {
                if (val.isBoolean()) return val.asBoolean();
                if (val.isString()) {
                    String s = val.asString();
                    if ("true".equalsIgnoreCase(s)) return true;
                    if ("false".equalsIgnoreCase(s)) return false;
                }
                if (val.isNumber()) return val.asDouble() != 0.0;
            } catch (Throwable ignored) {}
        }

        if (v instanceof Number n) return n.doubleValue() != 0.0;

        return def;
    }

    static String asString(Object v, String def) {
        if (v == null) return def;

        if (v instanceof String s) return s;

        if (v instanceof Value val) {
            try {
                if (val.isString()) return val.asString();
                if (val.isNumber()) return String.valueOf(val.asDouble());
                if (val.isBoolean()) return String.valueOf(val.asBoolean());
            } catch (Throwable ignored) {}
        }

        return def;
    }
}