package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.ScriptComponent;
import org.foxesworld.kalitech.engine.script.EntityScriptAPI;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ScriptSystem (entities scripts)
 *
 * Contract (required by your provider):
 * new ScriptSystem(ctx.ecs(), hotReload, cooldownSec, watchRoot)
 *
 * Features:
 * - Per-entity lifecycle: init/update/destroy on ScriptComponent.instance
 * - HotReloadWatcher (optional):
 *    pollChanged() -> runtime.invalidateMany(changed)
 *    entity instances restart automatically via moduleVersion() change
 */
public final class ScriptSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(ScriptSystem.class);

    private final EcsWorld ecs;
    private final boolean hotReload;
    private final float cooldownSec;
    private final Path watchRoot;

    private SimpleApplication app;
    private ScriptEventBus bus;
    private GraalScriptRuntime runtime;

    private HotReloadWatcher watcher;
    private float cooldown = 0f;

    public ScriptSystem(EcsWorld ecs, boolean hotReload, float cooldownSec, Path watchRoot) {
        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.hotReload = hotReload;
        this.cooldownSec = cooldownSec <= 0 ? 0.25f : cooldownSec;
        this.watchRoot = Objects.requireNonNull(watchRoot, "watchRoot");
    }

    @Override
    public void onStart(SystemContext ctx) {
        this.app = Objects.requireNonNull(ctx.app(), "ctx.app");
        this.bus = Objects.requireNonNull(ctx.events(), "ctx.bus");
        this.runtime = Objects.requireNonNull(ctx.runtime(), "ctx.runtime");

        if (hotReload) {
            try {
                this.watcher = new HotReloadWatcher(watchRoot);
                log.info("ScriptSystem hotReload enabled (root={}, cooldown={}s)", watchRoot.toAbsolutePath(), cooldownSec);
            } catch (Throwable t) {
                log.warn("ScriptSystem hotReload failed to start watcher at {}", watchRoot.toAbsolutePath(), t);
                this.watcher = null;
            }
        } else {
            log.info("ScriptSystem hotReload disabled");
        }

        this.cooldown = 0f;
        log.info("ScriptSystem started");
    }

    @Override
    public void onUpdate(SystemContext context, float tpf) {
        if (runtime == null) return;

        // 1) hot reload -> invalidate changed modules in runtime cache
        if (hotReload && watcher != null) {
            cooldown -= tpf;
            if (cooldown <= 0f) {
                Set<String> changed = watcher.pollChanged();
                if (!changed.isEmpty()) {
                    cooldown = cooldownSec;

                    int removed = runtime.invalidateMany(changed);
                    log.debug("HotReload: changed={}, removedFromCache={}", changed.size(), removed);

                    // Optional: allow JS to react (safe/no hard dependency)
                    try { bus.emit("hotreload:changed", changed); } catch (Throwable ignored) {}
                }
            }
        }

        // 2) lifecycle for ScriptComponent
        // NOTE: view() creates a snapshot map. If this becomes hot, switch ComponentStore to forEach().
        Map<Integer, ScriptComponent> scripts = ecs.components().view(ScriptComponent.class);
        if (scripts.isEmpty()) return;

        for (var e : scripts.entrySet()) {
            int entityId = e.getKey();
            ScriptComponent sc = e.getValue();
            if (sc == null || sc.assetPath == null) continue;

            ensureStarted(entityId, sc);
            callIfExists(sc.instance, "update", tpf);
        }
    }

    @Override
    public void onStop(SystemContext context) {
        // destroy all instances deterministically
        try {
            Map<Integer, ScriptComponent> scripts = ecs.components().view(ScriptComponent.class);
            for (var e : scripts.entrySet()) {
                destroyInstance(e.getKey(), e.getValue());
                if (e.getValue() != null) {
                    e.getValue().instance = null;
                    e.getValue().moduleHash = null;
                }
            }
        } catch (Throwable t) {
            log.warn("ScriptSystem stop encountered errors", t);
        }

        if (watcher != null) {
            try { watcher.close(); } catch (Throwable ignored) {}
            watcher = null;
        }

        app = null;
        bus = null;
        runtime = null;

        log.info("ScriptSystem stopped");
    }

    // -------------------- lifecycle internals --------------------

    private void ensureStarted(int entityId, ScriptComponent sc) {
        String moduleId = normalize(sc.assetPath);

        long v = runtime.moduleVersion(moduleId);
        String vStr = Long.toString(v);

        boolean needsStart = (sc.instance == null) || (sc.moduleHash == null) || (!sc.moduleHash.equals(vStr));
        if (!needsStart) return;

        // If exists but outdated -> destroy first
        if (sc.instance != null) {
            destroyInstance(entityId, sc);
            sc.instance = null;
        }

        Value exports = runtime.require(moduleId);
        Value instance = createInstance(exports);

        sc.instance = instance;
        sc.moduleHash = vStr;

        EntityScriptAPI api = new EntityScriptAPI(entityId, ecs, app, bus);
        callIfExists(sc.instance, "init", api);
    }

    /**
     * Supported module shapes:
     * 1) module.exports = { init, update, destroy }
     * 2) module.exports = function() { return { init, update, destroy } }
     * 3) module.exports = { create: () => ({...}) }
     */
    private static Value createInstance(Value exports) {
        if (exports == null || exports.isNull()) {
            throw new IllegalStateException("Script module exports is null");
        }

        if (exports.canExecute()) {
            return exports.execute();
        }

        if (exports.hasMember("create")) {
            Value c = exports.getMember("create");
            if (c != null && c.canExecute()) return c.execute();
        }

        return exports;
    }

    private static void destroyInstance(int entityId, ScriptComponent sc) {
        if (sc == null || sc.instance == null) return;
        try {
            callIfExists(sc.instance, "destroy");
        } catch (Throwable ex) {
            log.warn("Script destroy failed for entity {}", entityId, ex);
        }
    }

    private static void callIfExists(Value obj, String member, Object... args) {
        if (obj == null || obj.isNull()) return;
        if (!obj.hasMember(member)) return;
        Value fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) return;
        fn.execute(args);
    }

    private static String normalize(String id) {
        if (id == null) return "";
        String s = id.trim().replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);
        return s;
    }
}