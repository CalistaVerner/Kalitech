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
import java.util.HashMap;
import java.util.Map;

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
    private boolean dirty = true; // first tick load

    // cache: assetPath -> lastHash (avoid reload when dirty but unchanged)
    private final Map<String, String> hashCache = new HashMap<>();

    public ScriptSystem(EcsWorld ecs, boolean hotReload, float reloadCooldownSec, Path watchRoot) {
        this.ecs = ecs;
        this.hotReload = hotReload;
        this.reloadCooldownSec = reloadCooldownSec;
        this.watchRoot = watchRoot;
    }

    // backward compatible ctor
    public ScriptSystem(EcsWorld ecs) {
        this(ecs, false, 0.35f, Path.of("assets"));
    }

    @Override
    public void onStart(SystemContext ctx) {
        runtime = new GraalScriptRuntime();

        if (hotReload) {
            watcher = new HotReloadWatcher(watchRoot);
            log.info("ScriptSystem started (per-entity scripts) hotReload=true root={}", watchRoot.toAbsolutePath());
        } else {
            log.info("ScriptSystem started (per-entity scripts) hotReload=false");
        }
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        // hot reload trigger
        if (watcher != null) {
            cooldown -= tpf;
            if (cooldown <= 0f && watcher.pollDirty()) {
                cooldown = reloadCooldownSec;
                dirty = true;
                log.debug("ScriptSystem: assets dirty -> reload check");
            }
        }

        for (var entry : ecs.components().view(ScriptComponent.class).entrySet()) {
            int entityId = entry.getKey();
            ScriptComponent sc = entry.getValue();

            ensureLoadedIfNeeded(ctx, entityId, sc);
            callIfExists(sc.moduleObject, "update", makeApi(ctx, entityId), tpf);
        }

        // once processed, clear dirty flag (we only reload check on next watcher ping)
        dirty = false;
    }

    @Override
    public void onStop(SystemContext ctx) {
        for (var entry : ecs.components().view(ScriptComponent.class).entrySet()) {
            int entityId = entry.getKey();
            ScriptComponent sc = entry.getValue();
            safeDestroy(ctx, entityId, sc);
        }

        if (watcher != null) {
            watcher.close();
            watcher = null;
        }

        if (runtime != null) {
            runtime.close();
            runtime = null;
        }

        hashCache.clear();
        log.info("ScriptSystem stopped");
    }

    private void safeDestroy(SystemContext ctx, int entityId, ScriptComponent sc) {
        if (sc.moduleObject == null) return;
        try {
            callIfExists(sc.moduleObject, "destroy", makeApi(ctx, entityId));
        } catch (Exception ex) {
            log.error("destroy() failed for entity {} script {}", entityId, sc.assetPath, ex);
        } finally {
            sc.moduleObject = null;
            sc.lastLoadedCodeHash = null;
        }
    }

    private EntityScriptAPI makeApi(SystemContext ctx, int entityId) {
        return new EntityScriptAPI(entityId, ecs, ctx.app(), ctx.events());
    }

    private void ensureLoadedIfNeeded(SystemContext ctx, int entityId, ScriptComponent sc) {
        // first load always
        if (sc.moduleObject == null) {
            reload(ctx, entityId, sc);
            return;
        }

        // if not dirty — skip any I/O work
        if (!dirty) return;

        // dirty: check hash, but do it once per assetPath
        String assetPath = sc.assetPath;
        String code = loadTextAsset(ctx, assetPath);
        String hash = sha1(code);

        String prev = hashCache.get(assetPath);
        if (prev != null && prev.equals(hash)) {
            // unchanged: do nothing
            return;
        }

        // changed: reload THIS entity module
        reloadWithCode(ctx, entityId, sc, code, hash);
        hashCache.put(assetPath, hash);
    }

    private void reload(SystemContext ctx, int entityId, ScriptComponent sc) {
        String code = loadTextAsset(ctx, sc.assetPath);
        String hash = sha1(code);
        reloadWithCode(ctx, entityId, sc, code, hash);
        hashCache.put(sc.assetPath, hash);
    }

    private void reloadWithCode(SystemContext ctx, int entityId, ScriptComponent sc, String code, String hash) {
        try {
            // destroy old (hot swap)
            callIfExists(sc.moduleObject, "destroy", makeApi(ctx, entityId));

            Value moduleValue = runtime.loadModuleValue(sc.assetPath, code);

            sc.moduleObject = moduleValue;
            sc.lastLoadedCodeHash = hash;

            callIfExists(sc.moduleObject, "init", makeApi(ctx, entityId));
            log.info("Loaded script '{}' for entity {}", sc.assetPath, entityId);

        } catch (Exception e) {
            log.error("Failed to load script '{}' for entity {}", sc.assetPath, entityId, e);
        }
    }

    private String loadTextAsset(SystemContext ctx, String assetPath) {
        // IMPORTANT: do not delete cache every frame. only when dirty.
        if (dirty) ctx.assets().deleteFromCache(new AssetKey<>(assetPath));
        return (String) ctx.assets().loadAsset(new AssetKey<>(assetPath));
    }

    private static void callIfExists(Value module, String fn, Object... args) {
        if (module == null || !module.hasMember(fn)) return;
        Value f = module.getMember(fn);
        if (f == null || !f.canExecute()) return;
        f.execute(args);
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
}