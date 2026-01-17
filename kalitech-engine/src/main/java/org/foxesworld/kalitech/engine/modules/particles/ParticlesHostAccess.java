// FILE: org/foxesworld/kalitech/engine/modules/particles/ParticlesHostAccess.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.graalvm.polyglot.Value;

/**
 * HostAccess helpers for deterministic reading of JS Values.
 * <p>
 * AAA goals:
 * <ul>
 *   <li>NaN/Infinity-safe parsing</li>
 *   <li>Fast-path parsers without heap allocations for hot setters</li>
 *   <li>Backward-compatible API (old helpers preserved)</li>
 * </ul>
 */
public final class ParticlesHostAccess {

    private ParticlesHostAccess() {
    }

    public static Value m(Value v, String key) {
        if (v == null || v.isNull() || !v.hasMember(key)) return null;
        return v.getMember(key);
    }

    public static boolean has(Value v, String key) {
        return v != null && !v.isNull() && v.hasMember(key);
    }

    public static String str(Value v, String key, String def) {
        Value x = m(v, key);
        if (x == null || x.isNull()) return def;
        String s = x.asString();
        return (s == null || s.isBlank()) ? def : s;
    }

    public static int i(Value v, String key, int def) {
        Value x = m(v, key);
        if (x == null || x.isNull()) return def;
        int n = (int) x.asDouble();
        return n > 0 ? n : def;
    }

    public static float f(Value v, String key, float def) {
        Value x = m(v, key);
        if (x == null || x.isNull()) return def;
        float n = (float) x.asDouble();
        return Float.isFinite(n) ? n : def;
    }

    public static boolean b(Value v, String key, boolean def) {
        Value x = m(v, key);
        if (x == null || x.isNull()) return def;
        return x.asBoolean();
    }

    // ---------------------------------------------------------------------
    // Fast parsing (no allocations)
    // ---------------------------------------------------------------------

    public static float readFloat(Value v, String key, float def) {
        if (v == null || v.isNull() || !v.hasMember(key)) return def;
        float n = (float) v.getMember(key).asDouble();
        return Float.isFinite(n) ? n : def;
    }

    public static boolean readBool(Value v, String key, boolean def) {
        if (v == null || v.isNull() || !v.hasMember(key)) return def;
        return v.getMember(key).asBoolean();
    }

    public static int readIntClamped(Value v, String key, int def, int min, int max) {
        if (v == null || v.isNull() || !v.hasMember(key)) return def;
        int n = (int) v.getMember(key).asDouble();
        if (n < min) return min;
        if (n > max) return max;
        return n;
    }

    public static float readFloatClamped(Value v, String key, float def, float min, float max) {
        if (v == null || v.isNull() || !v.hasMember(key)) return def;
        float n = (float) v.getMember(key).asDouble();
        if (!Float.isFinite(n)) return def;
        if (n < min) return min;
        if (n > max) return max;
        return n;
    }

    /**
     * Reads vec3 from object {x,y,z} or array [x,y,z] into provided output.
     * Does not allocate.
     */
    public static void readVec3Into(Value v, Vector3f out, Vector3f def) {
        if (out == null) throw new IllegalArgumentException("out is required");
        if (def == null) def = Vector3f.ZERO;

        float x = def.x, y = def.y, z = def.z;
        if (v != null && !v.isNull()) {
            if (v.hasMember("x")) x = (float) v.getMember("x").asDouble();
            else if (v.hasArrayElements() && v.getArraySize() > 0) x = (float) v.getArrayElement(0).asDouble();

            if (v.hasMember("y")) y = (float) v.getMember("y").asDouble();
            else if (v.hasArrayElements() && v.getArraySize() > 1) y = (float) v.getArrayElement(1).asDouble();

            if (v.hasMember("z")) z = (float) v.getMember("z").asDouble();
            else if (v.hasArrayElements() && v.getArraySize() > 2) z = (float) v.getArrayElement(2).asDouble();
        }

        if (!Float.isFinite(x)) x = def.x;
        if (!Float.isFinite(y)) y = def.y;
        if (!Float.isFinite(z)) z = def.z;

        out.set(x, y, z);
    }

    /**
     * Reads quaternion {x,y,z,w} into provided output.
     * Does not allocate.
     */
    public static void readQuatInto(Value v, Quaternion out, Quaternion def) {
        if (out == null) throw new IllegalArgumentException("out is required");
        if (def == null) def = Quaternion.IDENTITY;

        float x = def.getX();
        float y = def.getY();
        float z = def.getZ();
        float w = def.getW();

        if (v != null && !v.isNull()) {
            if (v.hasMember("x")) x = (float) v.getMember("x").asDouble();
            if (v.hasMember("y")) y = (float) v.getMember("y").asDouble();
            if (v.hasMember("z")) z = (float) v.getMember("z").asDouble();
            if (v.hasMember("w")) w = (float) v.getMember("w").asDouble();
        }

        if (!Float.isFinite(x)) x = def.getX();
        if (!Float.isFinite(y)) y = def.getY();
        if (!Float.isFinite(z)) z = def.getZ();
        if (!Float.isFinite(w)) w = def.getW();

        out.set(x, y, z, w);
    }

    public static void readColorInto(Value v, ColorRGBA out, ColorRGBA def) {
        if (out == null) throw new IllegalArgumentException("out is required");
        if (def == null) def = ColorRGBA.White;

        float r = def.r, g = def.g, b = def.b, a = def.a;

        if (v != null && !v.isNull()) {
            r = readFloat(v, "r", r);
            g = readFloat(v, "g", g);
            b = readFloat(v, "b", b);
            a = readFloat(v, "a", a);
        }

        if (!Float.isFinite(r)) r = def.r;
        if (!Float.isFinite(g)) g = def.g;
        if (!Float.isFinite(b)) b = def.b;
        if (!Float.isFinite(a)) a = def.a;

        out.set(r, g, b, a);
    }

    public static float scale(double s) {
        float fs = (float) s;
        return (Float.isFinite(fs) && fs > 0f) ? fs : 1f;
    }

    public static void applyEnabledIfPresent(com.jme3.effect.ParticleEmitter em, Value cfg) {
        if (cfg != null && !cfg.isNull() && cfg.hasMember("enabled")) {
            em.setEnabled(cfg.getMember("enabled").asBoolean());
        }
    }

    // ---------------------------------------------------------------------
    // Backward-compatible alloc helpers (kept)
    // ---------------------------------------------------------------------

    public static Vector3f vec3(Value v, Vector3f def) {
        if (def == null) def = Vector3f.ZERO;
        Vector3f out = new Vector3f(def);
        readVec3Into(v, out, def);
        return out;
    }

    public static Quaternion quat(Value v, Quaternion def) {
        if (def == null) def = Quaternion.IDENTITY;
        Quaternion out = new Quaternion(def);
        readQuatInto(v, out, def);
        return out;
    }

    public static ColorRGBA color(Value v, ColorRGBA def) {
        if (def == null) def = ColorRGBA.White;
        ColorRGBA out = new ColorRGBA(def);
        readColorInto(v, out, def);
        return out;
    }
}