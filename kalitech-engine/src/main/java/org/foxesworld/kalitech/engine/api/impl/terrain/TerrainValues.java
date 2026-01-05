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

        // 1) JS Array / TypedArray / ArrayLike
        if (v.hasArrayElements()) {
            final int len = (int) v.getArraySize();
            final float[] out = new float[len];
            for (int i = 0; i < len; i++) {
                Value e = v.getArrayElement(i);
                if (e == null || e.isNull()) { out[i] = 0f; continue; }
                if (e.isNumber()) { out[i] = (float) e.asDouble(); continue; }

                // valueOf() fallback for wrapped numerics
                if (e.hasMember("valueOf")) {
                    try {
                        Value vo = e.invokeMember("valueOf");
                        if (vo != null && vo.isNumber()) {
                            out[i] = (float) vo.asDouble();
                            continue;
                        }
                    } catch (Throwable ignored) {}
                }

                throw new IllegalArgumentException("readFloatArray: element[" + i + "] is not numeric: " + e);
            }
            return out;
        }

        // 2) HostObject float[] / double[] (your error case: (language: Java, type: float[]))
        if (v.isHostObject()) {
            Object host = v.asHostObject();

            if (host instanceof float[]) {
                float[] a = (float[]) host;
                // IMPORTANT: return copy to avoid accidental shared mutation through JS references
                float[] out = new float[a.length];
                System.arraycopy(a, 0, out, 0, a.length);
                return out;
            }
            if (host instanceof double[]) {
                double[] a = (double[]) host;
                float[] out = new float[a.length];
                for (int i = 0; i < a.length; i++) out[i] = (float) a[i];
                return out;
            }
            if (host instanceof int[]) {
                int[] a = (int[]) host;
                float[] out = new float[a.length];
                for (int i = 0; i < a.length; i++) out[i] = (float) a[i];
                return out;
            }
        }

        throw new IllegalArgumentException("readFloatArray: value is not array-like or host float[]: " + v);
    }
}