package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.api.interfaces.AssetsApi;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.asset.AssetIO;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.member;

/**
 * Assets API (script-facing).
 *
 * <p>Contract:
 * - Paths are validated and normalized.
 * - Emits engine events for load lifecycle.
 * - Does not expose engine internals.
 */
public final class AssetsApiImpl extends AbstractApiModule implements AssetsApi {

    private AssetManager assets;
    private SurfaceRegistry surfaceRegistry;

    public AssetsApiImpl() {
        super("assets", "Assets", "1.0.0");
    }

    private static String normalizePath(String api, String assetPath) {
        if (assetPath == null) throw new IllegalArgumentException(api + ": path is null");
        String p = assetPath.trim();
        if (p.isEmpty()) throw new IllegalArgumentException(api + ": path is blank");
        return p;
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new HashMap<>();
        if (kv == null) return out;
        for (int i = 0; i + 1 < kv.length; i += 2) out.put(String.valueOf(kv[i]), kv[i + 1]);
        return out;
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.assets = Objects.requireNonNull(ctx.assets, "ctx.assets");
        this.surfaceRegistry = Objects.requireNonNull(ctx.engine.getSurfaceRegistry(), "surfaceRegistry");
    }

    @Override
    public void detach() {
        this.surfaceRegistry = null;
        this.assets = null;
        super.detach();
    }

    @HostAccess.Export
    @Override
    public String readText(String assetPath) {
        return profiled(() -> AssetIO.readTextUtf8(assets, normalizePath("assets.readText(path)", assetPath)));
    }

    @HostAccess.Export
    public String readJsVerified(String assetPath) {
        return profiled(() -> {
            String path = normalizePath("assets.readJsVerified(path)", assetPath);

            try {
                Object obj = assets.loadAsset(path);
                if (obj instanceof String s) return s;
                throw new IllegalStateException("JS loader returned non-string for path='" + path + "'");
            } catch (Throwable primary) {
                try {
                    return AssetIO.readTextUtf8(assets, path);
                } catch (Throwable fallback) {
                    fallback.addSuppressed(primary);
                    throw fallback;
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
                throw new IllegalStateException("assets.loadModel: failed path='" + path + "'", t);
            }

            if (model == null) {
                throw new IllegalStateException("assets.loadModel: model is null path='" + path + "'");
            }

            if (cfg != null && !cfg.isNull()) {
                Value n = member(cfg, "name");
                if (n != null && !n.isNull() && n.isString()) {
                    String name = n.asString();
                    if (name != null && !name.isBlank()) model.setName(name.trim());
                }
            }

            SurfaceApi api = engine.surface();
            SurfaceApi.SurfaceHandle h = surfaceRegistry.register(model, "model", api);

            //SurfaceApiImpl.applyTransform(model, cfg); LEGACY

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
                        if (log != null)
                            log.warn("[assets] loadModel: material override failed path={} id={}", path, h.id(), t);
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
                Value ent = member(cfg, "entityUuid");
                if (ent == null || ent.isNull()) ent = member(cfg, "entity");
                if (ent != null && !ent.isNull() && ent.isString()) {
                    String uuid = ent.asString();
                    if (uuid != null && !uuid.isBlank()) api.attachEntity(h, uuid.trim());
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

    @SuppressWarnings("unchecked")
    public void tryRegisterLoaderReflect(String loaderClassName, String... extensions) {
        profiledVoid(() -> {
            Objects.requireNonNull(loaderClassName, "loaderClassName");
            try {
                Class<?> c = Class.forName(loaderClassName);
                if (!AssetLoader.class.isAssignableFrom(c)) {
                    if (log != null) log.warn("[assets] class {} is not an AssetLoader", loaderClassName);
                    return;
                }
                assets.registerLoader((Class<? extends AssetLoader>) c, extensions);
                if (log != null)
                    log.info("[assets] registered loader={} extensions={}", loaderClassName, String.join(",", extensions));
            } catch (ClassNotFoundException e) {
                if (log != null) log.warn("[assets] loader not on classpath: {} (skip)", loaderClassName);
            } catch (Throwable t) {
                if (log != null) log.warn("[assets] failed to register loader: {}", loaderClassName, t);
            }
        });
    }

    public void safeRegisterLoader(Class<? extends AssetLoader> loader, String... extensions) {
        profiledVoid(() -> {
            Objects.requireNonNull(loader, "loader");
            try {
                assets.registerLoader(loader, extensions);
                if (log != null)
                    log.info("[assets] registered loader={} extensions={}", loader.getName(), String.join(",", extensions));
            } catch (Throwable t) {
                if (log != null)
                    log.warn("[assets] failed to register loader={} extensions={}", loader.getName(), String.join(",", extensions), t);
            }
        });
    }

    private void emit(String topic, Map<String, Object> payload) {
        try {
            var b = (engine == null) ? null : engine.getBus();
            if (b != null) b.emit(topic, payload);
        } catch (Throwable t) {
            if (log != null && log.isDebugEnabled()) log.debug("[assets] emit failed topic={}", topic, t);
        }
    }
}