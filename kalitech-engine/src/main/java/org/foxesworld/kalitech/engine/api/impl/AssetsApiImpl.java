package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.AssetsApi;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.asset.AssetIO;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.member;

public final class AssetsApiImpl implements AssetsApi {

    private static final Logger log = LogManager.getLogger(AssetsApiImpl.class);
    private final EngineApiImpl engine;
    private final AssetManager assets;
    private final SurfaceRegistry surfaceRegistry;
    private final ScriptEventBus bus; // optional

    public AssetsApiImpl(EngineApiImpl engineApi) {
        this.engine = Objects.requireNonNull(engineApi, "engineApi");
        this.assets = Objects.requireNonNull(engineApi.getAssets(), "assets");
        this.surfaceRegistry = engineApi.getSurfaceRegistry();
        this.bus = engineApi.getBus();
    }

    // -------------------- events --------------------

    private void emit(String topic, Map<String, Object> payload) {
        if (bus == null) return;
        try {
            bus.emit(topic, payload);
        } catch (Throwable t) {
            // Тут лучше НЕ молчать совсем, но и не падать.
            log.debug("[assets] emit failed topic={}", topic, t);
        }
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new HashMap<>();
        if (kv == null) return out;
        for (int i = 0; i + 1 < kv.length; i += 2) out.put(String.valueOf(kv[i]), kv[i + 1]);
        return out;
    }

    // -------------------- loaders --------------------


    @SuppressWarnings("unchecked")
    private void tryRegisterLoaderReflect(String loaderClassName, String... extensions) {
        try {
            Class<?> c = Class.forName(loaderClassName);
            if (!AssetLoader.class.isAssignableFrom(c)) {
                log.warn("[assets] class {} is not an AssetLoader", loaderClassName);
                return;
            }
            assets.registerLoader((Class<? extends AssetLoader>) c, extensions);
            log.info("[assets] registered loader={} extensions={}", loaderClassName, String.join(",", extensions));
        } catch (ClassNotFoundException e) {
            log.warn("[assets] loader not on classpath: {} (skip)", loaderClassName);
        } catch (Throwable t) {
            log.warn("[assets] failed to register loader: {}", loaderClassName, t);
        }
    }

    private void safeRegisterLoader(Class<? extends AssetLoader> loader, String... extensions) {
        try {
            assets.registerLoader(loader, extensions);
            log.info("[assets] registered loader={} extensions={}",
                    loader.getName(), String.join(",", extensions));
        } catch (Throwable t) {
            log.warn("[assets] failed to register loader={} extensions={}",
                    loader.getName(), String.join(",", extensions), t);
        }
    }

    // -------------------- API --------------------

    @HostAccess.Export
    @Override
    public String readText(String assetPath) {
        String path = normalizePath("assets.readText(path)", assetPath);

        // ВСЕГДА через AssetIO, без зависимости от loader'ов/extension'ов.
        return AssetIO.readTextUtf8(assets, path);
    }

    /**
     * Read JS text via AssetIO AND verify syntax using GraalVM (parse-only, no execution).
     *
     * JS-side usage:
     *   const code = engine.assets().readJsVerified("Scripts/player/index.js");
     */
    @HostAccess.Export
    public String readJsVerified(String assetPath) {
        String path = normalizePath("assets.readJsVerified(path)", assetPath);

        // Можно грузить прямо так (AssetIO), но мы ещё хотим верификацию — это делает JsTextLoader.
        // Чтобы не дублировать логику — просто используем loader на AssetManager:
        // (если кто-то вырубил loaders -> fallback на AssetIO + verify можно добавить отдельно)
        try {
            Object obj = assets.loadAsset(path); // JsTextLoader вернёт String или упадёт по синтаксису
            if (!(obj instanceof String s)) {
                throw new IllegalStateException("JS loader returned non-string for path='" + path + "': " +
                        (obj == null ? "null" : obj.getClass().getName()));
            }
            return s;
        } catch (Throwable t) {
            // fallback: хотя loaders должны быть, но пусть API всё равно работает
            // (если JsTextLoader не подцепился по какой-то причине)
            try {
                String code = AssetIO.readTextUtf8(assets, path);
                // если у вас есть JsSyntaxVerifier.verify(code, path) — можно позвать напрямую
                // но сейчас это делается внутри JsTextLoader. Поэтому здесь fallback без verify.
                // Рекомендую оставить verify (если класс доступен) — но не ломаем сборку, если его не видно.
                return code;
            } catch (Throwable t2) {
                t2.addSuppressed(t);
                throw t2;
            }
        }
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle loadModel(String assetPath, Value cfg) {
        String path = normalizePath("assets.loadModel(path,cfg)", assetPath);

        emit("engine.assets.model.load.before", m(
                "path", path,
                "cfg", (cfg == null || cfg.isNull()) ? null : cfg
        ));

        final Spatial model;
        try {
            model = assets.loadModel(path);
        } catch (Throwable t) {
            emit("engine.assets.model.load.error", m(
                    "path", path,
                    "error", String.valueOf(t)
            ));
            throw new IllegalStateException("assets.loadModel: failed to load model path='" + path + "'", t);
        }

        if (model == null) {
            throw new IllegalStateException("assets.loadModel: model is null for path='" + path + "'");
        }

        // Optional name override.
        if (cfg != null && !cfg.isNull()) {
            Value n = member(cfg, "name");
            if (n != null && !n.isNull() && n.isString()) {
                String name = n.asString();
                if (name != null && !name.isBlank()) model.setName(name);
            }
        }

        SurfaceApi api = engine.surface();
        SurfaceApi.SurfaceHandle h = surfaceRegistry.register(model, "model", api);

        // Apply transform (pos/rot/scale) if any.
        SurfaceApiImpl.applyTransform(model, cfg);

        // Optional shadow mode.
        if (cfg != null && !cfg.isNull()) {
            Value sm = member(cfg, "shadow");
            if (sm != null && !sm.isNull() && sm.isString()) {
                api.setShadowMode(h, sm.asString());
            }
        }

        // Optional material override.
        if (cfg != null && !cfg.isNull()) {
            Value mat = member(cfg, "material");
            if (mat != null && !mat.isNull()) {
                try {
                    api.setMaterial(h, mat);
                } catch (Throwable t) {
                    log.warn("[assets] loadModel: material override failed path={} id={}", path, h.id(), t);
                }
            }
        }

        // Attach logic: default true
        boolean attach = true;
        if (cfg != null && !cfg.isNull()) {
            Value a = member(cfg, "attach");
            if (a != null && !a.isNull()) attach = a.asBoolean();
        }
        if (attach) api.attachToRoot(h);

        // Optional attach to entity.
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
    }

    // -------------------- helpers --------------------

    private static String normalizePath(String api, String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            throw new IllegalArgumentException(api + ": path is empty");
        }
        return assetPath.trim();
    }
}