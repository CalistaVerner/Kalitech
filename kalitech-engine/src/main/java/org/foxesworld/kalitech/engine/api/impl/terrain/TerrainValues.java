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

    /**
     * Reads JS Array / TypedArray / ArrayLike into float[].
     *
     * Supported:
     *  - JS Array [1,2,3]
     *  - Float32Array / Float64Array / Int32Array / etc
     *  - Any object with hasArrayElements()
     *
     * @throws IllegalArgumentException if value is not array-like
     */
    public static float[] readFloatArray(Value v) {
        if (v == null || v.isNull()) {
            throw new IllegalArgumentException("readFloatArray: value is null");
        }

        if (!v.hasArrayElements()) {
            throw new IllegalArgumentException(
                    "readFloatArray: value is not array-like: " + v
            );
        }

        final int len = (int) v.getArraySize();
        final float[] out = new float[len];

        for (int i = 0; i < len; i++) {
            Value e = v.getArrayElement(i);

            if (e == null || e.isNull()) {
                out[i] = 0f;
                continue;
            }

            // Fast path: number
            if (e.isNumber()) {
                out[i] = (float) e.asDouble();
                continue;
            }

            // valueOf() fallback (rare, but real with Graal wrappers)
            if (e.hasMember("valueOf")) {
                try {
                    Value vo = e.invokeMember("valueOf");
                    if (vo != null && vo.isNumber()) {
                        out[i] = (float) vo.asDouble();
                        continue;
                    }
                } catch (Throwable ignored) {
                }
            }

            throw new IllegalArgumentException(
                    "readFloatArray: element[" + i + "] is not numeric: " + e
            );
        }

        return out;
    }
}