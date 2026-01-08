// FILE: org/foxesworld/kalitech/engine/api/impl/AssetsApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.AssetsApi;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.asset.AssetIO;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.member;

public final class AssetsApiImpl extends AbstractApiModule implements AssetsApi {

    private static final Logger L = LogManager.getLogger(AssetsApiImpl.class);

    private AssetManager assets;
    private SurfaceRegistry surfaceRegistry;

    public AssetsApiImpl() {
        super("assets", "Assets", "1.0.0");
    }

    private static String normalizePath(String api, String assetPath) {
        if (assetPath == null || assetPath.isBlank()) throw new IllegalArgumentException(api + ": path is empty");
        return assetPath.trim();
    }

    @Override
    public void attach(org.foxesworld.kalitech.engine.api.module.ApiContext ctx) {
        super.attach(ctx);
        this.assets = ctx.assets;
        this.surfaceRegistry = ctx.engine.getSurfaceRegistry();
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new HashMap<>();
        if (kv == null) return out;
        for (int i = 0; i + 1 < kv.length; i += 2) out.put(String.valueOf(kv[i]), kv[i + 1]);
        return out;
    }

    private void emit(String topic, Map<String, Object> payload) {
        try {
            var b = engine.getBus();
            if (b != null) b.emit(topic, payload);
        } catch (Throwable t) {
            L.debug("[assets] emit failed topic={}", topic, t);
        }
    }

    @SuppressWarnings("unchecked")
    private void tryRegisterLoaderReflect(String loaderClassName, String... extensions) {
        profiledVoid(() -> {
            try {
                Class<?> c = Class.forName(loaderClassName);
                if (!AssetLoader.class.isAssignableFrom(c)) {
                    L.warn("[assets] class {} is not an AssetLoader", loaderClassName);
                    return;
                }
                assets.registerLoader((Class<? extends AssetLoader>) c, extensions);
                L.info("[assets] registered loader={} extensions={}", loaderClassName, String.join(",", extensions));
            } catch (ClassNotFoundException e) {
                L.warn("[assets] loader not on classpath: {} (skip)", loaderClassName);
            } catch (Throwable t) {
                L.warn("[assets] failed to register loader: {}", loaderClassName, t);
            }
        });
    }

    private void safeRegisterLoader(Class<? extends AssetLoader> loader, String... extensions) {
        profiledVoid(() -> {
            try {
                assets.registerLoader(loader, extensions);
                L.info("[assets] registered loader={} extensions={}", loader.getName(), String.join(",", extensions));
            } catch (Throwable t) {
                L.warn("[assets] failed to register loader={} extensions={}", loader.getName(), String.join(",", extensions), t);
            }
        });
    }

    @HostAccess.Export
    @Override
    public String readText(String assetPath) {
        return profiled(() -> {
            String path = normalizePath("assets.readText(path)", assetPath);
            return AssetIO.readTextUtf8(assets, path);
        });
    }

    @HostAccess.Export
    public String readJsVerified(String assetPath) {
        return profiled(() -> {
            String path = normalizePath("assets.readJsVerified(path)", assetPath);

            try {
                Object obj = assets.loadAsset(path);
                if (!(obj instanceof String s)) {
                    throw new IllegalStateException("JS loader returned non-string for path='" + path + "': " +
                            (obj == null ? "null" : obj.getClass().getName()));
                }
                return s;
            } catch (Throwable t) {
                try {
                    return AssetIO.readTextUtf8(assets, path);
                } catch (Throwable t2) {
                    t2.addSuppressed(t);
                    throw t2;
                }
            }
        });
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle loadModel(String assetPath, Value cfg) {
        return profiled(() -> {
            String path = normalizePath("assets.loadModel(path,cfg)", assetPath);

            emit("engine.assets.model.load.before", m(
                    "path", path,
                    "cfg", (cfg == null || cfg.isNull()) ? null : cfg
            ));

            final Spatial model;
            try {
                model = assets.loadModel(path);
            } catch (Throwable t) {
                emit("engine.assets.model.load.error", m("path", path, "error", String.valueOf(t)));
                throw new IllegalStateException("assets.loadModel: failed to load model path='" + path + "'", t);
            }

            if (model == null)
                throw new IllegalStateException("assets.loadModel: model is null for path='" + path + "'");

            if (cfg != null && !cfg.isNull()) {
                Value n = member(cfg, "name");
                if (n != null && !n.isNull() && n.isString()) {
                    String name = n.asString();
                    if (name != null && !name.isBlank()) model.setName(name);
                }
            }

            SurfaceApi api = engine.surface();
            SurfaceApi.SurfaceHandle h = surfaceRegistry.register(model, "model", api);

            SurfaceApiImpl.applyTransform(model, cfg);

            if (cfg != null && !cfg.isNull()) {
                Value sm = member(cfg, "shadow");
                if (sm != null && !sm.isNull() && sm.isString()) api.setShadowMode(h, sm.asString());
            }

            if (cfg != null && !cfg.isNull()) {
                Value mat = member(cfg, "material");
                if (mat != null && !mat.isNull()) {
                    try {
                        api.setMaterial(h, mat);
                    } catch (Throwable t) {
                        L.warn("[assets] loadModel: material override failed path={} id={}", path, h.id(), t);
                    }
                }
            }

            boolean attach = true;
            if (cfg != null && !cfg.isNull()) {
                Value a = member(cfg, "attach");
                if (a != null && !a.isNull()) attach = a.asBoolean();
            }
            if (attach) api.attachToRoot(h);

            if (cfg != null && !cfg.isNull()) {
                Value ent = member(cfg, "entityId");
                if (ent != null && !ent.isNull() && ent.fitsInInt()) {
                    int entityId = ent.asInt();
                    if (entityId > 0) api.attach(h, entityId);
                }
            }

            emit("engine.assets.model.load.after", m(
                    "path", path,
                    "surfaceId", h.id(),
                    "name", model.getName()
            ));

            return h;
        });
    }
}