// FILE: org/foxesworld/kalitech/engine/world/systems/JsWorldSystem.java
package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

public final class JsWorldSystem implements KSystem, HotReloadableSystem {

    private static final Logger log = LogManager.getLogger(JsWorldSystem.class);

    private final String module;
    private final Object cfg;
    private final Object sysDesc;
    private final String runtimeProfile;

    private volatile Value exports;
    private volatile boolean needsInit = true;

    public JsWorldSystem(String module, Object cfg, Object sysDesc, String runtimeProfile) {
        this.module = Objects.requireNonNull(module, "module");
        this.cfg = cfg;
        this.sysDesc = sysDesc;
        this.runtimeProfile = normalizeProfile(runtimeProfile);
    }

    public JsWorldSystem(String module, Object cfg, Object sysDesc) {
        this(module, cfg, sysDesc, "world");
    }

    public JsWorldSystem(String module) {
        this(module, null, null, "world");
    }

    private static String normalizeProfile(String p) {
        if (p == null) return "world";
        final String t = p.trim();
        return t.isEmpty() ? "world" : t;
    }

    // ------------------------ ScriptRuntime reflection helpers ------------------------

    private static Value requireViaReflection(ScriptRuntime rt, String module) throws Exception {
        final Class<?> c = rt.getClass();

        Value v = tryInvokeValue(c, rt, "require", new Class<?>[]{String.class}, new Object[]{module});
        if (v != null) return v;

        v = tryInvokeValue(c, rt, "requireModule", new Class<?>[]{String.class}, new Object[]{module});
        if (v != null) return v;

        v = tryInvokeValue(c, rt, "loadModule", new Class<?>[]{String.class}, new Object[]{module});
        if (v != null) return v;

        v = tryInvokeValue(c, rt, "evalModule", new Class<?>[]{String.class}, new Object[]{module});
        if (v != null) return v;

        v = tryInvokeValue(c, rt, "evaluateModule", new Class<?>[]{String.class}, new Object[]{module});
        if (v != null) return v;

        throw new IllegalStateException("Cannot load module via ScriptRuntime reflection: " + module);
    }

    private static int invalidateManyViaReflection(ScriptRuntime rt, Set<String> modules, String reason) {
        final Class<?> c = rt.getClass();

        // invalidateManyWithReason(Set<String>, String)
        try {
            final Method m = c.getMethod("invalidateManyWithReason", Set.class, String.class);
            final Object r = m.invoke(rt, modules, reason);
            return (r instanceof Number n) ? n.intValue() : 0;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            throw new RuntimeException("runtime.invalidateManyWithReason failed: " + t, t);
        }

        // invalidateMany(Set<String>)
        try {
            final Method m = c.getMethod("invalidateMany", Set.class);
            final Object r = m.invoke(rt, modules);
            return (r instanceof Number n) ? n.intValue() : 0;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            throw new RuntimeException("runtime.invalidateMany failed: " + t, t);
        }

        return 0;
    }

    private static int invalidateAllViaReflection(ScriptRuntime rt, String reason) {
        final Class<?> c = rt.getClass();

        // invalidateAllWithReason(String)
        try {
            final Method m = c.getMethod("invalidateAllWithReason", String.class);
            final Object r = m.invoke(rt, reason);
            return (r instanceof Number n) ? n.intValue() : 0;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            throw new RuntimeException("runtime.invalidateAllWithReason failed: " + t, t);
        }

        // invalidateAll()
        try {
            final Method m = c.getMethod("invalidateAll");
            final Object r = m.invoke(rt);
            return (r instanceof Number n) ? n.intValue() : 0;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            throw new RuntimeException("runtime.invalidateAll failed: " + t, t);
        }

        // clearModuleCache()
        try {
            final Method m = c.getMethod("clearModuleCache");
            m.invoke(rt);
            return 0;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            throw new RuntimeException("runtime.clearModuleCache failed: " + t, t);
        }

        return 0;
    }

