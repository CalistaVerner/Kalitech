// FILE: org/foxesworld/kalitech/engine/api/impl/MaterialApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.MaterialApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.*;
import static org.foxesworld.kalitech.engine.api.util.MaterialUtils.*;

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

        Material m = new Material(assets, def.trim());

        Value params = member(cfg, "params");
        if (params != null && !params.isNull() && params.hasMembers()) {
            for (String key : params.getMemberKeys()) {
                applyParam(assets, m, key, params.getMember(key));
            }
        }

        return new MaterialHandle(ids.getAndIncrement(), m);
    }

    /** Update params without recreating material */
    @HostAccess.Export
    public void set(MaterialHandle handle, Value params) {
        if (handle == null || handle.__material() == null) throw new IllegalArgumentException("material.set: handle is null");
        if (params == null || params.isNull() || !params.hasMembers()) return;

        Material m = handle.__material();
        for (String key : params.getMemberKeys()) {
            applyParam(assets, m, key, params.getMember(key));
        }
    }

    @HostAccess.Export
    @Override
    public void destroy(MaterialHandle handle) {
        // Сейчас у вас handle просто обёртка над JME Material.
        // Можно оставить noop, либо позже добавить пул/рефкаунт.
    }

    public static final class MaterialHandle {
        private final int id;
        private final Material material;

        public MaterialHandle(int id, Material material) {
            this.id = id;
            this.material = material;
        }

        public int id() { return id; }
        public Material __material() { return material; } // engine internal only
    }
}