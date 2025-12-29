// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl.material;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.MaterialApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.foxesworld.kalitech.engine.api.impl.material.MaterialUtils.applyParamAsync;
import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.member;
import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.str;

public final class MaterialApiImpl implements MaterialApi {

    private static final Logger log = LogManager.getLogger(MaterialApiImpl.class);

    private final EngineApiImpl engine;
    private final AssetManager assets;
    private final AtomicInteger ids = new AtomicInteger(1);

    /**
     * Template cache: def+alias+paramsHash -> template Material (no heavy IO in template build).
     * Each create() returns clone() of template.
     */
    private final Cache<MaterialKey, Material> templateCache = Caffeine.newBuilder()
            .maximumSize(4096)
            .softValues()
            .recordStats()
            .build();

    public MaterialApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.assets = engine.getAssets();
        // allow MaterialUtils to schedule render-thread updates
        MaterialUtils.init(engine, assets);
    }

    @HostAccess.Export
    @Override
    public MaterialHandle create(Value cfg) {
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException("material.create(cfg): cfg is required");
        }

        String def = str(cfg, "def", null);
        if (def == null || def.isBlank()) {
            throw new IllegalArgumentException("material.create: cfg.def is required");
        }
        def = def.trim();

        final Value params = member(cfg, "params");

        String alias = null;
        try {
            alias = str(cfg, "id", null);
            if (alias == null || alias.isBlank()) alias = str(cfg, "name", null);
            if (alias != null) {
                alias = alias.trim();
                if (alias.isBlank()) alias = null;
            }
        } catch (Throwable ignored) {}

        final MaterialKey key = MaterialKey.from(def, alias, params);

        String finalDef = def;
        Material template = templateCache.get(key, k -> buildTemplate(finalDef, params));

        Material m;
        try {
            m = template.clone();
        } catch (Throwable e) {
            log.warn("material.create: template.clone() failed for def='{}' (fallback rebuild). {}", def, e.toString());
            m = buildTemplate(def, params);
        }

        // Even if template is cached, some textures might still be pending.
        // That's ok: MaterialUtils will swap placeholders -> real textures later.
        return new MaterialHandle(ids.getAndIncrement(), m);
    }

    private Material buildTemplate(String def, Value params) {
        final Material m = new Material(assets, def);

        if (params != null && !params.isNull() && params.hasMembers()) {
            List<String> keys = new ArrayList<>(params.getMemberKeys());
            keys.sort(String::compareTo);

            for (String key : keys) {
                boolean ok = applyParamAsync(m, key, params.getMember(key));
                if (!ok && MaterialUtils.isProbablyUnknownParam(m, key)) {
                    log.warn("material.create: unknown param '{}' for def='{}'", key, def);
                }
            }
        }

        return m;
    }

    @HostAccess.Export
    @Override
    public void set(MaterialHandle handle, Value params) {
        if (handle == null || handle.material == null) {
            throw new IllegalArgumentException("material.set: handle is required");
        }
        if (params == null || params.isNull() || !params.hasMembers()) return;

        List<String> keys = new ArrayList<>(params.getMemberKeys());
        keys.sort(String::compareTo);

        for (String key : keys) {
            boolean ok = applyParamAsync(handle.material, key, params.getMember(key));
            if (!ok && MaterialUtils.isProbablyUnknownParam(handle.material, key)) {
                log.warn("material.set: unknown param '{}' for materialId={}", key, handle.id);
            }
        }
    }

    @Override
    public void destroy(MaterialHandle handle) {
        // no-op
    }

    public static final class MaterialHandle {
        private final int id;
        final Material material;

        public MaterialHandle(int id, Material material) {
            this.id = id;
            this.material = material;
        }

        @HostAccess.Export
        public int id() { return id; }

        public Material __material() { return material; }
    }

    // ------------------------------------------------------------
    // Cache key
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
                return (int) (bits ^ (bits >>> 32));
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
                return Objects.hash("a", h, (int) n);
            }

            if (v.hasMembers()) {
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

        return 777;
    }
}