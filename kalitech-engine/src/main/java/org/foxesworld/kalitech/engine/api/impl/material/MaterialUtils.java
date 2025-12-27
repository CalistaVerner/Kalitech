package org.foxesworld.kalitech.engine.api.impl.material;

import com.jme3.asset.AssetManager;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.texture.Texture;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.*;

public final class MaterialUtils {
    private MaterialUtils() {}

    public static Material unwrapMaterial(Object h) {
        if (h == null) return null;

        if (h instanceof Material m) return m;

        if (h instanceof MaterialApiImpl.MaterialHandle mh) return mh.__material();

        if (h instanceof Value v && v.isHostObject()) {
            Object host = v.asHostObject();
            if (host instanceof MaterialApiImpl.MaterialHandle mh) return mh.__material();
            if (host instanceof Material m) return m;
        }

        return null;
    }

    public record TileWorld(float x, float z) {}

    public record TextureDesc(
            String texture,
            Texture.WrapMode wrap,
            Texture.MinFilter minFilter,
            Texture.MagFilter magFilter,
            int anisotropy,
            TileWorld tileWorld
    ) {}

    public record ParsedTex(String path, Texture.WrapMode wrap) {}

    public static Texture.WrapMode parseWrap(String s) {
        if (s == null || s.isBlank()) return null;

        String w = s.trim().toLowerCase();

        if (w.equals("repeat") || w.equals("reppeat") || w.equals("tile") || w.equals("tiled"))
            return Texture.WrapMode.Repeat;

        if (w.equals("clamp") || w.equals("edge") || w.equals("edgeclamp") || w.equals("edge_clamp") || w.equals("clamp_to_edge"))
            return Texture.WrapMode.EdgeClamp;

        if (w.equals("mirror") || w.equals("mirrored") || w.equals("mirroredrepeat") || w.equals("mirrored_repeat"))
            return Texture.WrapMode.MirroredRepeat;

        for (Texture.WrapMode wm : Texture.WrapMode.values()) {
            if (wm.name().equalsIgnoreCase(s.trim())) return wm;
        }
        return null;
    }

    public static Texture.MinFilter parseMinFilter(String s) {
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

    public static Texture.MagFilter parseMagFilter(String s) {
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

    public static ParsedTex parseTextureShorthand(String s) {
        if (s == null) return new ParsedTex(null, null);

        String t = s.trim();
        if (t.isEmpty()) return new ParsedTex(null, null);

        String[] parts = t.split("\\|");
        String path = parts[0].trim();
        Texture.WrapMode wrap = null;
        if (parts.length >= 2) wrap = parseWrap(parts[1].trim());

        return new ParsedTex(path, wrap);
    }

    public static TextureDesc parseTextureDesc(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.isString()) {
            ParsedTex pt = parseTextureShorthand(v.asString());
            if (pt.path() == null || pt.path().isBlank()) return null;
            return new TextureDesc(pt.path(), pt.wrap(), null, null, 0, null);
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
                } catch (Throwable ignored) {}
            }

            TileWorld tw = null;
            if (v.hasMember("tileWorld")) {
                try {
                    Value t = v.getMember("tileWorld");
                    if (t != null && !t.isNull() && t.hasMembers()) {
                        float x = (float) num(t, "x", 0.0);
                        float z = (float) num(t, "z", 0.0);
                        if (x > 0f && z > 0f) tw = new TileWorld(x, z);
                    }
                } catch (Throwable ignored) {}
            }

            return new TextureDesc(
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

    public static void applyParam(AssetManager assets, Material m, String name, Value v) {
        if (m == null || name == null || name.isBlank() || isNull(v)) return;

        MatParam declared = m.getParam(name);
        if (declared != null && applyByDeclared(assets, m, name, declared, v)) return;

        TextureDesc td = parseTextureDesc(v);
        if (td != null) {
            setTexture(assets, m, name, td);
            return;
        }

        ColorRGBA c = parseColor(v);
        if (c != null) {
            m.setColor(name, c);
            return;
        }

        Vector2f v2 = parseVec2(v);
        if (v2 != null) {
            m.setVector2(name, v2);
            return;
        }

        Vector3f v3 = parseVec3(v);
        if (v3 != null) {
            m.setVector3(name, v3);
            return;
        }

        Vector4f v4 = parseVec4(v);
        if (v4 != null) {
            m.setVector4(name, v4);
            return;
        }

        if (v.isBoolean()) { m.setBoolean(name, v.asBoolean()); return; }
        if (v.isNumber())  { m.setFloat(name, (float) v.asDouble()); return; }

        if (v.isString()) {
            ParsedTex pt = parseTextureShorthand(v.asString());
            if (pt.path() != null && !pt.path().isBlank()) {
                setTexture(assets, m, name, new TextureDesc(pt.path(), pt.wrap(), null, null, 0, null));
            }
        }
    }

    private static boolean applyByDeclared(AssetManager assets, Material m, String name, MatParam declared, Value v) {
        String type = declared.getVarType().name();
        try {
            switch (type) {
                case "Boolean" -> {
                    if (v.isBoolean()) { m.setBoolean(name, v.asBoolean()); return true; }
                    if (v.isNumber())  { m.setBoolean(name, v.asDouble() != 0.0); return true; }
                }
                case "Int" -> {
                    if (v.isNumber()) { m.setInt(name, (int) Math.round(v.asDouble())); return true; }
                }
                case "Float" -> {
                    if (v.isNumber()) { m.setFloat(name, (float) v.asDouble()); return true; }
                }
                case "Color" -> {
                    ColorRGBA c = parseColor(v);
                    if (c != null) { m.setColor(name, c); return true; }
                }
                case "Vector2" -> {
                    Vector2f vv = parseVec2(v);
                    if (vv != null) { m.setVector2(name, vv); return true; }
                }
                case "Vector3" -> {
                    Vector3f vv = parseVec3(v);
                    if (vv != null) { m.setVector3(name, vv); return true; }
                }
                case "Vector4" -> {
                    Vector4f vv = parseVec4(v);
                    if (vv != null) { m.setVector4(name, vv); return true; }
                }
                case "Texture2D", "Texture3D", "TextureCubeMap" -> {
                    TextureDesc td = parseTextureDesc(v);
                    if (td != null) { setTexture(assets, m, name, td); return true; }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static ColorRGBA parseColor(Value v) {
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

    private static Vector2f parseVec2(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.hasArrayElements() && v.getArraySize() >= 2) {
            return new Vector2f(
                    (float) v.getArrayElement(0).asDouble(),
                    (float) v.getArrayElement(1).asDouble()
            );
        }

        if (v.hasMembers() && (v.hasMember("x") || v.hasMember("y"))) {
            return new Vector2f(
                    (float) num(v, "x", 0.0),
                    (float) num(v, "y", 0.0)
            );
        }

        return null;
    }

    private static Vector3f parseVec3(Value v) {
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

    private static Vector4f parseVec4(Value v) {
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

    private static void setTexture(AssetManager assets, Material m, String name, TextureDesc td) {
        if (td == null || td.texture() == null || td.texture().isBlank()) return;

        Texture t = assets.loadTexture(td.texture().trim());

        if (td.wrap() != null) t.setWrap(td.wrap());
        if (td.minFilter() != null) t.setMinFilter(td.minFilter());
        if (td.magFilter() != null) t.setMagFilter(td.magFilter());
        if (td.anisotropy() > 0) t.setAnisotropicFilter(td.anisotropy());

        m.setTexture(name, t);
    }
}