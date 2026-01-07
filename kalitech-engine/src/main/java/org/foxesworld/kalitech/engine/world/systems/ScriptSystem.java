package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.ScriptComponent;
import org.foxesworld.kalitech.engine.script.EntityScriptAPI;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ScriptSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(ScriptSystem.class);

    private final EcsWorld ecs;
    private final boolean hotReload;
    private final float cooldownSec;
    private final Path watchRoot;

    private SimpleApplication app;
    private ScriptEventBus bus;   // optional
    private ScriptRuntime runtime;

    private HotReloadWatcher watcher;
    private float cooldown = 0f;

    public ScriptSystem(EcsWorld ecs, boolean hotReload, float cooldownSec, Path watchRoot) {
        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.hotReload = hotReload;
        this.cooldownSec = (cooldownSec <= 0f) ? 0.25f : cooldownSec;
        this.watchRoot = Objects.requireNonNull(watchRoot, "watchRoot");
    }

    private static Value createInstance(Value exports) {
        if (exports == null || exports.isNull()) throw new IllegalStateException("Script module exports is null");

        if (exports.canExecute()) return exports.execute();

        if (exports.hasMember("create")) {
            Value c = exports.getMember("create");
            if (c != null && c.canExecute()) return c.execute();
        }

        return exports;
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
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    @Override
    public void onStart(SystemContext ctx) {
        this.app = Objects.requireNonNull(ctx.app(), "ctx.app");
        this.bus = ctx.events(); // optional
        this.runtime = ctx.runtime();

        if (this.runtime == null) {
            throw new IllegalStateException("ScriptSystem requires ScriptRuntime in SystemContext (ctx.runtime() is null)");
        }

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

        if (hotReload && watcher != null) {
            cooldown -= tpf;
            if (cooldown <= 0f) {
                Set<String> changed = watcher.pollChanged();
                if (changed != null && !changed.isEmpty()) {
                    cooldown = cooldownSec;

                    int removed;
                    try {
                        removed = runtime.invalidateManyWithReason(changed, "hotReload");
                    } catch (NoSuchMethodError e) {
                        removed = runtime.invalidateMany(changed);
                    }

                    log.debug("HotReload: changed={}, removedFromCache={}", changed.size(), removed);

                    if (bus != null) {
                        try {
                            bus.emit("hotreload:changed", changed);
                        } catch (Throwable ignored) {
                        }
                    }
                } else {
                    cooldown = 0f;
                }
            }
        }

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
    public void onStop(SystemContext systemContext) {
        try {
            var scripts = ecs.components().view(ScriptComponent.class);
            for (var e : scripts.entrySet()) {
                ScriptComponent sc = e.getValue();
                if (sc == null || sc.instance == null) continue;

                try {
                    callIfExists(sc.instance, "destroy");
                } catch (org.graalvm.polyglot.PolyglotException pe) {
                    // ignore cancelled context on shutdown
                } catch (Throwable t) {
                    log.warn("Script destroy failed for entity {}", e.getKey(), t);
                } finally {
                    sc.instance = null;
                    sc.moduleVersion = 0L;
                }
            }
        } catch (Throwable t) {
            log.warn("ScriptSystem stop encountered errors", t);
        }

        if (watcher != null) {
            try {
                watcher.close();
            } catch (Throwable ignored) {
            }
            watcher = null;
        }

        app = null;
        bus = null;
        runtime = null;
        log.info("ScriptSystem stopped");
    }

    private void ensureStarted(int entityId, ScriptComponent sc) {
        String moduleId = (sc.moduleId != null && !sc.moduleId.isBlank())
                ? sc.moduleId
                : normalize(sc.assetPath);

        long v = runtime.moduleVersion(moduleId);

        boolean needsStart = (sc.instance == null) || (sc.moduleVersion != v);
        if (!needsStart) return;

        if (sc.instance != null) {
            try {
                callIfExists(sc.instance, "destroy");
            } catch (Throwable ignored) {
            }
            sc.instance = null;
        }

        Value exports = runtime.require(moduleId);
        Value instance = createInstance(exports);

        sc.instance = instance;
        sc.moduleVersion = v;

        EntityScriptAPI api = new EntityScriptAPI(entityId, ecs, app, bus);
        callIfExists(sc.instance, "init", api);
    }
}