/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.ColorRGBA
 *  com.jme3.math.Vector2f
 *  com.jme3.math.Vector3f
 *  com.jme3.math.Vector4f
 *  com.jme3.texture.Texture$MagFilter
 *  com.jme3.texture.Texture$MinFilter
 *  com.jme3.texture.Texture$WrapMode
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.material;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.texture.Texture;
import org.foxesworld.kalitech.engine.modules.material.MaterialTypes;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

final class MaterialParsers {
    private static volatile boolean DEBUG = false;

    private MaterialParsers() {
    }

    static void setDebug(boolean enabled) {
        DEBUG = enabled;
    }

    static boolean isNull(LuaValueRef v) {
        return v == null || v.isNull();
    }

    static String safeType(LuaValueRef v) {
        if (v == null) {
            return "null";
        }
        try {
            if (v.isNull()) {
                return "null";
            }
            if (v.isBoolean()) {
                return "boolean";
            }
            if (v.isNumber()) {
                return "number";
            }
            if (v.isString()) {
                return "string";
            }
            if (v.hasArrayElements()) {
                return "array";
            }
            if (v.hasMembers()) {
                return "object";
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return "unknown";
    }

    static MaterialTypes.ParsedTex parseTextureShorthand(String s) {
        if (s == null) {
            return new MaterialTypes.ParsedTex(null, null);
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return new MaterialTypes.ParsedTex(null, null);
        }
        String[] parts = t.split("\\|");
        String path = parts[0].trim();
        Texture.WrapMode wrap = null;
        if (parts.length >= 2) {
            wrap = MaterialParsers.parseWrap(parts[1].trim());
        }
        return new MaterialTypes.ParsedTex(path, wrap);
    }

    static Texture.WrapMode parseWrap(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String w = s.trim().toLowerCase();
        if (w.equals("repeat") || w.equals("tile") || w.equals("tiled")) {
            return Texture.WrapMode.Repeat;
        }
        if (w.equals("clamp") || w.equals("edge") || w.equals("edgeclamp") || w.equals("edge_clamp") || w.equals("clamp_to_edge")) {
            return Texture.WrapMode.EdgeClamp;
        }
        if (w.equals("mirror") || w.equals("mirrored") || w.equals("mirroredrepeat") || w.equals("mirrored_repeat")) {
            return Texture.WrapMode.MirroredRepeat;
        }
        for (Texture.WrapMode wm : Texture.WrapMode.values()) {
            if (!wm.name().equalsIgnoreCase(s.trim())) continue;
            return wm;
        }
        return null;
    }

    static Texture.MinFilter parseMinFilter(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        for (Texture.MinFilter f : Texture.MinFilter.values()) {
            if (!f.name().equalsIgnoreCase(t)) continue;
            return f;
        }
        String k = t.toLowerCase();
        if (k.equals("nearest")) {
            return Texture.MinFilter.NearestNoMipMaps;
        }
        if (k.equals("bilinear")) {
            return Texture.MinFilter.BilinearNoMipMaps;
        }
        if (k.equals("trilinear")) {
            return Texture.MinFilter.Trilinear;
        }
        return null;
    }

    static Texture.MagFilter parseMagFilter(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        for (Texture.MagFilter f : Texture.MagFilter.values()) {
            if (!f.name().equalsIgnoreCase(t)) continue;
            return f;
        }
        String k = t.toLowerCase();
        if (k.equals("nearest")) {
            return Texture.MagFilter.Nearest;
        }
        if (k.equals("bilinear") || k.equals("linear")) {
            return Texture.MagFilter.Bilinear;
        }
        return null;
    }

    static MaterialTypes.TextureDesc parseTextureDesc(LuaValueRef v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isString()) {
            MaterialTypes.ParsedTex pt = MaterialParsers.parseTextureShorthand(v.asString());
            if (pt.path() == null || pt.path().isBlank()) {
                return null;
            }
            return new MaterialTypes.TextureDesc(pt.path(), pt.wrap(), null, null, 0, null);
        }
        if (v.hasMembers() && v.hasMember("texture")) {
            String magS;
            String minS;
            String path = LuaCfg.str((LuaValueRef)v, (String)"texture", null);
            if (path == null || path.isBlank()) {
                return null;
            }
            String wrapS = LuaCfg.str((LuaValueRef)v, (String)"wrap", null);
            if (wrapS == null) {
                wrapS = LuaCfg.str((LuaValueRef)v, (String)"type", null);
            }
            if ((minS = LuaCfg.str((LuaValueRef)v, (String)"min", null)) == null) {
                minS = LuaCfg.str((LuaValueRef)v, (String)"minFilter", null);
            }
            if ((magS = LuaCfg.str((LuaValueRef)v, (String)"mag", null)) == null) {
                magS = LuaCfg.str((LuaValueRef)v, (String)"magFilter", null);
            }
            int aniso = 0;
            if (v.hasMember("anisotropy")) {
                try {
                    LuaValueRef a = v.getMember("anisotropy");
                    if (a != null && !a.isNull() && a.isNumber()) {
                        aniso = Math.max(0, a.asInt());
                    }
                }
                catch (Throwable a) {
                    // empty catch block
                }
            }
            MaterialTypes.TileWorld tw = null;
            if (v.hasMember("tileWorld")) {
                try {
                    LuaValueRef t = v.getMember("tileWorld");
                    if (t != null && !t.isNull() && t.hasMembers()) {
                        float x = (float)LuaCfg.num((LuaValueRef)t, (String)"x", (double)0.0);
                        float z = (float)LuaCfg.num((LuaValueRef)t, (String)"z", (double)0.0);
                        if (x > 0.0f && z > 0.0f) {
                            tw = new MaterialTypes.TileWorld(x, z);
                        }
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
            return new MaterialTypes.TextureDesc(path.trim(), MaterialParsers.parseWrap(wrapS), MaterialParsers.parseMinFilter(minS), MaterialParsers.parseMagFilter(magS), aniso, tw);
        }
        return null;
    }

    static ColorRGBA parseColor(LuaValueRef v) {
        long n;
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.hasArrayElements() && (n = v.getArraySize()) >= 3L) {
            float r = (float)v.getArrayElement(0L).asDouble();
            float g = (float)v.getArrayElement(1L).asDouble();
            float b = (float)v.getArrayElement(2L).asDouble();
            float a = n >= 4L ? (float)v.getArrayElement(3L).asDouble() : 1.0f;
            return new ColorRGBA(r, g, b, a);
        }
        if (v.hasMembers() && (v.hasMember("r") || v.hasMember("g") || v.hasMember("b"))) {
            float r = (float)LuaCfg.num((LuaValueRef)v, (String)"r", (double)1.0);
            float g = (float)LuaCfg.num((LuaValueRef)v, (String)"g", (double)1.0);
            float b = (float)LuaCfg.num((LuaValueRef)v, (String)"b", (double)1.0);
            float a = (float)LuaCfg.num((LuaValueRef)v, (String)"a", (double)1.0);
            return new ColorRGBA(r, g, b, a);
        }
        return null;
    }

    static Vector2f parseVec2(LuaValueRef v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.hasArrayElements() && v.getArraySize() >= 2L) {
            return new Vector2f((float)v.getArrayElement(0L).asDouble(), (float)v.getArrayElement(1L).asDouble());
        }
        if (v.hasMembers() && (v.hasMember("x") || v.hasMember("y"))) {
            return new Vector2f((float)LuaCfg.num((LuaValueRef)v, (String)"x", (double)0.0), (float)LuaCfg.num((LuaValueRef)v, (String)"y", (double)0.0));
        }
        return null;
    }

    static Vector3f parseVec3(LuaValueRef v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.hasArrayElements() && v.getArraySize() >= 3L) {
            return new Vector3f((float)v.getArrayElement(0L).asDouble(), (float)v.getArrayElement(1L).asDouble(), (float)v.getArrayElement(2L).asDouble());
        }
        if (v.hasMembers() && (v.hasMember("x") || v.hasMember("y") || v.hasMember("z"))) {
            return new Vector3f((float)LuaCfg.num((LuaValueRef)v, (String)"x", (double)0.0), (float)LuaCfg.num((LuaValueRef)v, (String)"y", (double)0.0), (float)LuaCfg.num((LuaValueRef)v, (String)"z", (double)0.0));
        }
        return null;
    }

    static Vector4f parseVec4(LuaValueRef v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.hasArrayElements() && v.getArraySize() >= 4L) {
            return new Vector4f((float)v.getArrayElement(0L).asDouble(), (float)v.getArrayElement(1L).asDouble(), (float)v.getArrayElement(2L).asDouble(), (float)v.getArrayElement(3L).asDouble());
        }
        if (v.hasMembers() && (v.hasMember("x") || v.hasMember("y") || v.hasMember("z") || v.hasMember("w"))) {
            return new Vector4f((float)LuaCfg.num((LuaValueRef)v, (String)"x", (double)0.0), (float)LuaCfg.num((LuaValueRef)v, (String)"y", (double)0.0), (float)LuaCfg.num((LuaValueRef)v, (String)"z", (double)0.0), (float)LuaCfg.num((LuaValueRef)v, (String)"w", (double)1.0));
        }
        return null;
    }
}

