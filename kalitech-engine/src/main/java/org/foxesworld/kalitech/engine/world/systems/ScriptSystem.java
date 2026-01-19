// FILE: org/foxesworld/kalitech/engine/world/systems/ScriptSystem.java
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
import org.foxesworld.kalitech.engine.script.util.StateCapsule;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ScriptSystem binds per-entity script components to ScriptRuntime modules.
 *
 * <p>Important:</p>
 * <ul>
 *   <li>No reflection is used. ScriptRuntime must expose a stable API.</li>
 *   <li>Errors are observable (logged). The system does not silently swallow failures.</li>
 * </ul>
 *
 * <p>Required ScriptRuntime API (stable contract):</p>
 * <ul>
 *   <li>{@code Value require(String moduleId)}</li>
 *   <li>{@code long moduleVersion(String moduleId)}</li>
 *   <li>{@code int invalidateManyWithReason(Set<String> ids, String reason)}</li>
 *   <li>{@code int invalidateAllWithReason(String reason)}</li>
 * </ul>
 */
public final class ScriptSystem implements KSystem, HotReloadableSystem {

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
        if (exports == null || exports.isNull()) {
            throw new IllegalStateException("Script module exports is null");
        }

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

    private static Object callForState(Value obj, String member, Object... args) {
        if (obj == null || obj.isNull()) return null;
        if (!obj.hasMember(member)) return null;
        Value fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) return null;
        try {
            return StateCapsule.toState(fn.execute(args));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object snapshotState(Value obj) {
        if (obj == null || obj.isNull() || !obj.hasMember("state")) return null;
        try {
            return StateCapsule.toState(obj.getMember("state"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void callOnError(Value obj, Throwable t) {
        if (obj == null || obj.isNull()) return;
        if (!obj.hasMember("onError")) return;
        Value fn = obj.getMember("onError");
        if (fn == null || fn.isNull() || !fn.canExecute()) return;
        try {
            fn.execute(String.valueOf(t));
        } catch (Throwable ignored) {
        }
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
        ScriptRuntime rt = this.runtime;
        if (rt == null) return;

        pumpHotReload(rt, tpf);

        Map<Integer, ScriptComponent> scripts = ecs.components().view(ScriptComponent.class);
        if (scripts.isEmpty()) return;

        for (var e : scripts.entrySet()) {
            int entityId = e.getKey();
            ScriptComponent sc = e.getValue();
            if (sc == null || sc.assetPath == null) continue;

            String uuid = ecs.uuids().uuidStringOf(entityId);
            if (uuid == null || uuid.isBlank()) {
                if (log.isDebugEnabled()) {
                    log.debug("ScriptSystem skipped script without uuid entityId={}", entityId);
                }
                continue;
            }

            try {
                ensureStarted(rt, uuid, entityId, sc);
            } catch (Throwable t) {
                log.error("Script ensureStarted failed entityId={} uuid={}", entityId, uuid, t);
                callOnError(sc != null ? sc.instance : null, t);
                continue;
            }

            try {
                callIfExists(sc.instance, "update", tpf);
                Object state = snapshotState(sc.instance);
                if (state != null) sc.stateCapsule = state;
            } catch (Throwable t) {
                log.error("Script update failed entityId={} uuid={}", entityId, uuid, t);
                callOnError(sc.instance, t);
            }
        }
    }

    @Override
    public void onStop(SystemContext systemContext) {
        destroyAllInstances();

        if (watcher != null) {
            try {
                watcher.close();
            } catch (Throwable t) {
                log.warn("ScriptSystem watcher.close failed", t);
            }
            watcher = null;
        }

        app = null;
        bus = null;
        runtime = null;

        log.info("ScriptSystem stopped");
    }

    @Override
    public void onHotReload(SystemContext ctx, String reason) {
        try {
            destroyAllInstances();

            if (runtime == null) runtime = ctx.runtime();
            ScriptRuntime rt = runtime;
            if (rt != null) {
                String r = (reason != null && !reason.isBlank()) ? reason : "F5";
                int removed = rt.invalidateAllWithReason(r);
                log.info("HotReload: ScriptRuntime invalidatedAll removedFromCache={}", removed);
            } else {
                log.warn("HotReload: ScriptRuntime is null");
            }

            if (bus != null) {
                try {
                    bus.emit("hotreload:force", (reason != null) ? reason : "F5");
                } catch (Throwable t) {
                    log.error("HotReload: bus emit failed", t);
                }
            }
        } catch (Throwable t) {
            log.warn("HotReload failed in ScriptSystem", t);
        }
    }

    private void pumpHotReload(ScriptRuntime rt, float tpf) {
        if (!hotReload || watcher == null) return;

        cooldown -= tpf;
        if (cooldown > 0f) return;

        Set<String> changed;
        try {
            changed = watcher.pollChanged();
        } catch (Throwable t) {
            log.error("HotReload watcher.pollChanged failed", t);
            cooldown = cooldownSec;
            return;
        }

        if (changed == null || changed.isEmpty()) {
            cooldown = 0f;
            return;
        }

        cooldown = cooldownSec;

        int removed = 0;
        try {
            removed = rt.invalidateManyWithReason(changed, "hotReload");
        } catch (Throwable t) {
            log.error("HotReload invalidateManyWithReason failed changed={}", changed.size(), t);
        }

        if (log.isDebugEnabled()) {
            log.debug("HotReload: changed={}, removedFromCache={}", changed.size(), removed);
        }

        if (bus != null) {
            try {
                bus.emit("hotreload:changed", changed);
            } catch (Throwable t) {
                log.error("HotReload bus emit failed", t);
            }
        }
    }

    private void destroyAllInstances() {
        try {
            var scripts = ecs.components().view(ScriptComponent.class);
            for (var e : scripts.entrySet()) {
                ScriptComponent sc = e.getValue();
                if (sc == null) continue;

                if (sc.instance != null) {
                    try {
                        Object state = snapshotState(sc.instance);
                        if (state != null) sc.stateCapsule = state;
                        if (sc.instance.hasMember("onStop")) {
                            callIfExists(sc.instance, "onStop", "stop");
                        } else {
                            callIfExists(sc.instance, "destroy");
                        }
                    } catch (org.graalvm.polyglot.PolyglotException pe) {
                        // Cancelled context is acceptable during shutdown.
                    } catch (Throwable t) {
                        log.warn("Script destroy failed for entity {}", e.getKey(), t);
                        callOnError(sc.instance, t);
                    }
                }

                sc.instance = null;
                sc.moduleVersion = 0L;
            }
        } catch (Throwable t) {
            log.warn("destroyAllInstances encountered errors", t);
        }
    }

    private void ensureStarted(ScriptRuntime rt, String uuid, int entityId, ScriptComponent sc) {
        String moduleId = (sc.moduleId != null && !sc.moduleId.isBlank())
                ? sc.moduleId
                : normalize(sc.assetPath);

        long v;
        try {
            v = rt.moduleVersion(moduleId);
        } catch (Throwable t) {
            throw new IllegalStateException("runtime.moduleVersion failed for moduleId=" + moduleId, t);
        }

        boolean needsStart = (sc.instance == null) || (sc.moduleVersion != v);
        if (!needsStart) return;

        Object prevState = sc.stateCapsule;
        if (sc.instance != null) {
            try {
                Object state = snapshotState(sc.instance);
                if (state != null) prevState = state;
                if (sc.instance.hasMember("onStop")) {
                    callIfExists(sc.instance, "onStop", "reload");
                } else {
                    callIfExists(sc.instance, "destroy");
                }
            } catch (Throwable t) {
                log.warn("Script destroy (reload) failed entityId={} uuid={}", entityId, uuid, t);
                callOnError(sc.instance, t);
            }
            sc.instance = null;
        }

        Value exports;
        try {
            exports = rt.require(moduleId);
        } catch (Throwable t) {
            throw new IllegalStateException("runtime.require failed for moduleId=" + moduleId, t);
        }

        Value instance;
        try {
            instance = createInstance(exports);
        } catch (Throwable t) {
            throw new IllegalStateException("createInstance failed for moduleId=" + moduleId, t);
        }

        sc.instance = instance;
        sc.moduleVersion = v;

        EntityScriptAPI api = new EntityScriptAPI(uuid, ecs, bus);
        try {
            Object onLoadState = null;
            if (sc.instance.hasMember("onLoad")) {
                onLoadState = callForState(sc.instance, "onLoad", api);
            } else if (sc.instance.hasMember("init")) {
                callIfExists(sc.instance, "init", api);
            }

            if (onLoadState != null) sc.stateCapsule = onLoadState;
            if (prevState != null && sc.instance.hasMember("onReload")) {
                Object reloaded = callForState(sc.instance, "onReload", prevState);
                if (reloaded != null) sc.stateCapsule = reloaded;
            } else if (prevState != null) {
                sc.stateCapsule = prevState;
            }

            if (sc.instance.hasMember("onStart")) {
                callIfExists(sc.instance, "onStart");
            }
        } catch (Throwable t) {
            log.error("Script init failed entityId={} uuid={} moduleId={}", entityId, uuid, moduleId, t);
            callOnError(sc.instance, t);
        }
    }
}
