package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetManager;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.MaterialApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class MaterialApiImpl implements MaterialApi {

    private final AssetManager assets;
    private final AtomicInteger ids = new AtomicInteger(1);

    public MaterialApiImpl(EngineApiImpl engineApi) {
        this.assets = Objects.requireNonNull(engineApi, "engineApi").getAssets();
    }

    @HostAccess.Export
    @Override
    public MaterialHandle create(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("material.create(cfg): cfg is null");

        String def = str(cfg, "def", null);
        if (def == null || def.isBlank()) throw new IllegalArgumentException("material.create: def is required");

        Material m = new Material(assets, def);

        Value params = member(cfg, "params");
        if (params != null && !params.isNull() && params.hasMembers()) {
            for (String key : params.getMemberKeys()) {
                Value v = params.getMember(key);
                applyParam(m, key, v);
            }
        }

        return new MaterialHandle(ids.getAndIncrement(), m);
    }

    @HostAccess.Export
    @Override
    public void destroy(MaterialHandle handle) {
        // JME Materials are GC-managed; keep method for API symmetry/future pooling.
    }

    // ---------------------------------------------------------------------
    // Typed param application (diamond: strong typing, minimal surprises)
    // ---------------------------------------------------------------------

    private enum ParamKind {
        TEXTURE,
        COLOR,
        BOOLEAN,
        INT,
        FLOAT,
        VECTOR2,
        VECTOR3,
        VECTOR4,
        UNKNOWN
    }

    private enum WrapMode {
        Repeat(Texture.WrapMode.Repeat),
        Clamp(Texture.WrapMode.Clamp);

        final Texture.WrapMode jme;
        WrapMode(Texture.WrapMode jme) { this.jme = jme; }

        static Texture.WrapMode parse(String s) {
            if (s == null || s.isBlank()) return null;
            for (WrapMode w : values()) {
                if (w.name().equalsIgnoreCase(s)) return w.jme;
            }
            return null;
        }
    }

    private enum TexHint {
        AUTO, // if value is string and looks like a texture path
        FORCE // if object has {texture:"..."}
    }

    private void applyParam(Material m, String name, Value v) {
        if (m == null) return;
        if (name == null || name.isBlank()) return;
        if (v == null || v.isNull()) return;

        // If MatDef declares param type — respect it.
        MatParam declared = m.getParam(name);
        ParamKind kind = kindOf(declared);

        // 1) Explicit object forms (texture/color/vec)
        if (v.hasMembers()) {
            // texture: { texture:"...", wrap:"Repeat" }
            if (v.hasMember("texture")) {
                applyTexture(m, name, v);
                return;
            }

            // color: { color:{r,g,b,a?} }
            if (v.hasMember("color")) {
                applyColor(m, name, v.getMember("color"));
                return;
            }

            // vectors: { vec2:{x,y} } / { vec3:{x,y,z} } / { vec4:{x,y,z,w} }
            if (v.hasMember("vec2")) {
                applyVectorN(m, name, v.getMember("vec2"), 2);
                return;
            }
            if (v.hasMember("vec3")) {
                applyVectorN(m, name, v.getMember("vec3"), 3);
                return;
            }
            if (v.hasMember("vec4")) {
                applyVectorN(m, name, v.getMember("vec4"), 4);
                return;
            }
        }

        // 2) Primitive values routed by declared kind (if known)
        if (kind != ParamKind.UNKNOWN) {
            if (applyByDeclaredKind(m, name, kind, v)) return;
        }

        // 3) Fallback: infer from value type (old behavior, but safer)
        if (v.isBoolean()) { m.setBoolean(name, v.asBoolean()); return; }
        if (v.fitsInInt()) { m.setInt(name, v.asInt()); return; }
        if (v.isNumber())  { m.setFloat(name, (float) v.asDouble()); return; }

        if (v.isString()) {
            // allow shorthand texture by string if param expects Texture2D OR it looks like a texture path
            String s = v.asString();
            if (looksLikeTexturePath(s) || kind == ParamKind.TEXTURE) {
                Texture t = assets.loadTexture(s);
                m.setTexture(name, t);
            }
        }
    }

    private boolean applyByDeclaredKind(Material m, String name, ParamKind kind, Value v) {
        switch (kind) {
            case BOOLEAN -> {
                if (v.isBoolean()) { m.setBoolean(name, v.asBoolean()); return true; }
                // tolerate 0/1
                if (v.isNumber()) { m.setBoolean(name, v.asInt() != 0); return true; }
                return false;
            }
            case INT -> {
                if (v.fitsInInt()) { m.setInt(name, v.asInt()); return true; }
                if (v.isNumber())  { m.setInt(name, (int) v.asDouble()); return true; }
                return false;
            }
            case FLOAT -> {
                if (v.isNumber()) { m.setFloat(name, (float) v.asDouble()); return true; }
                return false;
            }
            case TEXTURE -> {
                if (v.isString()) {
                    Texture t = assets.loadTexture(v.asString());
                    m.setTexture(name, t);
                    return true;
                }
                // allow object form handled earlier
                return false;
            }
            case COLOR -> {
                if (v.hasMembers()) {
                    // allow direct {r,g,b,a}
                    applyColor(m, name, v);
                    return true;
                }
                return false;
            }
            case VECTOR2 -> {
                if (v.hasMembers()) { applyVectorN(m, name, v, 2); return true; }
                return false;
            }
            case VECTOR3 -> {
                if (v.hasMembers()) { applyVectorN(m, name, v, 3); return true; }
                return false;
            }
            case VECTOR4 -> {
                if (v.hasMembers()) { applyVectorN(m, name, v, 4); return true; }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private ParamKind kindOf(MatParam declared) {
        if (declared == null) return ParamKind.UNKNOWN;

        // Avoid extra imports: use type name to map.
        String t = String.valueOf(declared.getVarType());
        // Common JME VarTypes: Boolean, Int, Float, Vector2, Vector3, Vector4, Texture2D, Texture3D, TextureCubeMap, Color
        if ("Boolean".equals(t)) return ParamKind.BOOLEAN;
        if ("Int".equals(t)) return ParamKind.INT;
        if ("Float".equals(t)) return ParamKind.FLOAT;
        if ("Vector2".equals(t)) return ParamKind.VECTOR2;
        if ("Vector3".equals(t)) return ParamKind.VECTOR3;
        if ("Vector4".equals(t)) return ParamKind.VECTOR4;
        if ("Color".equals(t)) return ParamKind.COLOR;

        // texture var types
        if (t.startsWith("Texture")) return ParamKind.TEXTURE;

        return ParamKind.UNKNOWN;
    }

    private void applyTexture(Material m, String name, Value v) {
        String texPath = str(v, "texture", null);
        if (texPath == null || texPath.isBlank()) return;

        Texture t = assets.loadTexture(texPath);

        String wrap = str(v, "wrap", null);
        Texture.WrapMode wm = WrapMode.parse(wrap);
        if (wm != null) t.setWrap(wm);

        // Optional: min/mag hints could go here later, without breaking config.
        m.setTexture(name, t);
    }

    private void applyColor(Material m, String name, Value c) {
        if (c == null || c.isNull()) return;
        float r = (float) num(c, "r", 1.0);
        float g = (float) num(c, "g", 1.0);
        float b = (float) num(c, "b", 1.0);
        float a = (float) num(c, "a", 1.0);
        m.setColor(name, new ColorRGBA(r, g, b, a));
    }

    /**
     * Vector support without adding JME vector imports:
     * - For now, we store vectors as ColorRGBA for vec4 (works for many shader params),
     *   and for vec2/vec3 we also use ColorRGBA with default w=1.0.
     *
     * If you later want strict types, swap to com.jme3.math.Vector2f/Vector3f/Vector4f
     * (but that would add imports + potential host config changes).
     */
    private void applyVectorN(Material m, String name, Value v, int n) {
        if (v == null || v.isNull()) return;
        float x = (float) num(v, "x", 0.0);
        float y = (float) num(v, "y", 0.0);
        float z = (float) num(v, "z", 0.0);
        float w = (float) num(v, "w", 1.0);

        if (n == 2) {
            m.setColor(name, new ColorRGBA(x, y, 0f, 1f));
        } else if (n == 3) {
            m.setColor(name, new ColorRGBA(x, y, z, 1f));
        } else {
            m.setColor(name, new ColorRGBA(x, y, z, w));
        }
    }

    private static boolean looksLikeTexturePath(String s) {
        if (s == null) return false;
        String x = s.toLowerCase();
        return x.endsWith(".png") || x.endsWith(".jpg") || x.endsWith(".dds") || x.endsWith(".jpeg") || x.endsWith(".tga");
    }

    private static Value member(Value v, String k) {
        return (v != null && !v.isNull() && v.hasMember(k)) ? v.getMember(k) : null;
    }

    private static String str(Value v, String k, String def) {
        try { Value m = member(v, k); return (m == null || m.isNull()) ? def : m.asString(); }
        catch (Exception e) { return def; }
    }

    private static double num(Value v, String k, double def) {
        try { Value m = member(v, k); return (m == null || m.isNull()) ? def : m.asDouble(); }
        catch (Exception e) { return def; }
    }

    public static final class MaterialHandle {
        private final int id;
        private final Material material;

        public MaterialHandle(int id, Material material) {
            this.id = id;
            this.material = material;
        }

        public int id() { return id; }          // optional
        public Material __material() { return material; } // engine internal only
    }
}