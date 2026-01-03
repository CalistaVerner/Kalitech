package org.foxesworld.kalitech.engine.api.impl.terrain;

import org.graalvm.polyglot.Value;

public final class TerrainValues {
    private TerrainValues() {}

    public static Value member(Value v, String k) {
        return (v != null && !v.isNull() && v.hasMember(k)) ? v.getMember(k) : null;
    }

    public static boolean has(Value v, String k) {
        Value m = member(v, k);
        return m != null && !m.isNull();
    }

    public static String str(Value v, String k, String def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asString();
        } catch (Throwable ignored) {
            return def;
        }
    }

    public static boolean bool(Value v, String k, boolean def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asBoolean();
        } catch (Throwable ignored) {
            return def;
        }
    }

    public static double num(Value v, String k, double def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asDouble();
        } catch (Throwable ignored) {
            return def;
        }
    }

    public static int clampInt(double v, int a, int b) {
        int x = (int) Math.round(v);
        return Math.max(a, Math.min(b, x));
    }

    public static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }
}