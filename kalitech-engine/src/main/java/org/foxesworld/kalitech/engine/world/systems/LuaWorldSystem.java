// FILE: org/foxesworld/kalitech/engine/world/systems/LuaWorldSystem.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.ScriptFailureBoundary;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.script.util.StateCapsule;

import java.util.Objects;
import java.util.concurrent.Callable;

public final class LuaWorldSystem implements KSystem, HotReloadableSystem {

    private static final Logger log = LogManager.getLogger(LuaWorldSystem.class);

    private final String module;
    private final Object cfg;
    private final Object sysDesc;
    private final String runtimeProfile;

    private volatile LuaValueRef moduleValue;
    private volatile boolean needsInit = true;
    private volatile Object stateCapsule;
    private volatile Object pendingReloadState;
    private volatile boolean quarantined;
    private volatile String quarantineReason;

    public LuaWorldSystem(String module, Object cfg, Object sysDesc, String runtimeProfile) {
        this.module = Objects.requireNonNull(module, "module");
        this.cfg = cfg;
        this.sysDesc = sysDesc;
        this.runtimeProfile = (runtimeProfile == null || runtimeProfile.isBlank()) ? "world" : runtimeProfile.trim();
    }

    @Override
    public void onStart(SystemContext ctx) {
        if (quarantined) return;
        runGuarded(ctx, "onStart", () -> {
            ensureLoaded(ctx);
            if (needsInit) {
                Object loadState = null;
                if (moduleValue.hasMember("onLoad")) {
                    loadState = invokeLifecycleForState("onLoad", ctx);
                } else {
                    invokeLifecycleIfPresent("init", ctx);
                }

                if (loadState != null) stateCapsule = loadState;

                Object prevState = pendingReloadState;
                if (prevState != null && moduleValue.hasMember("onReload")) {
                    Object reloaded = invokeLifecycleForState("onReload", prevState);
                    stateCapsule = reloaded != null ? reloaded : prevState;
                }

                pendingReloadState = null;
                needsInit = false;
            }

            invokeLifecycleIfPresent("onStart");
            return null;
        });
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        if (quarantined) return;
        runGuarded(ctx, "onUpdate", () -> {
            ensureLoaded(ctx);

            if (needsInit) {
                if (moduleValue.hasMember("onLoad")) {
                    Object loadState = invokeLifecycleForState("onLoad", ctx);
                    if (loadState != null) stateCapsule = loadState;
                } else {
                    invokeLifecycleIfPresent("init", ctx);
                }
                needsInit = false;
            }

            invokeIfPresent("update", ctx, tpf);
            Object state = snapshotState();
            if (state != null) stateCapsule = state;
            return null;
        });
    }

    @Override
    public void onStop(SystemContext ctx) {
        if (moduleValue == null || quarantined) return;
        runGuarded(ctx, "onStop", () -> {
            Object state = snapshotState();
            if (state != null) stateCapsule = state;
            if (moduleValue.hasMember("onStop")) {
                invokeLifecycleIfPresent("onStop", "stop");
            } else {
                invokeLifecycleIfPresent("destroy", ctx);
            }
            return null;
        });
    }

    @Override
    public void onHotReload(SystemContext ctx, String reason) {
        final String why = (reason == null || reason.isBlank()) ? "F5" : reason;

        try {
            withScopedSystem(ctx, () -> {
                if (!quarantined) {
                    try {
                        Object state = snapshotState();
                        if (state != null) stateCapsule = state;
                        if (moduleValue != null && moduleValue.hasMember("onStop")) {
                            invokeLifecycleIfPresent("onStop", why);
                        } else {
                            invokeLifecycleIfPresent("destroy", ctx);
                        }
                    } catch (Throwable failure) {
                        ScriptFailureBoundary.rethrowIfFatal(failure);
                        log.debug("[LuaWorldSystem] teardown during reload failed module={} (ignored): {}",
                                module, failure.toString());
                    }
                }

                ScriptRuntime rt = pickRuntime(ctx);
                if (rt != null) {
                    try {
                        rt.invalidateAllWithReason(why);
                    } catch (Throwable failure) {
                        ScriptFailureBoundary.rethrowIfFatal(failure);
                        log.warn("[LuaWorldSystem] invalidation failed profile={} module={}",
                                runtimeProfile, module, failure);
                    }
                }

                moduleValue = null;
                needsInit = true;
                pendingReloadState = stateCapsule;
                quarantined = false;
                quarantineReason = null;

                log.info("[LuaWorldSystem] reload enabled module={} reason={}", module, why);
                return null;
            });
        } catch (Throwable failure) {
            ScriptFailureBoundary.rethrowIfFatal(failure);
            log.warn("[LuaWorldSystem] reload failed module={}", module, failure);
            moduleValue = null;
            needsInit = true;
            quarantined = false;
            quarantineReason = null;
        }
    }

    public boolean isQuarantined() {
        return quarantined;
    }

    public String quarantineReason() {
        return quarantineReason;
    }

    private void runGuarded(SystemContext ctx, String callback, Callable<Void> call) {
        try {
            withScopedSystem(ctx, call);
        } catch (Throwable failure) {
            quarantine(callback, failure);
        }
    }

