// FILE: ScriptLifecycle.java
package org.foxesworld.kalitech.engine.script.lifecycle;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.ScriptComponent;
import org.foxesworld.kalitech.engine.script.EntityScriptAPI;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Set;

/**
 * ScriptLifecycle:
 * - ensure instance exists and is fresh (by moduleVersion)
 * - call init/update/destroy if present
 * - destroy on reset + onEntityRemoved
 * - hot reload: invalidate modules and restart only affected entities
 *
 * <p>Hot path is allocation-free: uses ComponentStore.forEach() instead of view().
 */
public final class ScriptLifecycle {

    private static final Logger log = LogManager.getLogger(ScriptLifecycle.class);

    private final EcsWorld ecs;
    private final SimpleApplication app;
    private final ScriptEventBus events;
    private final GraalScriptRuntime runtime;

    public ScriptLifecycle(EcsWorld ecs, SimpleApplication app, ScriptEventBus events, GraalScriptRuntime runtime) {
        this.ecs = ecs;
        this.app = app;
        this.events = events;
        this.runtime = runtime;
    }

    /** Call every frame from your ScriptSystem / WorldSystem. */
    public void update(float tpf) {
        ecs.components().forEach(ScriptComponent.class, (entityId, sc) -> {
            if (sc == null) return;
            ensureStarted(entityId, sc);
            safeCall(sc.instance, "update", tpf);
        });
    }

    /** Call when entity is destroyed (or before ecs.destroyEntity()). Safe to call multiple times. */
    public void onEntityRemoved(int entityId) {
        ScriptComponent sc = ecs.components().get(entityId, ScriptComponent.class);
        if (sc == null) return;
        destroyInstance(entityId, sc, true);
        sc.instance = null;
        sc.moduleVersion = 0L;
    }

    /** Destroy all script instances (used on world rebuild / reload). */
    public void reset() {
        ecs.components().forEach(ScriptComponent.class, (entityId, sc) -> {
            if (sc == null) return;
            destroyInstance(entityId, sc, true);
            sc.instance = null;
            sc.moduleVersion = 0L;
        });
    }

    /** Hot reload: invalidate modules and restart only affected entities. */
    public void onHotReloadChanged(Set<String> changedModuleIds) {
        if (changedModuleIds == null || changedModuleIds.isEmpty()) return;

        int removed = runtime.invalidateMany(changedModuleIds);
        log.info("HotReload: invalidated {} modules (removed from cache={})", changedModuleIds.size(), removed);

        ecs.components().forEach(ScriptComponent.class, (entityId, sc) -> {
            if (sc == null) return;
            if (!changedModuleIds.contains(sc.moduleId)) return;

            destroyInstance(entityId, sc, true);
            sc.instance = null;
            sc.moduleVersion = 0L;
        });
    }

    // ---------------- internals ----------------

    private void ensureStarted(int entityId, ScriptComponent sc) {
        final String moduleId = sc.moduleId;
        final long v = runtime.moduleVersion(moduleId);

        if (sc.instance != null && sc.moduleVersion == v) return;

        if (sc.instance != null) {
            destroyInstance(entityId, sc, true);
            sc.instance = null;
        }

        Value exports = runtime.require(moduleId);
        Value instance = createInstance(exports);

        sc.instance = instance;
        sc.moduleVersion = v;

        EntityScriptAPI api = new EntityScriptAPI(entityId, ecs, app, events);
        safeCall(sc.instance, "init", api);
    }

    private static Value createInstance(Value exports) {
        if (exports == null || exports.isNull()) throw new IllegalStateException("Script module exports is null");

        // Common pattern: module exports a factory function
        if (exports.canExecute()) return exports.execute();

        // Alternative: exports.create()
        if (exports.hasMember("create")) {
            Value c = exports.getMember("create");
            if (c != null && c.canExecute()) return c.execute();
        }

        // Otherwise treat exports as an instance
        return exports;
    }

    private void destroyInstance(int entityId, ScriptComponent sc, boolean cleanupOwner) {
        if (sc == null) return;

        // Critical: unsubscribe JS handlers captured as Value (prevents leaks + zombie calls)
        if (cleanupOwner) {
            events.offOwner(entityId);
        }

        if (sc.instance == null || sc.instance.isNull()) return;

        try {
            safeCall(sc.instance, "destroy");
        } catch (Throwable t) {
            log.warn("Script destroy failed for entity {}", entityId, t);
        }
    }

    private void safeCall(Value obj, String member, Object... args) {
        try {
            callIfExists(obj, member, args);
        } catch (Throwable t) {
            // fail-soft: script errors must not crash engine
            log.error("Script call failed: {}()", member, t);
        }
    }

    private static void callIfExists(Value obj, String member, Object... args) {
        if (obj == null || obj.isNull()) return;
        if (!obj.hasMember(member)) return;
        Value fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) return;
        fn.execute(args);
    }
}