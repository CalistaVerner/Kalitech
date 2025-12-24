package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.asset.AssetKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.ScriptComponent;
import org.foxesworld.kalitech.engine.script.EntityScriptAPI;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

public final class ScriptSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(ScriptSystem.class);

    private final EcsWorld ecs;

    // config
    private final boolean hotReload;
    private final float reloadCooldownSec;
    private final Path watchRoot;

    private GraalScriptRuntime runtime;
    private HotReloadWatcher watcher;

    private float cooldown = 0f;
    private boolean dirty = true;

    // entityId -> stable API wrapper (avoid per-frame allocations)
    private final Map<Integer, EntityScriptAPI> apiCache = new HashMap<>();

    // moduleId(assetPath) -> module record
    private final Map<String, ModuleRec> modules = new HashMap<>();

    public ScriptSystem(EcsWorld ecs, boolean hotReload, float reloadCooldownSec, Path watchRoot) {
        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.hotReload = hotReload;
        this.reloadCooldownSec = reloadCooldownSec <= 0 ? 0.25f : reloadCooldownSec;
        this.watchRoot = Objects.requireNonNull(watchRoot, "watchRoot");
    }

    public ScriptSystem(EcsWorld ecs) {
        this(ecs, false, 0.35f, Path.of("assets"));
    }

    @Override
    public void onStart(SystemContext ctx) {
        // IMPORTANT: use shared runtime from ctx (one runtime policy)
        // If you want per-system runtime, replace with new GraalScriptRuntime().
        this.runtime = ctx.runtime();

        if (hotReload) {
            watcher = new HotReloadWatcher(watchRoot);
            log.info("ScriptSystem started hotReload=true root={}", watchRoot.toAbsolutePath());
        } else {
            log.info("ScriptSystem started hotReload=false");
        }

        dirty = true;
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        // hot reload trigger
        if (watcher != null) {
            cooldown -= tpf;
            if (cooldown <= 0f && watcher.pollDirty()) {
                cooldown = reloadCooldownSec;
                dirty = true;
            }
        }

        // 1) If dirty - invalidate module cache in runtime and local module hashes will be rechecked
        if (dirty) {
            // We do NOT blindly clear everything; we re-hash per module on demand.
            log.debug("ScriptSystem: dirty -> rehash modules on demand");
        }

        // 2) Iterate entities with ScriptComponent
        for (var entry : ecs.components().view(ScriptComponent.class).entrySet()) {
            int entityId = entry.getKey();
            ScriptComponent sc = entry.getValue();

            ModuleRec mod = ensureModuleLoaded(ctx, sc.assetPath);
            if (mod == null) continue;

            // if entity has no instance OR module changed -> recreate instance
            if (sc.instance == null || !Objects.equals(sc.moduleHash, mod.hash)) {
                recreateInstance(ctx, entityId, sc, mod);
            }

            // tick
            callIfExists(sc.instance, "update", tpf);
        }

        dirty = false;
    }

    @Override
    public void onStop(SystemContext ctx) {
        // destroy instances
        for (var entry : ecs.components().view(ScriptComponent.class).entrySet()) {
            int entityId = entry.getKey();
            ScriptComponent sc = entry.getValue();
            safeDestroyInstance(entityId, sc);
        }

        apiCache.clear();
        modules.clear();

        if (watcher != null) {
            watcher.close();
            watcher = null;
        }

        // runtime is shared (ctx.runtime()), do NOT close it here
        runtime = null;

        log.info("ScriptSystem stopped");
    }

    private ModuleRec ensureModuleLoaded(SystemContext ctx, String assetPath) {
        ModuleRec rec = modules.get(assetPath);

        // fast path: not dirty and already loaded
        if (rec != null && !dirty) return rec;

        String code = loadTextAsset(ctx, assetPath, dirty);
        String hash = sha1(code);

        // unchanged
        if (rec != null && Objects.equals(rec.hash, hash)) return rec;

        // changed/new -> (re)load module exports
        try {
            // invalidate runtime cache for this module so require() gets fresh deps (important)
            runtimeInvalidate(assetPath);

            Value exports = runtime.loadModuleValue(assetPath, code);

            Value factory = pickFactory(exports);
            if (factory == null) {
                log.error("Script '{}' must export create(api) function", assetPath);
                return null;
            }

            ModuleRec nrec = new ModuleRec(assetPath, hash, exports, factory);
            modules.put(assetPath, nrec);

            log.info("Script module loaded: {} (hash={})", assetPath, hash);
            return nrec;

        } catch (Exception e) {
            log.error("Failed to load script module '{}'", assetPath, e);
            return null;
        }
    }

    private void recreateInstance(SystemContext ctx, int entityId, ScriptComponent sc, ModuleRec mod) {
        // destroy old
        safeDestroyInstance(entityId, sc);

        try {
            EntityScriptAPI api = apiCache.computeIfAbsent(entityId, id -> new EntityScriptAPI(
                    id, ecs, ctx.app(), ctx.events()
            ));

            // create new instance via factory
            Value instance = mod.factory.execute(api);
            sc.instance = instance;
            sc.moduleHash = mod.hash;

            callIfExists(sc.instance, "init");
            log.debug("Script instance created: entity={} module={}", entityId, mod.moduleId);

        } catch (Exception e) {
            log.error("Failed to create script instance: entity={} module={}", entityId, mod.moduleId, e);
            sc.instance = null;
            sc.moduleHash = null;
        }
    }

    private void safeDestroyInstance(int entityId, ScriptComponent sc) {
        if (sc == null || sc.instance == null) return;
        try {
            callIfExists(sc.instance, "destroy");
        } catch (Exception e) {
            log.error("destroy() failed: entity={} module={}", entityId, sc.assetPath, e);
        } finally {
            sc.instance = null;
            sc.moduleHash = null;
        }
    }

    private static Value pickFactory(Value exports) {
        if (exports == null || exports.isNull()) return null;

        // CommonJS case: module.exports = { create(){} }
        if (exports.hasMember("create")) {
            Value f = exports.getMember("create");
            if (f != null && f.canExecute()) return f;
        }

        // legacy: module itself is executable (rare)
        if (exports.canExecute()) return exports;

        return null;
    }

    private static void callIfExists(Value obj, String fn, Object... args) {
        if (obj == null || obj.isNull()) return;
        if (!obj.hasMember(fn)) return;
        Value f = obj.getMember(fn);
        if (f == null || !f.canExecute()) return;
        f.execute(args);
    }

    private static String loadTextAsset(SystemContext ctx, String assetPath, boolean flushCache) {
        if (flushCache) ctx.assets().deleteFromCache(new AssetKey<>(assetPath));
        return (String) ctx.assets().loadAsset(new AssetKey<>(assetPath));
    }

    private void runtimeInvalidate(String moduleId) {
        // optional method (below we will add it to GraalScriptRuntime). If not present, ignore.
        try {
            runtime.getClass().getMethod("invalidate", String.class).invoke(runtime, moduleId);
        } catch (Exception ignored) {}
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static final class ModuleRec {
        final String moduleId;
        final String hash;
        final Value exports;
        final Value factory;

        ModuleRec(String moduleId, String hash, Value exports, Value factory) {
            this.moduleId = moduleId;
            this.hash = hash;
            this.exports = exports;
            this.factory = factory;
        }
    }
}