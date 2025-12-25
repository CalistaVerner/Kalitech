// FILE: org/foxesworld/kalitech/engine/api/util/MaterialUtils.java
package org.foxesworld.kalitech.engine.api.util;

import com.jme3.asset.AssetManager;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.api.impl.MaterialApiImpl;

import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.*;

public final class MaterialUtils {
    private MaterialUtils() {}

    public static Material unwrapMaterial(Object h) {
        if (h == null) return null;

        if (h instanceof Material m) return m;

        if (h instanceof MaterialApiImpl.MaterialHandle mh) {
            return mh.__material();
        }

        if (h instanceof Value v) {
            if (v.isHostObject()) {
                Object host = v.asHostObject();
                if (host instanceof MaterialApiImpl.MaterialHandle mh) return mh.__material();
                if (host instanceof Material m) return m;
            }
        }
        return null;
    }

    public static Texture.WrapMode parseWrap(String s) {
        if (s == null || s.isBlank()) return null;
        for (Texture.WrapMode w : Texture.WrapMode.values()) {
            if (w.name().equalsIgnoreCase(s)) return w;
        }
        return null;
    }

    /** "path|Repeat" -> {path, wrap} */
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

    public record ParsedTex(String path, Texture.WrapMode wrap) {}

    public static void applyParam(AssetManager assets, Material m, String name, Value v) {
        if (m == null || name == null || name.isBlank() || isNull(v)) return;

        // declared type (если matdef знает параметр)
        MatParam declared = m.getParam(name);
        if (declared != null) {
            if (applyByDeclared(assets, m, name, declared, v)) return;
        }

        // object forms:
        // { texture:"...", wrap:"Repeat" }
        if (v.hasMembers() && v.hasMember("texture")) {
            String tex = str(v, "texture", null);
            String wrapS = str(v, "wrap", null);
            setTexture(assets, m, name, tex, parseWrap(wrapS));
            return;
        }

        // {r,g,b,a}
        if (v.hasMembers() && (v.hasMember("r") || v.hasMember("g") || v.hasMember("b"))) {
            float r = (float) num(v, "r", 1.0);
            float g = (float) num(v, "g", 1.0);
            float b = (float) num(v, "b", 1.0);
            float a = (float) num(v, "a", 1.0);
            m.setColor(name, new ColorRGBA(r, g, b, a));
            return;
        }

        // vec2/vec3/vec4 as {x,y,z,w} or array [..]
        if (v.hasMembers() && (v.hasMember("x") || v.hasMember("y"))) {
            float x = (float) num(v, "x", 0.0);
            float y = (float) num(v, "y", 0.0);
            float z = (float) num(v, "z", 0.0);
            float w = (float) num(v, "w", 1.0);
            // без Vector* типов — кладём в ColorRGBA (как у вас было)
            m.setColor(name, new ColorRGBA(x, y, z, w));
            return;
        }

        // primitives fallback
        if (v.isBoolean()) { m.setBoolean(name, v.asBoolean()); return; }
        if (v.fitsInInt()) { m.setInt(name, v.asInt()); return; }
        if (v.isNumber())  { m.setFloat(name, (float) v.asDouble()); return; }

        // string: try as texture shorthand; else ignore
        if (v.isString()) {
            ParsedTex pt = parseTextureShorthand(v.asString());
            if (pt.path() != null) {
                setTexture(assets, m, name, pt.path(), pt.wrap());
            }
        }
    }

    private static boolean applyByDeclared(AssetManager assets, Material m, String name, MatParam declared, Value v) {
        String type = declared.getVarType().name();
        try {
            switch (type) {
                case "Boolean" -> {
                    if (v.isBoolean()) { m.setBoolean(name, v.asBoolean()); return true; }
                    if (v.isNumber())  { m.setBoolean(name, v.asInt() != 0); return true; }
                }
                case "Int" -> {
                    if (v.fitsInInt()) { m.setInt(name, v.asInt()); return true; }
                    if (v.isNumber())  { m.setInt(name, (int) v.asDouble()); return true; }
                }
                case "Float" -> {
                    if (v.isNumber())  { m.setFloat(name, (float) v.asDouble()); return true; }
                }
                case "Texture2D", "Texture3D", "TextureCubeMap" -> {
                    if (v.isString()) {
                        ParsedTex pt = parseTextureShorthand(v.asString());
                        setTexture(assets, m, name, pt.path(), pt.wrap());
                        return true;
                    }
                    if (v.hasMembers() && v.hasMember("texture")) {
                        String tex = str(v, "texture", null);
                        String wrapS = str(v, "wrap", null);
                        setTexture(assets, m, name, tex, parseWrap(wrapS));
                        return true;
                    }
                }
                case "Vector2", "Vector3", "Vector4", "Color" -> {
                    // object/vec/color обработаем выше
                    return false;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void setTexture(AssetManager assets, Material m, String name, String texPath, Texture.WrapMode wrap) {
        if (texPath == null || texPath.isBlank()) return;
        Texture t = assets.loadTexture(texPath.trim());
        if (wrap != null) t.setWrap(wrap);
        m.setTexture(name, t);
    }
}