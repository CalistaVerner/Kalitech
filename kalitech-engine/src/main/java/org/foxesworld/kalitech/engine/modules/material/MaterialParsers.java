// FILE: org/foxesworld/kalitech/engine/modules/material/MaterialParsers.java
package org.foxesworld.kalitech.engine.modules.material;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.texture.Texture;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.num;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.str;

final class MaterialParsers {

    private static volatile boolean DEBUG = false;

    private MaterialParsers() {
    }

    static void setDebug(boolean enabled) {
        DEBUG = enabled;
    }

    static boolean isNull(Value v) {
        return v == null || v.isNull();
    }

    static String safeType(Value v) {
        if (v == null) return "null";
        try {
            if (v.isNull()) return "null";
            if (v.isBoolean()) return "boolean";
            if (v.isNumber()) return "number";
            if (v.isString()) return "string";
            if (v.hasArrayElements()) return "array";
            if (v.hasMembers()) return "object";
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    static MaterialTypes.ParsedTex parseTextureShorthand(String s) {
        if (s == null) return new MaterialTypes.ParsedTex(null, null);
        String t = s.trim();
        if (t.isEmpty()) return new MaterialTypes.ParsedTex(null, null);

        String[] parts = t.split("\\|");
        String path = parts[0].trim();
        Texture.WrapMode wrap = null;
        if (parts.length >= 2) wrap = parseWrap(parts[1].trim());
        return new MaterialTypes.ParsedTex(path, wrap);
    }

    static Texture.WrapMode parseWrap(String s) {
        if (s == null || s.isBlank()) return null;
        String w = s.trim().toLowerCase();

        if (w.equals("repeat") || w.equals("tile") || w.equals("tiled")) return Texture.WrapMode.Repeat;
        if (w.equals("clamp") || w.equals("edge") || w.equals("edgeclamp") || w.equals("edge_clamp") || w.equals("clamp_to_edge"))
            return Texture.WrapMode.EdgeClamp;
        if (w.equals("mirror") || w.equals("mirrored") || w.equals("mirroredrepeat") || w.equals("mirrored_repeat"))
            return Texture.WrapMode.MirroredRepeat;

        for (Texture.WrapMode wm : Texture.WrapMode.values()) {
            if (wm.name().equalsIgnoreCase(s.trim())) return wm;
        }
        return null;
    }

    static Texture.MinFilter parseMinFilter(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();

        for (Texture.MinFilter f : Texture.MinFilter.values()) {
            if (f.name().equalsIgnoreCase(t)) return f;
        }

        String k = t.toLowerCase();
        if (k.equals("nearest")) return Texture.MinFilter.NearestNoMipMaps;
        if (k.equals("bilinear")) return Texture.MinFilter.BilinearNoMipMaps;
        if (k.equals("trilinear")) return Texture.MinFilter.Trilinear;
        return null;
    }

    static Texture.MagFilter parseMagFilter(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();

        for (Texture.MagFilter f : Texture.MagFilter.values()) {
            if (f.name().equalsIgnoreCase(t)) return f;
        }

        String k = t.toLowerCase();
        if (k.equals("nearest")) return Texture.MagFilter.Nearest;
        if (k.equals("bilinear") || k.equals("linear")) return Texture.MagFilter.Bilinear;
        return null;
    }

    static MaterialTypes.TextureDesc parseTextureDesc(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.isString()) {
            MaterialTypes.ParsedTex pt = parseTextureShorthand(v.asString());
            if (pt.path() == null || pt.path().isBlank()) return null;
            return new MaterialTypes.TextureDesc(pt.path(), pt.wrap(), null, null, 0, null);
        }

        if (v.hasMembers() && v.hasMember("texture")) {
            String path = str(v, "texture", null);
            if (path == null || path.isBlank()) return null;

            String wrapS = str(v, "wrap", null);
            if (wrapS == null) wrapS = str(v, "type", null);

            String minS = str(v, "min", null);
            if (minS == null) minS = str(v, "minFilter", null);

            String magS = str(v, "mag", null);
            if (magS == null) magS = str(v, "magFilter", null);

            int aniso = 0;
            if (v.hasMember("anisotropy")) {
                try {
                    Value a = v.getMember("anisotropy");
                    if (a != null && !a.isNull() && a.isNumber()) aniso = Math.max(0, a.asInt());
                } catch (Throwable ignored) {
                }
            }

            MaterialTypes.TileWorld tw = null;
            if (v.hasMember("tileWorld")) {
                try {
                    Value t = v.getMember("tileWorld");
                    if (t != null && !t.isNull() && t.hasMembers()) {
                        float x = (float) num(t, "x", 0.0);
                        float z = (float) num(t, "z", 0.0);
                        if (x > 0f && z > 0f) tw = new MaterialTypes.TileWorld(x, z);
                    }
                } catch (Throwable ignored) {
                }
            }

            return new MaterialTypes.TextureDesc(
                    path.trim(),
                    parseWrap(wrapS),
                    parseMinFilter(minS),
                    parseMagFilter(magS),
                    aniso,
                    tw
            );
        }

        return null;
    }

    static ColorRGBA parseColor(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.hasArrayElements()) {
            long n = v.getArraySize();
            if (n >= 3) {
                float r = (float) v.getArrayElement(0).asDouble();
                float g = (float) v.getArrayElement(1).asDouble();
                float b = (float) v.getArrayElement(2).asDouble();
                float a = (n >= 4) ? (float) v.getArrayElement(3).asDouble() : 1f;
                return new ColorRGBA(r, g, b, a);
            }
        }

        if (v.hasMembers() && (v.hasMember("r") || v.hasMember("g") || v.hasMember("b"))) {
            float r = (float) num(v, "r", 1.0);
            float g = (float) num(v, "g", 1.0);
            float b = (float) num(v, "b", 1.0);
            float a = (float) num(v, "a", 1.0);
            return new ColorRGBA(r, g, b, a);
        }

        return null;
    }

