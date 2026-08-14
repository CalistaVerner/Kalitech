/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.effect.ParticleEmitter
 *  com.jme3.math.ColorRGBA
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector3f
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.particles;

import com.jme3.effect.ParticleEmitter;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class ParticleLuaConfig {
    private ParticleLuaConfig() {
    }

    public static LuaValueRef m(LuaValueRef v, String key) {
        if (v == null || v.isNull() || !v.hasMember(key)) {
            return null;
        }
        return v.getMember(key);
    }

    public static boolean has(LuaValueRef v, String key) {
        return v != null && !v.isNull() && v.hasMember(key);
    }

    public static String str(LuaValueRef v, String key, String def) {
        LuaValueRef x = ParticleLuaConfig.m(v, key);
        if (x == null || x.isNull()) {
            return def;
        }
        String s = x.asString();
        return s == null || s.isBlank() ? def : s;
    }

    public static int i(LuaValueRef v, String key, int def) {
        LuaValueRef x = ParticleLuaConfig.m(v, key);
        if (x == null || x.isNull()) {
            return def;
        }
        int n = (int)x.asDouble();
        return n > 0 ? n : def;
    }

    public static float f(LuaValueRef v, String key, float def) {
        LuaValueRef x = ParticleLuaConfig.m(v, key);
        if (x == null || x.isNull()) {
            return def;
        }
        float n = (float)x.asDouble();
        return Float.isFinite(n) ? n : def;
    }

    public static boolean b(LuaValueRef v, String key, boolean def) {
        LuaValueRef x = ParticleLuaConfig.m(v, key);
        if (x == null || x.isNull()) {
            return def;
        }
        return x.asBoolean();
    }

    public static float readFloat(LuaValueRef v, String key, float def) {
        if (v == null || v.isNull() || !v.hasMember(key)) {
            return def;
        }
        float n = (float)v.getMember(key).asDouble();
        return Float.isFinite(n) ? n : def;
    }

    public static boolean readBool(LuaValueRef v, String key, boolean def) {
        if (v == null || v.isNull() || !v.hasMember(key)) {
            return def;
        }
        return v.getMember(key).asBoolean();
    }

    public static int readIntClamped(LuaValueRef v, String key, int def, int min, int max) {
        if (v == null || v.isNull() || !v.hasMember(key)) {
            return def;
        }
        int n = (int)v.getMember(key).asDouble();
        if (n < min) {
            return min;
        }
        if (n > max) {
            return max;
        }
        return n;
    }

    public static float readFloatClamped(LuaValueRef v, String key, float def, float min, float max) {
        if (v == null || v.isNull() || !v.hasMember(key)) {
            return def;
        }
        float n = (float)v.getMember(key).asDouble();
        if (!Float.isFinite(n)) {
            return def;
        }
        if (n < min) {
            return min;
        }
        if (n > max) {
            return max;
        }
        return n;
    }

    public static void readVec3Into(LuaValueRef v, Vector3f out, Vector3f def) {
        if (out == null) {
            throw new IllegalArgumentException("out is required");
        }
        if (def == null) {
            def = Vector3f.ZERO;
        }
        float x = def.x;
        float y = def.y;
        float z = def.z;
        if (v != null && !v.isNull()) {
            if (v.hasMember("x")) {
                x = (float)v.getMember("x").asDouble();
            } else if (v.hasArrayElements() && v.getArraySize() > 0L) {
                x = (float)v.getArrayElement(0L).asDouble();
            }
            if (v.hasMember("y")) {
                y = (float)v.getMember("y").asDouble();
            } else if (v.hasArrayElements() && v.getArraySize() > 1L) {
                y = (float)v.getArrayElement(1L).asDouble();
            }
            if (v.hasMember("z")) {
                z = (float)v.getMember("z").asDouble();
            } else if (v.hasArrayElements() && v.getArraySize() > 2L) {
                z = (float)v.getArrayElement(2L).asDouble();
            }
        }
        if (!Float.isFinite(x)) {
            x = def.x;
        }
        if (!Float.isFinite(y)) {
            y = def.y;
        }
        if (!Float.isFinite(z)) {
            z = def.z;
        }
        out.set(x, y, z);
    }

    public static void readQuatInto(LuaValueRef v, Quaternion out, Quaternion def) {
        if (out == null) {
            throw new IllegalArgumentException("out is required");
        }
        if (def == null) {
            def = Quaternion.IDENTITY;
        }
        float x = def.getX();
        float y = def.getY();
        float z = def.getZ();
        float w = def.getW();
        if (v != null && !v.isNull()) {
            if (v.hasMember("x")) {
                x = (float)v.getMember("x").asDouble();
            }
            if (v.hasMember("y")) {
                y = (float)v.getMember("y").asDouble();
            }
            if (v.hasMember("z")) {
                z = (float)v.getMember("z").asDouble();
            }
            if (v.hasMember("w")) {
                w = (float)v.getMember("w").asDouble();
            }
        }
        if (!Float.isFinite(x)) {
            x = def.getX();
        }
        if (!Float.isFinite(y)) {
            y = def.getY();
        }
        if (!Float.isFinite(z)) {
            z = def.getZ();
        }
        if (!Float.isFinite(w)) {
            w = def.getW();
        }
        out.set(x, y, z, w);
    }

    public static void readColorInto(LuaValueRef v, ColorRGBA out, ColorRGBA def) {
        if (out == null) {
            throw new IllegalArgumentException("out is required");
        }
        if (def == null) {
            def = ColorRGBA.White;
        }
        float r = def.r;
        float g = def.g;
        float b = def.b;
        float a = def.a;
        if (v != null && !v.isNull()) {
            r = ParticleLuaConfig.readFloat(v, "r", r);
            g = ParticleLuaConfig.readFloat(v, "g", g);
            b = ParticleLuaConfig.readFloat(v, "b", b);
            a = ParticleLuaConfig.readFloat(v, "a", a);
        }
        if (!Float.isFinite(r)) {
            r = def.r;
        }
        if (!Float.isFinite(g)) {
            g = def.g;
        }
        if (!Float.isFinite(b)) {
            b = def.b;
        }
        if (!Float.isFinite(a)) {
            a = def.a;
        }
        out.set(r, g, b, a);
    }

    public static float scale(double s) {
        float fs = (float)s;
        return Float.isFinite(fs) && fs > 0.0f ? fs : 1.0f;
    }

    public static void applyEnabledIfPresent(ParticleEmitter em, LuaValueRef cfg) {
        if (cfg != null && !cfg.isNull() && cfg.hasMember("enabled")) {
            em.setEnabled(cfg.getMember("enabled").asBoolean());
        }
    }

    public static Vector3f vec3(LuaValueRef v, Vector3f def) {
        if (def == null) {
            def = Vector3f.ZERO;
        }
        Vector3f out = new Vector3f(def);
        ParticleLuaConfig.readVec3Into(v, out, def);
        return out;
    }

    public static Quaternion quat(LuaValueRef v, Quaternion def) {
        if (def == null) {
            def = Quaternion.IDENTITY;
        }
        Quaternion out = new Quaternion(def);
        ParticleLuaConfig.readQuatInto(v, out, def);
        return out;
    }

    public static ColorRGBA color(LuaValueRef v, ColorRGBA def) {
        if (def == null) {
            def = ColorRGBA.White;
        }
        ColorRGBA out = new ColorRGBA(def);
        ParticleLuaConfig.readColorInto(v, out, def);
        return out;
    }
}