    private static Value tryInvokeValue(Class<?> c, Object target, String name, Class<?>[] sig, Object[] args) {
        try {
            final Method m = c.getMethod(name, sig);
            final Object r = m.invoke(target, args);
            return (r instanceof Value vv) ? vv : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Throwable t) {
            throw new RuntimeException("runtime." + name + " invocation failed: " + t, t);
        }
    }

    // ------------------------ KSystem ------------------------

    @Override
    public void onStart(SystemContext ctx) {
        withScopedSystem(ctx, () -> {
            ensureLoaded(ctx);
            invokeIfPresent("init", ctx);
            needsInit = false;
            return null;
        });
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        withScopedSystem(ctx, () -> {
            ensureLoaded(ctx);

            if (needsInit) {
                invokeIfPresent("init", ctx);
                needsInit = false;
            }

            invokeIfPresent("update", ctx, tpf);
            return null;
        });
    }

    @Override
    public void onStop(SystemContext ctx) {
        withScopedSystem(ctx, () -> {
            invokeIfPresent("destroy", ctx);
            return null;
        });
    }

    // ------------------------ HotReloadableSystem ------------------------

    @Override
    public void onHotReload(SystemContext ctx, String reason) {
        // FULL wipe: kill exports + force init next tick + invalidate caches
        try {
            withScopedSystem(ctx, () -> {
                // best-effort destroy old instance if it exists
                try {
                    invokeIfPresent("destroy", ctx);
                } catch (Throwable ignored) {
                }

                ScriptRuntime rt = ctx.runtime(runtimeProfile);
                if (rt == null) rt = ctx.runtime();

                if (rt != null) {
                    // user asked "clear all": invalidate ALL modules, not only this one
                    try {
                        invalidateAllViaReflection(rt, reason != null ? reason : "F5");
                    } catch (Throwable t) {
                        // fallback: at least invalidate this module
                        try {
                            invalidateManyViaReflection(rt, Set.of(module), reason != null ? reason : "F5");
                        } catch (Throwable ignored) {
                        }
                    }
                }

                exports = null;
                needsInit = true;

                log.info("[JsWorldSystem] HotReload({}) module={}", (reason != null ? reason : "F5"), module);
                return null;
            });
        } catch (Throwable t) {
            log.warn("[JsWorldSystem] HotReload failed module={}", module, t);
            exports = null;
            needsInit = true;
        }
    }

    // ------------------------ internals ------------------------

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
            if (pe.isCancelled()) {
                if (log.isDebugEnabled()) log.debug("[JsWorldSystem] cancelled module={}", module);
                return null;
            }
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

    private void ensureLoaded(SystemContext ctx) throws Exception {
        if (exports != null) return;

        synchronized (this) {
            if (exports != null) return;

            ScriptRuntime rt = ctx.runtime(runtimeProfile);
            if (rt == null) rt = ctx.runtime();
            if (rt == null) throw new IllegalStateException("JsWorldSystem requires ScriptRuntime in SystemContext");

            final Value ex = requireViaReflection(rt, module);
            if (ex == null) throw new IllegalStateException("ScriptRuntime returned null exports for module=" + module);

            exports = ex;

            if (log.isDebugEnabled()) {
                log.debug("[JsWorldSystem] loaded module={} exportsKeys={}", module, exports.getMemberKeys());
            }
        }
    }

    private void invokeIfPresent(String fnName, Object... args) {
        final Value ex = exports;
        if (ex == null || !ex.hasMember(fnName)) return;

        final Value fn = ex.getMember(fnName);
        if (fn == null || !fn.canExecute()) return;

        try {
            fn.execute(args);
        } catch (PolyglotException pe) {
            if (pe.isCancelled()) return;
            throw new RuntimeException("[JsWorldSystem] js threw in " + fnName + " module=" + module + " err=" + pe.getMessage(), pe);
        }
    }
}