    private void quarantine(String callback, Throwable failure) {
        ScriptFailureBoundary.rethrowIfFatal(failure);
        if (quarantined) return;

        quarantined = true;
        needsInit = false;
        quarantineReason = ScriptFailureBoundary.summary(failure);

        log.error("[LuaWorldSystem] script quarantined module={} callback={}; "
                        + "world and engine remain active; recovery=hot reload",
                module, callback, failure);

        LuaValueRef value = moduleValue;
        if (value == null || value.isNull() || !value.hasMember("onError")) return;
        LuaValueRef hook = value.getMember("onError");
        if (hook == null || hook.isNull() || !hook.canExecute()) return;

        try {
            value.invokeMember("onError", quarantineReason);
        } catch (Throwable hookFailure) {
            ScriptFailureBoundary.rethrowIfFatal(hookFailure);
            log.warn("[LuaWorldSystem] onError failed in quarantined module={}: {}",
                    module, hookFailure.toString());
        }
    }

    private ScriptRuntime pickRuntime(SystemContext ctx) {
        ScriptRuntime rt = ctx.runtime(runtimeProfile);
        if (rt == null) rt = ctx.runtime();
        return rt;
    }

    private <T> T withScopedSystem(SystemContext ctx, Callable<T> call) {
        Objects.requireNonNull(ctx, "ctx");

        final boolean hadConfig = ctx.has("config");
        final boolean hadCfg = ctx.has("cfg");
        final boolean hadSystem = ctx.has("system");

        final Object prevConfig = hadConfig ? ctx.get("config") : null;
        final Object prevCfg = hadCfg ? ctx.get("cfg") : null;
        final Object prevSystem = hadSystem ? ctx.get("system") : null;

        ctx.put("config", cfg);
        ctx.put("cfg", cfg);
        ctx.put("system", sysDesc);

        try {
            return call.call();
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new RuntimeException("[LuaWorldSystem] failure module=" + module, failure);
        } finally {
            if (hadConfig) ctx.put("config", prevConfig);
            else ctx.remove("config");

            if (hadCfg) ctx.put("cfg", prevCfg);
            else ctx.remove("cfg");

            if (hadSystem) ctx.put("system", prevSystem);
            else ctx.remove("system");
        }
    }

    private void ensureLoaded(SystemContext ctx) {
        if (moduleValue != null) return;

        synchronized (this) {
            if (moduleValue != null) return;

            ScriptRuntime rt = pickRuntime(ctx);
            if (rt == null) {
                throw new IllegalStateException("LuaWorldSystem requires ScriptRuntime in SystemContext");
            }

            moduleValue = rt.require(module);
            if (moduleValue == null || moduleValue.isNull()) {
                throw new IllegalStateException("LuaWorldSystem module returned nil. module=" + module);
            }

            if (log.isDebugEnabled()) {
                try {
                    log.debug("[LuaWorldSystem] loaded module={} keys={}", module, moduleValue.getMemberKeys());
                } catch (Throwable failure) {
                    ScriptFailureBoundary.rethrowIfFatal(failure);
                    log.debug("[LuaWorldSystem] loaded module={}", module);
                }
            }
        }
    }

    private void invokeIfPresent(String fnName, Object... args) {
        final LuaValueRef value = moduleValue;
        if (value == null || value.isNull() || !value.hasMember(fnName)) return;

        final LuaValueRef fn = value.getMember(fnName);
        if (fn == null || fn.isNull() || !fn.canExecute()) return;
        value.invokeMember(fnName, args);
    }

    private Object invokeForState(String fnName, Object... args) {
        final LuaValueRef value = moduleValue;
        if (value == null || value.isNull() || !value.hasMember(fnName)) return null;

        final LuaValueRef fn = value.getMember(fnName);
        if (fn == null || fn.isNull() || !fn.canExecute()) return null;
        return StateCapsule.toState(value.invokeMember(fnName, args));
    }

    private void invokeLifecycleIfPresent(String fnName, Object... args) {
        final LuaValueRef value = moduleValue;
        if (value == null || value.isNull() || !value.hasMember(fnName)) return;

        final LuaValueRef fn = value.getMember(fnName);
        if (fn == null || fn.isNull() || !fn.canExecute()) return;
        value.invokeMemberLifecycle(module + ":" + fnName, fnName, args);
    }

    private Object invokeLifecycleForState(String fnName, Object... args) {
        final LuaValueRef value = moduleValue;
        if (value == null || value.isNull() || !value.hasMember(fnName)) return null;

        final LuaValueRef fn = value.getMember(fnName);
        if (fn == null || fn.isNull() || !fn.canExecute()) return null;
        return StateCapsule.toState(
                value.invokeMemberLifecycle(module + ":" + fnName, fnName, args)
        );
    }

    private Object snapshotState() {
        final LuaValueRef value = moduleValue;
        if (value == null || value.isNull() || !value.hasMember("state")) return null;
        try {
            return StateCapsule.toState(value.getMember("state"));
        } catch (Throwable failure) {
            ScriptFailureBoundary.rethrowIfFatal(failure);
            return null;
        }
    }
}
