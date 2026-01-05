package org.foxesworld.kalitech.engine.script.util;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Array;

public final class JsCfg {
    private JsCfg() {
    }

    // ---------- Basic ----------

    public static boolean has(Value v) {
        return v != null && !v.isNull();
    }

    public static Value member(Value v, String key) {
        return (has(v) && v.hasMember(key)) ? v.getMember(key) : null;
    }

    public static String str(Value cfg, String key, String def) {
        Value m = member(cfg, key);
        if (m == null || m.isNull()) return def;
        try {
            return m.asString();
        } catch (Exception ignored) {
            return def;
        }
    }

    public static boolean bool(Value cfg, String key, boolean def) {
        Value m = member(cfg, key);
        if (m == null || m.isNull()) return def;
        try {
            return m.asBoolean();
        } catch (Exception ignored) {
            return def;
        }
    }

    public static double num(Value cfg, String key, double def) {
        Value m = member(cfg, key);
        if (m == null || m.isNull()) return def;
        try {
            return m.asDouble();
        } catch (Exception ignored) {
            return def;
        }
    }

    public static int intR(Value cfg, String key, int def) {
        return (int) Math.round(num(cfg, key, def));
    }

    public static int intClampR(Value cfg, String key, int def, int lo, int hi) {
        return clamp((int) Math.round(num(cfg, key, def)), lo, hi);
    }

    public static double numClamp(Value cfg, String key, double def, double lo, double hi) {
        return clamp(num(cfg, key, def), lo, hi);
    }

    // ---------- Vec2 / Vec3 ----------
    // accepts [x,y] or {x,y} or {0:...,1:...} in arrays

    public static Vector2f vec2(Value v, Vector2f def) {
        if (!has(v)) return def;
        try {
            if (v.hasArrayElements() && v.getArraySize() >= 2) {
                return new Vector2f((float) v.getArrayElement(0).asDouble(), (float) v.getArrayElement(1).asDouble());
            }
            if (v.hasMember("x") && v.hasMember("y")) {
                return new Vector2f((float) v.getMember("x").asDouble(), (float) v.getMember("y").asDouble());
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    public static int i32(Value cfg, String key, int def) {
        if (cfg == null || cfg.isNull() || !cfg.hasMember(key)) return def;
        Value v = cfg.getMember(key);
        if (v == null || v.isNull()) return def;
        try {
            return v.asInt();
        } catch (Exception ignored) {
            return def;
        }
    }

    public static double f64(Value cfg, String key, double def) {
        if (cfg == null || cfg.isNull() || !cfg.hasMember(key)) return def;
        Value v = cfg.getMember(key);
        if (v == null || v.isNull()) return def;
        try {
            return v.asDouble();
        } catch (Exception ignored) {
            return def;
        }
    }

    public static boolean has(Value v, String k) {
        Value m = member(v, k);
        return m != null && !m.isNull();
    }

    public static int clampInt(double v, int a, int b) {
        int x = (int) Math.round(v);
        return Math.max(a, Math.min(b, x));
    }

    public static Vector3f vec3(Value v, Vector3f def) {
        if (!has(v)) return def;
        try {
            if (v.hasArrayElements() && v.getArraySize() >= 3) {
                return new Vector3f(
                        (float) v.getArrayElement(0).asDouble(),
                        (float) v.getArrayElement(1).asDouble(),
                        (float) v.getArrayElement(2).asDouble()
                );
            }
            if (v.hasMember("x") && v.hasMember("y") && v.hasMember("z")) {
                return new Vector3f(
                        (float) v.getMember("x").asDouble(),
                        (float) v.getMember("y").asDouble(),
                        (float) v.getMember("z").asDouble()
                );
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    // ---------- Color ----------
    // accepts {r,g,b,a?} OR {color:{r,g,b,a?}} OR [r,g,b,a?]
    public static ColorRGBA color(Value cfg, String key, ColorRGBA def) {
        Value v = member(cfg, key);
        if (v == null) v = member(cfg, "color"); // common alias
        if (!has(v)) return def;

        try {
            if (v.hasArrayElements()) {
                float r = (float) v.getArrayElement(0).asDouble();
                float g = (float) v.getArrayElement(1).asDouble();
                float b = (float) v.getArrayElement(2).asDouble();
                float a = (v.getArraySize() >= 4) ? (float) v.getArrayElement(3).asDouble() : 1f;
                return new ColorRGBA(r, g, b, a);
            }
            if (v.hasMember("r") && v.hasMember("g") && v.hasMember("b")) {
                float r = (float) v.getMember("r").asDouble();
                float g = (float) v.getMember("g").asDouble();
                float b = (float) v.getMember("b").asDouble();
                float a = v.hasMember("a") ? (float) v.getMember("a").asDouble() : 1f;
                return new ColorRGBA(r, g, b, a);
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    /**
     * Reads JS Array / TypedArray / ArrayLike into float[].
     * <p>
     * Supported:
     * - JS Array [1,2,3]
     * - Float32Array / Float64Array / Int32Array / etc
     * - Any object with hasArrayElements()
     *
     * @throws IllegalArgumentException if value is not array-like
     */
    public static float[] readFloatArray(Value v) {
        if (v == null || v.isNull()) {
            throw new IllegalArgumentException("readFloatArray: value is null");
        }

        // 1) HostObject primitive arrays: float[], double[], int[], Number[] etc
        if (v.isHostObject()) {
            Object o = v.asHostObject();
            if (o == null) throw new IllegalArgumentException("readFloatArray: host object is null");

            if (o instanceof float[] a) {
                return a; // можно copyOf(a,a.length) если хочешь защититься от мутаций
            }
            if (o instanceof double[] a) {
                float[] out = new float[a.length];
                for (int i = 0; i < a.length; i++) out[i] = (float) a[i];
                return out;
            }
            if (o.getClass().isArray()) {
                int len = Array.getLength(o);
                float[] out = new float[len];
                for (int i = 0; i < len; i++) {
                    Object el = Array.get(o, i);
                    out[i] = (el instanceof Number n) ? n.floatValue() : 0f;
                }
                return out;
            }

            throw new IllegalArgumentException("readFloatArray: unsupported host object: " + o.getClass());
        }

        // 2) JS Array / TypedArray / Polyglot arrays
        if (v.hasArrayElements()) {
            long sz = v.getArraySize();
            if (sz > Integer.MAX_VALUE) throw new IllegalArgumentException("readFloatArray: too large: " + sz);

            int len = (int) sz;
            float[] out = new float[len];

            for (int i = 0; i < len; i++) {
                Value e = v.getArrayElement(i);
                if (e == null || e.isNull()) {
                    out[i] = 0f;
                    continue;
                }

                if (e.isNumber()) {
                    out[i] = (float) e.asDouble();
                    continue;
                }

                // valueOf fallback
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

                out[i] = 0f; // не падаем: JS должен быть простым
            }

            return out;
        }

        throw new IllegalArgumentException("readFloatArray: value is not array-like/host-array: " + v);
    }

    // ---------- Clamp ----------
    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}