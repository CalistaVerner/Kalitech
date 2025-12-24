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

import java.util.Map;
import java.util.Set;

/**
 * Small, deterministic script lifecycle:
 * - ensure instance exists and is fresh (by moduleVersion)
 * - call init/update/destroy if present
 * - destroy on reset + onEntityRemoved
 * - hot reload: invalidate modules and restart only affected entities
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

    /**
     * Call every frame from your ScriptSystem / WorldSystem.
     */
    public void update(float tpf) {
        // Compatibility: your ComponentStore currently has view(Class)
        Map<Integer, ScriptComponent> scripts = ecs.components().view(ScriptComponent.class);
        if (scripts.isEmpty()) return;

        for (var e : scripts.entrySet()) {
            int entityId = e.getKey();
            ScriptComponent sc = e.getValue();
            if (sc == null) continue;

            ensureStarted(entityId, sc);
            callIfExists(sc.instance, "update", tpf);
        }
    }

    /**
     * Call when entity is destroyed (or before ecs.destroyEntity()).
     * Safe to call multiple times.
     */
    public void onEntityRemoved(int entityId) {
        ScriptComponent sc = ecs.components().get(entityId, ScriptComponent.class);
        if (sc == null) return;
        destroyInstance(entityId, sc);
        sc.instance = null;
        sc.moduleHash = null;
    }

    /**
     * Destroy all script instances (used on world rebuild / reload).
     */
    public void reset() {
        Map<Integer, ScriptComponent> scripts = ecs.components().view(ScriptComponent.class);
        for (var e : scripts.entrySet()) {
            destroyInstance(e.getKey(), e.getValue());
            e.getValue().instance = null;
            e.getValue().moduleHash = null;
        }
    }

    /**
     * Hot reload: invalidate changed module ids in runtime cache,
     * and restart only entities whose ScriptComponent.assetPath is in changed ids.
     */
    public void onHotReloadChanged(Set<String> changedModuleIds) {
        if (changedModuleIds == null || changedModuleIds.isEmpty()) return;

        // 1) runtime cache invalidation + version bump
        int removed = runtime.invalidateMany(changedModuleIds);
        log.info("HotReload: invalidated {} modules (removed from cache={})", changedModuleIds.size(), removed);

        // 2) restart only affected entities
        Map<Integer, ScriptComponent> scripts = ecs.components().view(ScriptComponent.class);
        for (var e : scripts.entrySet()) {
            int entityId = e.getKey();
            ScriptComponent sc = e.getValue();
            if (sc == null || sc.assetPath == null) continue;

            String id = normalize(sc.assetPath);
            if (!changedModuleIds.contains(id)) continue;

            // kill and mark for re-init next update
            destroyInstance(entityId, sc);
            sc.instance = null;
            sc.moduleHash = null;
        }
    }

    // -------------------- internals --------------------

    private void ensureStarted(int entityId, ScriptComponent sc) {
        String moduleId = normalize(sc.assetPath);
        long v = runtime.moduleVersion(moduleId);
        String vStr = Long.toString(v);

        boolean needsStart = (sc.instance == null) || (sc.moduleHash == null) || (!sc.moduleHash.equals(vStr));

        if (!needsStart) return;

        // if exists but outdated — destroy first
        if (sc.instance != null) {
            destroyInstance(entityId, sc);
            sc.instance = null;
        }

        Value exports = runtime.require(moduleId);
        Value instance = createInstance(exports);

        sc.instance = instance;
        sc.moduleHash = vStr;

        EntityScriptAPI api = new EntityScriptAPI(entityId, ecs, app, events);
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

        // exports is a factory function
        if (exports.canExecute()) {
            return exports.execute();
        }

        // exports.create() factory
        if (exports.hasMember("create")) {
            Value c = exports.getMember("create");
            if (c != null && c.canExecute()) {
                return c.execute();
            }
        }

        // exports is already an instance-like object
        return exports;
    }

    private static void destroyInstance(int entityId, ScriptComponent sc) {
        if (sc == null || sc.instance == null) return;
        try {
            callIfExists(sc.instance, "destroy");
        } catch (Exception ex) {
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