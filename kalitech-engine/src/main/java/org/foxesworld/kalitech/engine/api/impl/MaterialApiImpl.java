package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetManager;
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
        this.assets = engineApi.getAssets();
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

    private void applyParam(Material m, String name, Value v) {
        if (v == null || v.isNull()) return;

        // shorthand: { texture:"...", wrap:"Repeat" }
        if (v.hasMembers() && v.hasMember("texture")) {
            String texPath = str(v, "texture", null);
            if (texPath != null && !texPath.isBlank()) {
                Texture t = assets.loadTexture(texPath);

                String wrap = str(v, "wrap", null);
                if ("Repeat".equalsIgnoreCase(wrap)) t.setWrap(Texture.WrapMode.Repeat);
                if ("Clamp".equalsIgnoreCase(wrap)) t.setWrap(Texture.WrapMode.Clamp);

                m.setTexture(name, t);
            }
            return;
        }

        // color: { color:{r,g,b,a?} }
        if (v.hasMembers() && v.hasMember("color")) {
            Value c = v.getMember("color");
            float r = (float) num(c, "r", 1.0);
            float g = (float) num(c, "g", 1.0);
            float b = (float) num(c, "b", 1.0);
            float a = (float) num(c, "a", 1.0);
            m.setColor(name, new ColorRGBA(r, g, b, a));
            return;
        }

        // primitives
        if (v.isBoolean()) { m.setBoolean(name, v.asBoolean()); return; }
        if (v.fitsInInt()) { m.setInt(name, v.asInt()); return; }
        if (v.isNumber())  { m.setFloat(name, (float) v.asDouble()); return; }
        if (v.isString())  {
            // allow shorthand texture by string if param expects Texture2D
            String s = v.asString();
            if (s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".dds") || s.endsWith(".jpeg")) {
                Texture t = assets.loadTexture(s);
                m.setTexture(name, t);
            }
        }
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
