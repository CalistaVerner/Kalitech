// FILE: org/foxesworld/kalitech/engine/world/systems/JsWorldSystem.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.util.StateCapsule;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.Callable;

public final class JsWorldSystem implements KSystem, HotReloadableSystem {

    private static final Logger log = LogManager.getLogger(JsWorldSystem.class);

    private final String module;
    private final Object cfg;
    private final Object sysDesc;
    private final String runtimeProfile;

    private volatile Value exports;
    private volatile boolean needsInit = true;
    private volatile Object stateCapsule;
    private volatile Object pendingReloadState;

    public JsWorldSystem(String module, Object cfg, Object sysDesc, String runtimeProfile) {
        this.module = Objects.requireNonNull(module, "module");
        this.cfg = cfg;
        this.sysDesc = sysDesc;
        this.runtimeProfile = (runtimeProfile == null || runtimeProfile.isBlank()) ? "world" : runtimeProfile.trim();
    }

    @Override
    public void onStart(SystemContext ctx) {
        withScopedSystem(ctx, () -> {
            ensureLoaded(ctx);
            if (needsInit) {
                Object loadState = null;
                if (exports.hasMember("onLoad")) {
                    loadState = invokeForState("onLoad", ctx);
                } else {
                    invokeIfPresent("init", ctx);
                }

                if (loadState != null) stateCapsule = loadState;

                Object prevState = pendingReloadState;
                if (prevState != null && exports.hasMember("onReload")) {
                    Object reloaded = invokeForState("onReload", prevState);
                    if (reloaded != null) stateCapsule = reloaded;
                    else stateCapsule = prevState;
                }

                pendingReloadState = null;
                needsInit = false;
            }

            invokeIfPresent("onStart");
            needsInit = false;
            return null;
        });
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        withScopedSystem(ctx, () -> {
            ensureLoaded(ctx);

            if (needsInit) {
                if (exports.hasMember("onLoad")) {
                    Object loadState = invokeForState("onLoad", ctx);
                    if (loadState != null) stateCapsule = loadState;
                } else {
                    invokeIfPresent("init", ctx);
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
        withScopedSystem(ctx, () -> {
            Object state = snapshotState();
            if (state != null) stateCapsule = state;
            if (exports != null && exports.hasMember("onStop")) {
                invokeIfPresent("onStop", "stop");
            } else {
                invokeIfPresent("destroy", ctx);
            }
            return null;
        });
    }

    @Override
    public void onHotReload(SystemContext ctx, String reason) {
        final String why = (reason == null || reason.isBlank()) ? "F5" : reason;

        try {
            withScopedSystem(ctx, () -> {
                try {
                    Object state = snapshotState();
                    if (state != null) stateCapsule = state;
                    if (exports != null && exports.hasMember("onStop")) {
                        invokeIfPresent("onStop", why);
                    } else {
                        invokeIfPresent("destroy", ctx);
                    }
                } catch (Throwable t) {
                    log.debug("[JsWorldSystem] destroy during hotReload failed module={} (ignored): {}", module, t.toString());
                }

                ScriptRuntime rt = pickRuntime(ctx);
                if (rt != null) {
                    try {
                        rt.invalidateAllWithReason(why);
                    } catch (Throwable t) {
                        log.warn("[JsWorldSystem] invalidateAllWithReason failed profile={} module={}", runtimeProfile, module, t);
                    }
                }

                exports = null;
                needsInit = true;
                pendingReloadState = stateCapsule;

                log.info("[JsWorldSystem] HotReload({}) module={}", why, module);
                return null;
            });
        } catch (Throwable t) {
            log.warn("[JsWorldSystem] HotReload failed module={}", module, t);
            exports = null;
            needsInit = true;
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
        } catch (PolyglotException pe) {
            if (pe.isCancelled()) return null;
            throw pe;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("[JsWorldSystem] failure module=" + module + " err=" + e, e);
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
        if (exports != null) return;

        synchronized (this) {
            if (exports != null) return;

            ScriptRuntime rt = pickRuntime(ctx);
            if (rt == null) {
                throw new IllegalStateException("JsWorldSystem requires ScriptRuntime in SystemContext");
            }

            // No reflection: stable ScriptRuntime contract
            exports = rt.require(module);

            if (exports == null || exports.isNull()) {
                throw new IllegalStateException("JsWorldSystem module exports is null. module=" + module);
            }

            if (log.isDebugEnabled()) {
                try {
                    log.debug("[JsWorldSystem] loaded module={} exportsKeys={}", module, exports.getMemberKeys());
                } catch (Throwable ignored) {
                    log.debug("[JsWorldSystem] loaded module={}", module);
                }
            }
        }
    }

    private void invokeIfPresent(String fnName, Object... args) {
        final Value ex = exports;
        if (ex == null || ex.isNull() || !ex.hasMember(fnName)) return;

        final Value fn = ex.getMember(fnName);
        if (fn == null || fn.isNull() || !fn.canExecute()) return;

        try {
            fn.execute(args);
        } catch (PolyglotException pe) {
            if (pe.isCancelled()) return;
            invokeIfPresent("onError", String.valueOf(pe));
            throw new RuntimeException("[JsWorldSystem] js threw in " + fnName + " module=" + module + " err=" + pe.getMessage(), pe);
        }
    }

    private Object invokeForState(String fnName, Object... args) {
        final Value ex = exports;
        if (ex == null || ex.isNull() || !ex.hasMember(fnName)) return null;

        final Value fn = ex.getMember(fnName);
        if (fn == null || fn.isNull() || !fn.canExecute()) return null;

        try {
            return StateCapsule.toState(fn.execute(args));
        } catch (PolyglotException pe) {
            if (pe.isCancelled()) return null;
            invokeIfPresent("onError", String.valueOf(pe));
            throw new RuntimeException("[JsWorldSystem] js threw in " + fnName + " module=" + module + " err=" + pe.getMessage(), pe);
        }
    }

    private Object snapshotState() {
        final Value ex = exports;
        if (ex == null || ex.isNull() || !ex.hasMember("state")) return null;
        try {
            return StateCapsule.toState(ex.getMember("state"));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
