// FILE: org/foxesworld/kalitech/engine/world/systems/ScriptSystem.java
package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.ScriptComponent;
import org.foxesworld.kalitech.engine.script.EntityScriptAPI;
import org.foxesworld.kalitech.engine.script.ScriptEntryPoint;
import org.foxesworld.kalitech.engine.script.ScriptFailureBoundary;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.foxesworld.kalitech.engine.script.util.StateCapsule;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

import java.nio.file.Path;
import java.util.HashSet;
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
 *   <li>{@code LuaValueRef require(String moduleId)}</li>
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
    private final Path projectOwnedRoot;
    private final ScriptEntryPoint entryPoint;

    private SimpleApplication app;
    private ScriptEventBus bus;   // optional
    private ScriptRuntime runtime;

    private HotReloadWatcher watcher;
    private float cooldown = 0f;

    public ScriptSystem(
            EcsWorld ecs,
            boolean hotReload,
            float cooldownSec,
            Path projectOwnedRoot,
            ScriptEntryPoint entryPoint
    ) {
        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.hotReload = hotReload;
        this.cooldownSec = (cooldownSec <= 0f) ? 0.25f : cooldownSec;
        this.projectOwnedRoot = Objects.requireNonNull(projectOwnedRoot, "projectOwnedRoot");
        this.entryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
    }

    private static LuaValueRef createInstance(LuaValueRef moduleValue) {
        if (moduleValue == null || moduleValue.isNull()) {
            throw new IllegalStateException("Lua module returned nil");
        }

        if (moduleValue.canExecute()) return moduleValue.executeLifecycle("entity.create");

        if (moduleValue.hasMember("create")) {
            LuaValueRef c = moduleValue.getMember("create");
            if (c != null && c.canExecute()) return moduleValue.invokeMemberLifecycle("entity.create", "create");
        }

        return moduleValue;
    }

    private static void callIfExists(LuaValueRef obj, String member, Object... args) {
        if (obj == null || obj.isNull() || !obj.hasMember(member)) return;
        LuaValueRef fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) return;
        obj.invokeMember(member, args);
    }

    private static Object callForState(LuaValueRef obj, String member, Object... args) {
        if (obj == null || obj.isNull() || !obj.hasMember(member)) return null;
        LuaValueRef fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) return null;
        return StateCapsule.toState(obj.invokeMember(member, args));
    }

    private static void callLifecycleIfExists(LuaValueRef obj, String member, Object... args) {
        if (obj == null || obj.isNull() || !obj.hasMember(member)) return;
        LuaValueRef fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) return;
        obj.invokeMemberLifecycle("entity." + member, member, args);
    }

    private static Object callLifecycleForState(LuaValueRef obj, String member, Object... args) {
        if (obj == null || obj.isNull() || !obj.hasMember(member)) return null;
        LuaValueRef fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) return null;
        return StateCapsule.toState(
                obj.invokeMemberLifecycle("entity." + member, member, args)
        );
    }

    private static Object snapshotState(LuaValueRef obj) {
        if (obj == null || obj.isNull() || !obj.hasMember("state")) return null;
        try {
            return StateCapsule.toState(obj.getMember("state"));
        } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
            return null;
        }
    }

    private static void callOnError(LuaValueRef obj, Throwable t) {
        if (obj == null || obj.isNull()) return;
        if (!obj.hasMember("onError")) return;
        LuaValueRef fn = obj.getMember("onError");
        if (fn == null || fn.isNull() || !fn.canExecute()) return;
        try {
            obj.invokeMember("onError", ScriptFailureBoundary.summary(t));
        } catch (Throwable hookFailure) {
            ScriptFailureBoundary.rethrowIfFatal(hookFailure);
        }
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
                this.watcher = new HotReloadWatcher(projectOwnedRoot);
                log.info("ScriptSystem hotReload enabled (root={}, cooldown={}s)", projectOwnedRoot.toAbsolutePath(), cooldownSec);
            } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                log.warn("ScriptSystem hotReload failed to start watcher at {}", projectOwnedRoot.toAbsolutePath(), t);
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
            if (sc == null || sc.moduleId == null || sc.moduleId.isBlank()) continue;

            String uuid = ecs.uuids().uuidStringOf(entityId);
            if (uuid == null || uuid.isBlank()) {
                if (log.isDebugEnabled()) {
                    log.debug("ScriptSystem skipped script without uuid entityId={}", entityId);
                }
                continue;
            }

            String moduleId = sc.moduleId;

            long version;
            try {
                version = rt.moduleVersion(moduleId);
            } catch (Throwable failure) {
                ScriptFailureBoundary.rethrowIfFatal(failure);
                quarantineEntity(sc, entityId, uuid, moduleId, sc.moduleVersion, "version", failure);
                continue;
            }

            if (sc.quarantined) {
                if (version == sc.quarantineVersion) continue;
                clearQuarantine(sc);
                log.info("Entity Lua script retrying after module change entityId={} uuid={} module={}",
                        entityId, uuid, moduleId);
            }

            try {
                ensureStarted(rt, uuid, entityId, sc, moduleId, version);
            } catch (Throwable failure) {
                quarantineEntity(sc, entityId, uuid, moduleId, version, "start", failure);
                continue;
            }

            try {
                callIfExists(sc.instance, "update", tpf);
                Object state = snapshotState(sc.instance);
                if (state != null) sc.stateCapsule = state;
            } catch (Throwable failure) {
                quarantineEntity(sc, entityId, uuid, moduleId, version, "update", failure);
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
            ScriptFailureBoundary.rethrowIfFatal(t);
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
            ScriptFailureBoundary.rethrowIfFatal(t);
                    log.error("HotReload: bus emit failed", t);
                }
            }
        } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
            log.warn("HotReload failed in ScriptSystem", t);
        }
    }

    private void pumpHotReload(ScriptRuntime rt, float tpf) {
        if (!hotReload || watcher == null) return;

        cooldown -= tpf;
        if (cooldown > 0f) return;

        Set<String> changedProjectPaths;
        try {
            changedProjectPaths = watcher.pollChanged();
        } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
            log.error("HotReload watcher.pollChanged failed", t);
            cooldown = cooldownSec;
            return;
        }

        if (changedProjectPaths == null || changedProjectPaths.isEmpty()) {
            cooldown = 0f;
            return;
        }

        cooldown = cooldownSec;

        Set<String> changedModules = new HashSet<>();
        for (String projectPath : changedProjectPaths) {
            String moduleId = entryPoint.moduleIdForProjectPath(projectPath);
            if (moduleId != null) changedModules.add(moduleId);
        }
        if (changedModules.isEmpty()) return;

        int removed = 0;
        try {
            removed = rt.invalidateManyWithReason(changedModules, "hotReload");
        } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
            log.error("HotReload invalidateManyWithReason failed changed={}", changedModules.size(), t);
        }

        if (log.isDebugEnabled()) {
            log.debug("HotReload: changed={}, removedFromCache={}", changedModules.size(), removed);
        }

        if (bus != null) {
            try {
                bus.emit("hotreload:changed", Set.copyOf(changedModules));
            } catch (Throwable t) {
                ScriptFailureBoundary.rethrowIfFatal(t);
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
                            callLifecycleIfExists(sc.instance, "onStop", "stop");
                        } else {
                            callLifecycleIfExists(sc.instance, "destroy");
                        }
                    } catch (org.luaj.vm2.LuaError ignored) {
                        // Lua shutdown errors are non-fatal during teardown.
                    } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                        log.warn("Script destroy failed for entity {}", e.getKey(), t);
                        callOnError(sc.instance, t);
                    }
                }

                sc.instance = null;
                sc.moduleVersion = 0L;
                clearQuarantine(sc);
            }
        } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
            log.warn("destroyAllInstances encountered errors", t);
        }
    }

    private static void clearQuarantine(ScriptComponent sc) {
        sc.quarantined = false;
        sc.quarantineVersion = 0L;
        sc.quarantineReason = null;
    }

    private void quarantineEntity(
            ScriptComponent sc,
            int entityId,
            String uuid,
            String moduleId,
            long version,
            String callback,
            Throwable failure
    ) {
        ScriptFailureBoundary.rethrowIfFatal(failure);
        if (sc.quarantined && sc.quarantineVersion == version) return;

        sc.quarantined = true;
        sc.quarantineVersion = version;
        sc.quarantineReason = ScriptFailureBoundary.summary(failure);

        log.error("Entity Lua script quarantined entityId={} uuid={} module={} callback={}; "
                        + "world and engine remain active; recovery=module change",
                entityId, uuid, moduleId, callback, failure);

        callOnError(sc.instance, failure);
        sc.instance = null;
    }

    private void ensureStarted(
            ScriptRuntime rt,
            String uuid,
            int entityId,
            ScriptComponent sc,
            String moduleId,
            long version
    ) {
        boolean needsStart = (sc.instance == null) || (sc.moduleVersion != version);
        if (!needsStart) return;

        Object prevState = sc.stateCapsule;
        if (sc.instance != null) {
            try {
                Object state = snapshotState(sc.instance);
                if (state != null) prevState = state;
                if (sc.instance.hasMember("onStop")) {
                    callLifecycleIfExists(sc.instance, "onStop", "reload");
                } else {
                    callLifecycleIfExists(sc.instance, "destroy");
                }
            } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                log.warn("Script destroy (reload) failed entityId={} uuid={}", entityId, uuid, t);
                callOnError(sc.instance, t);
            }
            sc.instance = null;
        }

        LuaValueRef moduleValue;
        try {
            moduleValue = rt.require(moduleId);
        } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
            throw new IllegalStateException("runtime.require failed for moduleId=" + moduleId, t);
        }

        LuaValueRef instance;
        try {
            instance = createInstance(moduleValue);
        } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
            throw new IllegalStateException("createInstance failed for moduleId=" + moduleId, t);
        }

        sc.instance = instance;
        sc.moduleVersion = version;

        EntityScriptAPI api = new EntityScriptAPI(uuid, ecs, bus);
        try {
            Object onLoadState = null;
            if (sc.instance.hasMember("onLoad")) {
                onLoadState = callLifecycleForState(sc.instance, "onLoad", api);
            } else if (sc.instance.hasMember("init")) {
                callLifecycleIfExists(sc.instance, "init", api);
            }

            if (onLoadState != null) sc.stateCapsule = onLoadState;
            if (prevState != null && sc.instance.hasMember("onReload")) {
                Object reloaded = callLifecycleForState(sc.instance, "onReload", prevState);
                if (reloaded != null) sc.stateCapsule = reloaded;
            } else if (prevState != null) {
                sc.stateCapsule = prevState;
            }

            if (sc.instance.hasMember("onStart")) {
                callLifecycleIfExists(sc.instance, "onStart");
            }
        } catch (Throwable failure) {
            throw new IllegalStateException(
                    "Lua lifecycle failed entityId=" + entityId + " uuid=" + uuid
                            + " moduleId=" + moduleId,
                    failure);
        }
    }
}
