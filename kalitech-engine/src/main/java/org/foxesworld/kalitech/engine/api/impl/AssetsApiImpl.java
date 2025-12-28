// FILE: AssetsApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.AssetsApi;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.member;

public final class AssetsApiImpl implements AssetsApi {

    private static final Logger log = LogManager.getLogger(AssetsApiImpl.class);

    private static final AtomicBoolean LOADERS_REGISTERED = new AtomicBoolean(false);

    private final EngineApiImpl engine;
    private final AssetManager assets;
    private final SurfaceRegistry surfaceRegistry;

    public AssetsApiImpl(EngineApiImpl engineApi) {
        this.engine = Objects.requireNonNull(engineApi, "engineApi");
        this.assets = Objects.requireNonNull(engineApi.getAssets(), "assets");
        this.surfaceRegistry = engineApi.getSurfaceRegistry();
        ensureModelLoadersRegistered();
    }

    private void ensureModelLoadersRegistered() {
        if (!LOADERS_REGISTERED.compareAndSet(false, true)) return;
        tryRegisterLoader("com.jme3.scene.plugins.OBJLoader", "obj", "mtl");
        tryRegisterLoader("com.jme3.scene.plugins.fbx.FbxLoader", "fbx");
        // Add more here later (assimp, etc.).
    }

    @SuppressWarnings("unchecked")
    private void tryRegisterLoader(String loaderClassName, String... extensions) {
        try {
            Class<?> c = Class.forName(loaderClassName);
            if (!AssetLoader.class.isAssignableFrom(c)) {
                log.warn("AssetsApi: class {} is not an AssetLoader", loaderClassName);
                return;
            }
            assets.registerLoader((Class<? extends AssetLoader>) c, extensions);
            if (log.isInfoEnabled()) {
                log.info("AssetsApi: registered loader={} extensions={}", loaderClassName, String.join(",", extensions));
            }
        } catch (ClassNotFoundException e) {
            // Optional dependency not present.
            log.warn("AssetsApi: loader not on classpath: {} (skip)", loaderClassName);
        } catch (Throwable t) {
            log.warn("AssetsApi: failed to register loader: {}", loaderClassName, t);
        }
    }

    @HostAccess.Export
    @Override
    public String readText(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            throw new IllegalArgumentException("assets.readText(path): path is empty");
        }
        Object obj = assets.loadAsset(new AssetKey<>(assetPath.trim()));
        if (obj == null) return null;
        return (obj instanceof String s) ? s : String.valueOf(obj);
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle loadModel(String assetPath, Value cfg) {
        if (assetPath == null || assetPath.isBlank()) {
            throw new IllegalArgumentException("assets.loadModel(path,cfg): path is empty");
        }
        String path = assetPath.trim();

        // Ensure loaders exist (in case API was constructed before dependencies were ready).
        ensureModelLoadersRegistered();

        Spatial model;
        try {
            model = assets.loadModel(path);
        } catch (Throwable t) {
            throw new IllegalStateException("assets.loadModel: failed to load model path='" + path + "'", t);
        }

        if (model == null) {
            throw new IllegalStateException("assets.loadModel: model is null for path='" + path + "'");
        }

        // Optional name override.
        if (cfg != null && !cfg.isNull()) {
            try {
                Value n = member(cfg, "name");
                if (n != null && !n.isNull() && n.isString()) {
                    String name = n.asString();
                    if (name != null && !name.isBlank()) model.setName(name);
                }
            } catch (Throwable ignored) {}
        }

        // Register in registry first (so follow-up operations can use handle).
        SurfaceApi api = engine.surface();
        SurfaceApi.SurfaceHandle h = surfaceRegistry.register(model, "model", api);

        // Apply transform (pos/rot/scale) if any.
        try {
            SurfaceApiImpl.applyTransform(model, cfg);
        } catch (Throwable ignored) {}

        // Optional shadow mode.
        if (cfg != null && !cfg.isNull()) {
            try {
                Value sm = member(cfg, "shadow");
                if (sm != null && !sm.isNull() && sm.isString()) {
                    api.setShadowMode(h, sm.asString());
                }
            } catch (Throwable ignored) {}
        }

        // Optional material override: cfg.material can be a MaterialHandle or material cfg.
        if (cfg != null && !cfg.isNull()) {
            try {
                Value mat = member(cfg, "material");
                if (mat != null && !mat.isNull()) {
                    api.setMaterial(h, mat);
                }
            } catch (Throwable t) {
                log.warn("assets.loadModel: material override failed path={} id={}", path, h.id(), t);
            }
        }

        // Attach logic: default true
        boolean attach = true;
        if (cfg != null && !cfg.isNull()) {
            try {
                Value a = member(cfg, "attach");
                if (a != null && !a.isNull()) attach = a.asBoolean();
            } catch (Throwable ignored) {}
        }
        if (attach) {
            api.attachToRoot(h);
        }

        // Optional attach to entity.
        if (cfg != null && !cfg.isNull()) {
            try {
                Value ent = member(cfg, "entityId");
                if (ent != null && !ent.isNull() && ent.fitsInInt()) {
                    int entityId = ent.asInt();
                    if (entityId > 0) api.attach(h, entityId);
                }
            } catch (Throwable ignored) {}
        }

        return h;
    }
}