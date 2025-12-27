// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl.material;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.MaterialApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.foxesworld.kalitech.engine.api.impl.material.MaterialUtils.applyParam;
import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.member;
import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.str;

/**
 * Material factory for JS configs {def, params}.
 */
public final class MaterialApiImpl implements MaterialApi {

    private final EngineApiImpl engine;
    private final AssetManager assets;
    private final AtomicInteger ids = new AtomicInteger(1);

    public MaterialApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.assets = engine.getAssets();
    }

    /**
     * Creates a new MaterialHandle from {def, params}.
     */
    @HostAccess.Export
    @Override
    public MaterialHandle create(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("material.create(cfg): cfg is required");

        String def = str(cfg, "def", null);
        if (def == null || def.isBlank()) throw new IllegalArgumentException("material.create: cfg.def is required");

        Material m = new Material(assets, def);

        Value params = member(cfg, "params");
        if (params != null && !params.isNull() && params.hasMembers()) {
            for (String key : params.getMemberKeys()) {
                applyParam(assets, m, key, params.getMember(key));
            }
        }

        return new MaterialHandle(ids.getAndIncrement(), m);
    }

    @Override
    public void destroy(MaterialHandle handle) {

    }

    /**
     * Updates params on an existing handle.
     */
    @HostAccess.Export
    @Override
    public void set(MaterialHandle handle, Value params) {
        if (handle == null || handle.material == null) throw new IllegalArgumentException("material.set: handle is required");
        if (params == null || params.isNull() || !params.hasMembers()) return;

        for (String key : params.getMemberKeys()) {
            applyParam(assets, handle.material, key, params.getMember(key));
        }
    }

    /**
     * Stable host object handle.
     */
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
}