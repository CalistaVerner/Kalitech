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
import org.foxesworld.kalitech.engine.script.profiler.ScriptProfiler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ScriptLifecycle {

    private static final Logger log = LogManager.getLogger(ScriptLifecycle.class);

    private static final int MAX_CRASH_STREAK = 3;
    private static final long DISABLE_FOR_NANOS = 5_000_000_000L; // 5s
    private static final long MIN_LOG_INTERVAL_NANOS = 1_000_000_000L; // 1s

    private final EcsWorld ecs;
    private final SimpleApplication app;
    private final ScriptEventBus events;
    private final GraalScriptRuntime runtime;
    private final ScriptProfiler profiler;

    private final Map<Integer, Breaker> breaker = new HashMap<>();
    private final Map<Integer, Value> pendingRestore = new HashMap<>();

    private static final class Breaker {
        int crashStreak;
        long disabledUntilNanos;
        long lastLogNanos;
    }

    public ScriptLifecycle(EcsWorld ecs,
                           SimpleApplication app,
                           ScriptEventBus events,
                           GraalScriptRuntime runtime,
                           ScriptProfiler profiler) {
        this.ecs = ecs;
        this.app = app;
        this.events = events;
        this.runtime = runtime;
        this.profiler = profiler;
    }

    public void update(float tpf) {
        final long now = System.nanoTime();

        ecs.components().forEach(ScriptComponent.class, (entityId, sc) -> {
            if (sc == null) return;

            Breaker b = breaker.computeIfAbsent(entityId, k -> new Breaker());
            if (b.disabledUntilNanos > now) return;

            if (!ensureStarted(entityId, sc, b, now)) return;

            long t0 = profiler != null ? profiler.begin() : 0L;
            boolean ok = safeCall(entityId, sc, b, now, "update", tpf);
            if (profiler != null && t0 != 0L) profiler.endModule(sc.moduleId, ScriptProfiler.Phase.UPDATE, t0, ok);

            if (ok) b.crashStreak = 0;
            else onCrash(entityId, sc, b, now);
        });
    }

    public void reset() {
        ecs.components().forEach(ScriptComponent.class, (entityId, sc) -> {
            if (sc == null) return;
            destroyInstance(entityId, sc, "reset", false);
            sc.instance = null;
            sc.moduleVersion = 0L;
        });
        breaker.clear();
        pendingRestore.clear();
    }

    public void onHotReloadChanged(Set<String> changedModuleIds) {
        if (changedModuleIds == null || changedModuleIds.isEmpty()) return;

        int removed = runtime.invalidateMany(changedModuleIds);
        log.info("HotReload: invalidated {} modules (removed from cache={})", changedModuleIds.size(), removed);

        ecs.components().forEach(ScriptComponent.class, (entityId, sc) -> {
            if (sc == null) return;
            if (!changedModuleIds.contains(sc.moduleId)) return;

            Value state = destroyInstance(entityId, sc, "hotReload", true);
            if (state != null && !state.isNull()) pendingRestore.put(entityId, state);

            sc.instance = null;
            sc.moduleVersion = 0L;
        });
    }

    private boolean ensureStarted(int entityId, ScriptComponent sc, Breaker b, long nowNanos) {
        final String moduleId = sc.moduleId;
        final long v = runtime.moduleVersion(moduleId);

        if (sc.instance != null && sc.moduleVersion == v) return true;

        Value restore = pendingRestore.remove(entityId);

        if (sc.instance != null) {
            Value state = destroyInstance(entityId, sc, "rebind", true);
            if ((restore == null || restore.isNull()) && state != null && !state.isNull()) restore = state;
            sc.instance = null;
        }

        try {
            Value exports = runtime.require(moduleId);
            Value instance = createInstance(exports);

            sc.instance = instance;
            sc.moduleVersion = v;

            EntityScriptAPI api = new EntityScriptAPI(entityId, ecs, app, events);

            long t0 = profiler != null ? profiler.begin() : 0L;
            boolean okInit = safeCall(entityId, sc, b, nowNanos, "init", api);
            if (profiler != null && t0 != 0L) profiler.endModule(sc.moduleId, ScriptProfiler.Phase.INIT, t0, okInit);

            if (restore != null && !restore.isNull()) {
                safeCall(entityId, sc, b, nowNanos, "deserialize", restore);
            }

            return true;

        } catch (Throwable t) {
            rateLimitedError(entityId, sc, b, nowNanos, "Script start failed: " + moduleId, t);
            onCrash(entityId, sc, b, nowNanos);
            return false;
        }
    }

    private Value destroyInstance(int entityId, ScriptComponent sc, String reason, boolean allowSerialize) {
        Value inst = sc.instance;
        if (inst == null || inst.isNull()) return null;

        Value state = null;

        if (allowSerialize) {
            try {
                long t0 = profiler != null ? profiler.begin() : 0L;
                state = callIfExistsReturn(inst, "serialize");
                if (profiler != null && t0 != 0L) profiler.endModule(sc.moduleId, ScriptProfiler.Phase.SERIALIZE, t0, true);
            } catch (Throwable t) {
                log.debug("serialize() failed for entity={} module={}", entityId, sc.moduleId, t);
            }
        }

        try {
            if (inst.hasMember("destroy")) {
                Value fn = inst.getMember("destroy");
                if (fn != null && fn.canExecute()) {
                    long t0 = profiler != null ? profiler.begin() : 0L;
                    fn.execute(reason);
                    if (profiler != null && t0 != 0L) profiler.endModule(sc.moduleId, ScriptProfiler.Phase.DESTROY, t0, true);
                }
            }
        } catch (Throwable t) {
            log.debug("destroy() failed for entity={} module={}", entityId, sc.moduleId, t);
        }

        return state;
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

    private void onCrash(int entityId, ScriptComponent sc, Breaker b, long nowNanos) {
        b.crashStreak++;
        if (b.crashStreak >= MAX_CRASH_STREAK) {
            b.disabledUntilNanos = nowNanos + DISABLE_FOR_NANOS;
            b.crashStreak = 0;
            rateLimitedError(entityId, sc, b, nowNanos,
                    "Script disabled for " + (DISABLE_FOR_NANOS / 1_000_000_000L) + "s due to repeated crashes: " + sc.moduleId,
                    null);
        }
    }

    private boolean safeCall(int entityId, ScriptComponent sc, Breaker b, long nowNanos, String member, Object... args) {
        try {
            Value inst = sc.instance;
            if (inst == null || inst.isNull()) return true;
            if (!inst.hasMember(member)) return true;

            Value fn = inst.getMember(member);
            if (fn == null || fn.isNull() || !fn.canExecute()) return true;

            fn.execute(args);
            return true;

        } catch (Throwable t) {
            rateLimitedError(entityId, sc, b, nowNanos, "Script call failed: " + member + "()", t);
            if (profiler != null) profiler.endModule(sc.moduleId, ScriptProfiler.Phase.UPDATE, profiler.begin(), false); // lightweight marker
            return false;
        }
    }

    private static Value callIfExistsReturn(Value obj, String member, Object... args) {
        if (obj == null || obj.isNull()) return null;
        if (!obj.hasMember(member)) return null;
        Value fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) return null;
        Value v = fn.execute(args);
        return (v == null || v.isNull()) ? null : v;
    }

    private void rateLimitedError(int entityId, ScriptComponent sc, Breaker b, long nowNanos, String msg, Throwable t) {
        if (nowNanos - b.lastLogNanos < MIN_LOG_INTERVAL_NANOS) return;
        b.lastLogNanos = nowNanos;

        if (t != null) log.error("{} (entity={}, module={})", msg, entityId, sc.moduleId, t);
        else log.warn("{} (entity={}, module={})", msg, entityId, sc.moduleId);
    }
}