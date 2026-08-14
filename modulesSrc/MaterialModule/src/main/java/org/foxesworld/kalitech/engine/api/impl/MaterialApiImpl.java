/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.benmanes.caffeine.cache.Cache
 *  com.github.benmanes.caffeine.cache.Caffeine
 *  com.jme3.asset.AssetManager
 *  com.jme3.material.Material
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.api.module.AbstractApiModule
 *  org.foxesworld.kalitech.engine.api.module.ApiContext
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.MaterialApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.types.MaterialHandle;
import org.foxesworld.kalitech.engine.modules.material.MaterialTypes;
import org.foxesworld.kalitech.engine.modules.material.MaterialUtils;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class MaterialApiImpl
extends AbstractApiModule
implements MaterialApi {
    private static final Logger log = LogManager.getLogger(MaterialApiImpl.class);
    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Material> matById = new ConcurrentHashMap();
    private final Cache<MaterialKey, Material> templateCache = Caffeine.newBuilder().maximumSize(4096L).softValues().recordStats().build();
    private AssetManager assets;

    public MaterialApiImpl() {
        super("material", "Material", "1.0.0");
    }

    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.assets = Objects.requireNonNull(ctx.assets, "ctx.assets");
        MaterialUtils.init(ctx.engine, ctx.assets);
        MaterialUtils.setDebug(Boolean.getBoolean("kalitech.material.debug"));
    }

    public void detach() {
        this.assets = null;
        this.matById.clear();
        this.templateCache.invalidateAll();
        super.detach();
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public MaterialHandle create(LuaValueRef cfg) {
        return (MaterialHandle)this.profiled(() -> {
            Material m;
            if (cfg == null || cfg.isNull()) {
                throw new IllegalArgumentException("material.create(cfg): cfg is required");
            }
            String def = LuaCfg.str((LuaValueRef)cfg, (String)"def", null);
            if (def == null || def.isBlank()) {
                throw new IllegalArgumentException("material.create: cfg.def is required");
            }
            def = def.trim();
            LuaValueRef params = LuaCfg.member((LuaValueRef)cfg, (String)"params");
            String alias = null;
            try {
                alias = LuaCfg.str((LuaValueRef)cfg, (String)"id", null);
                if (alias == null || alias.isBlank()) {
                    alias = LuaCfg.str((LuaValueRef)cfg, (String)"name", null);
                }
                if (alias != null && (alias = alias.trim()).isBlank()) {
                    alias = null;
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            MaterialKey key = MaterialKey.from(def, alias, params);
            String finalDef = def;
            Material template = this.templateCache.get(key, k -> this.buildTemplate(finalDef, params));
            try {
                m = template.clone();
            }
            catch (Throwable e) {
                log.warn("[material] template.clone failed def='{}' (fallback rebuild). {}", (Object)def, (Object)e.toString());
                m = this.buildTemplate(def, params);
            }
            // Async texture callbacks must target the actual material installed in the scene.
            // A cloned template only contains the placeholder that existed at clone time.
            this.applyParams(m, params, "instance def='" + def + "'");
            int id = this.ids.getAndIncrement();
            this.matById.put(id, m);
            return new MaterialHandle(id);
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public int createId(LuaValueRef cfg) {
        MaterialHandle h = this.create(cfg);
        return h != null ? h.id() : 0;
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public MaterialHandle getById(int id) {
        return this.matById.containsKey(id) ? new MaterialHandle(id) : null;
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void destroy(MaterialHandle handle) {
        if (handle == null) {
            return;
        }
        this.matById.remove(handle.id());
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void destroyById(int id) {
        if (id <= 0) {
            return;
        }
        this.matById.remove(id);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void set(MaterialHandle handle, LuaValueRef params) {
        this.profiledVoid(() -> {
            if (handle == null) {
                throw new IllegalArgumentException("material.set: handle is required");
            }
            this.setById(handle.id(), params);
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setById(int id, LuaValueRef params) {
        this.profiledVoid(() -> {
            if (id <= 0) {
                throw new IllegalArgumentException("material.setById: id must be > 0");
            }
            Material m = this.matById.get(id);
            if (m == null) {
                throw new IllegalArgumentException("material.setById: unknown materialId=" + id);
            }
            this.applyParams(m, params, "setById id=" + id);
        });
    }

    @Override
    public Material material(MaterialHandle handle) {
        if (handle == null) {
            return null;
        }
        return this.matById.get(handle.id());
    }

    @Override
    public Material materialById(int id) {
        if (id <= 0) {
            return null;
        }
        return this.matById.get(id);
    }

    private Material buildTemplate(String def, LuaValueRef params) {
        AssetManager am = this.assets;
        if (am == null) {
            throw new IllegalStateException("material: AssetManager is not attached");
        }
        Material m = new Material(am, def);
        this.applyParams(m, params, "template def='" + def + "'");
        return m;
    }

    private void applyParams(Material material, LuaValueRef params, String context) {
        if (params == null || params.isNull() || !params.hasMembers()) {
            return;
        }
        ArrayList<String> keys = new ArrayList<>(params.getMemberKeys());
        keys.sort(String::compareTo);
        for (String key : keys) {
            LuaValueRef value = params.getMember(key);
            boolean applied = MaterialUtils.applyParamAsync(material, key, value);
            if (!applied && MaterialUtils.isProbablyUnknownParam(material, key)) {
                log.warn("[material] {}: unknown param '{}'", context, key);
            }
        }
    }

    private static int stableParamsHash(LuaValueRef params) {
        if (params == null || params.isNull() || !params.hasMembers()) {
            return 0;
        }
        ArrayList<String> keys = new ArrayList<>(params.getMemberKeys());
        keys.sort(String::compareTo);
        int h = 1;
        for (String k : keys) {
            h = 31 * h + k.hashCode();
            h = 31 * h + MaterialApiImpl.stableValueHash(params.getMember(k));
        }
        return h;
    }

    private static int stableValueHash(LuaValueRef v) {
        if (v == null || v.isNull()) {
            return 0;
        }
        try {
            if (v.isBoolean()) {
                return v.asBoolean() ? 1231 : 1237;
            }
            if (v.isNumber()) {
                long bits = Double.doubleToLongBits(v.asDouble());
                return (int)(bits ^ bits >>> 32);
            }
            if (v.isString()) {
                MaterialTypes.ParsedTex pt = MaterialUtils.parseTextureShorthand(v.asString());
                if (pt != null && pt.path() != null && !pt.path().isBlank()) {
                    String wrap = pt.wrap() == null ? "" : pt.wrap().name();
                    return Objects.hash("tex", pt.path().trim(), wrap);
                }
                return Objects.hash("s", v.asString());
            }
            if (v.hasArrayElements()) {
                int h = 1;
                long n = v.getArraySize();
                int i = 0;
                while ((long)i < n) {
                    h = 31 * h + MaterialApiImpl.stableValueHash(v.getArrayElement((long)i));
                    ++i;
                }
                return Objects.hash("a", h, (int)n);
            }
            if (v.hasMembers()) {
                MaterialTypes.TextureDesc td;
                if (v.hasMember("texture") && (td = MaterialUtils.parseTextureDesc(v)) != null) {
                    return Objects.hash("texo", td.texture(), td.wrap() == null ? "" : td.wrap().name(), td.minFilter() == null ? "" : td.minFilter().name(), td.magFilter() == null ? "" : td.magFilter().name(), td.anisotropy(), td.tileWorld() == null ? "" : td.tileWorld().x() + ":" + td.tileWorld().z());
                }
                ArrayList<String> keys = new ArrayList<>(v.getMemberKeys());
                keys.sort(String::compareTo);
                int h = 1;
                for (String k : keys) {
                    h = 31 * h + k.hashCode();
                    h = 31 * h + MaterialApiImpl.stableValueHash(v.getMember(k));
                }
                return Objects.hash("o", h);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return 777;
    }

    private record MaterialKey(String def, String alias, int paramsHash, int hash) {
        static MaterialKey from(String def, String alias, LuaValueRef params) {
            int pHash = MaterialApiImpl.stableParamsHash(params);
            int h = Objects.hash(def, alias, pHash);
            return new MaterialKey(def, alias, pHash, h);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MaterialKey)) {
                return false;
            }
            MaterialKey k = (MaterialKey)o;
            return this.hash == k.hash && this.paramsHash == k.paramsHash && Objects.equals(this.def, k.def) && Objects.equals(this.alias, k.alias);
        }
    }
}