    static Vector2f parseVec2(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.hasArrayElements() && v.getArraySize() >= 2) {
            return new Vector2f(
                    (float) v.getArrayElement(0).asDouble(),
                    (float) v.getArrayElement(1).asDouble()
            );
        }

        if (v.hasMembers() && (v.hasMember("x") || v.hasMember("y"))) {
            return new Vector2f((float) num(v, "x", 0.0), (float) num(v, "y", 0.0));
        }

        return null;
    }

    static Vector3f parseVec3(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.hasArrayElements() && v.getArraySize() >= 3) {
            return new Vector3f(
                    (float) v.getArrayElement(0).asDouble(),
                    (float) v.getArrayElement(1).asDouble(),
                    (float) v.getArrayElement(2).asDouble()
            );
        }

        if (v.hasMembers() && (v.hasMember("x") || v.hasMember("y") || v.hasMember("z"))) {
            return new Vector3f(
                    (float) num(v, "x", 0.0),
                    (float) num(v, "y", 0.0),
                    (float) num(v, "z", 0.0)
            );
        }

        return null;
    }

    static Vector4f parseVec4(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.hasArrayElements() && v.getArraySize() >= 4) {
            return new Vector4f(
                    (float) v.getArrayElement(0).asDouble(),
                    (float) v.getArrayElement(1).asDouble(),
                    (float) v.getArrayElement(2).asDouble(),
                    (float) v.getArrayElement(3).asDouble()
            );
        }

        if (v.hasMembers() && (v.hasMember("x") || v.hasMember("y") || v.hasMember("z") || v.hasMember("w"))) {
            return new Vector4f(
                    (float) num(v, "x", 0.0),
                    (float) num(v, "y", 0.0),
                    (float) num(v, "z", 0.0),
                    (float) num(v, "w", 1.0)
            );
        }

        return null;
    }
}