// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl.material;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.MaterialApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.foxesworld.kalitech.engine.api.impl.material.MaterialUtils.applyParam;
import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.member;
import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.str;

public final class MaterialApiImpl implements MaterialApi {

    private final EngineApiImpl engine;
    private final AssetManager assets;
    private final AtomicInteger ids = new AtomicInteger(1);

    // Template cache: def + stable params hash
    private final Cache<MaterialKey, Material> templateCache = Caffeine.newBuilder()
            .maximumSize(4096)
            .softValues()
            .recordStats()
            .build();

    public MaterialApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.assets = engine.getAssets();
    }

    @HostAccess.Export
    @Override
    public MaterialHandle create(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("material.create(cfg): cfg is required");

        String def = str(cfg, "def", null);
        if (def == null || def.isBlank()) throw new IllegalArgumentException("material.create: cfg.def is required");
        def = def.trim();

        Value params = member(cfg, "params");

        // Optional fast-key (doesn't change API): cfg.id / cfg.name => stable alias
        String alias = null;
        try {
            alias = str(cfg, "id", null);
            if (alias == null || alias.isBlank()) alias = str(cfg, "name", null);
            if (alias != null) alias = alias.trim();
            if (alias != null && alias.isBlank()) alias = null;
        } catch (Throwable ignored) {}

        MaterialKey key = MaterialKey.from(def, alias, params);

        String finalDef = def;
        Material template = templateCache.get(key, k -> buildTemplate(finalDef, params));

        // IMPORTANT: always clone so caller can mutate via material.set(...)
        Material m = template.clone();
        return new MaterialHandle(ids.getAndIncrement(), m);
    }

    private Material buildTemplate(String def, Value params) {
        Material m = new Material(assets, def);

        if (params != null && !params.isNull() && params.hasMembers()) {
            for (String key : sortedKeys(params.getMemberKeys())) {
                applyParam(assets, m, key, params.getMember(key));
            }
        }

        return m;
    }

    private static List<String> sortedKeys(Set<String> keys) {
        if (keys == null || keys.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>(keys);
        out.sort(String::compareTo);
        return out;
    }

    @Override
    public void destroy(MaterialHandle handle) {
        // no-op: materials are GC-managed; we keep template cache separate
    }

    @HostAccess.Export
    @Override
    public void set(MaterialHandle handle, Value params) {
        if (handle == null || handle.material == null) throw new IllegalArgumentException("material.set: handle is required");
        if (params == null || params.isNull() || !params.hasMembers()) return;

        for (String key : sortedKeys(params.getMemberKeys())) {
            applyParam(assets, handle.material, key, params.getMember(key));
        }
    }

    public static final class MaterialHandle {
        private final int id;
        private final Material material;

        public MaterialHandle(int id, Material material) {
            this.id = id;
            this.material = material;
        }

        @HostAccess.Export
        public int id() { return id; }

        public Material __material() { return material; }
    }

    // ------------------------------------------------------------
    // Cache key: def + (optional alias) + stable params hash
    // ------------------------------------------------------------

    private record MaterialKey(String def, String alias, int paramsHash, int hash) {
        static MaterialKey from(String def, String alias, Value params) {
            int pHash = stableParamsHash(params);
            int h = Objects.hash(def, alias, pHash);
            return new MaterialKey(def, alias, pHash, h);
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof MaterialKey k)) return false;
            return hash == k.hash &&
                    paramsHash == k.paramsHash &&
                    Objects.equals(def, k.def) &&
                    Objects.equals(alias, k.alias);
        }
    }

    // Fast, deterministic hash of params (ordered by key)
    private static int stableParamsHash(Value params) {
        if (params == null || params.isNull() || !params.hasMembers()) return 0;

        ArrayList<String> keys = new ArrayList<>(params.getMemberKeys());
        keys.sort(String::compareTo);

        int h = 1;
        for (String k : keys) {
            h = 31 * h + k.hashCode();
            h = 31 * h + stableValueHash(params.getMember(k));
        }
        return h;
    }

    private static int stableValueHash(Value v) {
        if (v == null || v.isNull()) return 0;

        try {
            if (v.isBoolean()) return v.asBoolean() ? 1231 : 1237;
            if (v.isNumber()) {
                long bits = Double.doubleToLongBits(v.asDouble());
                return (int)(bits ^ (bits >>> 32));
            }
            if (v.isString()) {
                MaterialUtils.ParsedTex pt = MaterialUtils.parseTextureShorthand(v.asString());
                if (pt.path() != null && !pt.path().isBlank()) {
                    String wrap = (pt.wrap() == null) ? "" : pt.wrap().name();
                    return Objects.hash("tex", pt.path().trim(), wrap);
                }
                return Objects.hash("s", v.asString());
            }

            if (v.hasArrayElements()) {
                int h = 1;
                long n = v.getArraySize();
                for (int i = 0; i < n; i++) {
                    h = 31 * h + stableValueHash(v.getArrayElement(i));
                }
                return Objects.hash("a", h, (int)n);
            }

            if (v.hasMembers()) {
                // texture object shortcut
                if (v.hasMember("texture")) {
                    MaterialUtils.TextureDesc td = MaterialUtils.parseTextureDesc(v);
                    if (td != null) {
                        return Objects.hash(
                                "texo",
                                td.texture(),
                                td.wrap() == null ? "" : td.wrap().name(),
                                td.minFilter() == null ? "" : td.minFilter().name(),
                                td.magFilter() == null ? "" : td.magFilter().name(),
                                td.anisotropy()
                        );
                    }
                }

                ArrayList<String> keys = new ArrayList<>(v.getMemberKeys());
                keys.sort(String::compareTo);
                int h = 1;
                for (String k : keys) {
                    h = 31 * h + k.hashCode();
                    h = 31 * h + stableValueHash(v.getMember(k));
                }
                return Objects.hash("o", h);
            }
        } catch (Throwable ignored) {}

        return 777; // fallback deterministic-ish
    }
}