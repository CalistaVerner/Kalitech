package org.foxesworld.kalitech.engine.script.util;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import org.graalvm.polyglot.Value;

public final class JsCfg {
    private JsCfg() {}

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
        try { return m.asString(); } catch (Exception ignored) { return def; }
    }

    public static boolean bool(Value cfg, String key, boolean def) {
        Value m = member(cfg, key);
        if (m == null || m.isNull()) return def;
        try { return m.asBoolean(); } catch (Exception ignored) { return def; }
    }

    public static double num(Value cfg, String key, double def) {
        Value m = member(cfg, key);
        if (m == null || m.isNull()) return def;
        try { return m.asDouble(); } catch (Exception ignored) { return def; }
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
        } catch (Exception ignored) {}
        return def;
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
        } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
        return def;
    }

    // ---------- Clamp ----------
    public static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    public static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}