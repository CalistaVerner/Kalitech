package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * JsWorldSystem (AAA):
 *  - ctx is stable/shared across systems
 *  - per-system config is applied ONLY during this system callback and then restored
 *  - module loading uses reflection against GraalScriptRuntime to avoid hard dependency on exact method names
 */
public final class JsWorldSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(JsWorldSystem.class);

    private final String module;
    private final Object cfg;     // JS-friendly config (ProxyObject/ProxyArray/primitives)
    private final Object sysDesc; // ProxyObject with {provider,module,config}

    // module exports cached
    private volatile Value exports;

    // optional: keep init state
    private volatile boolean started = false;

    /**
     * New constructor used by JsWorldSystemProvider (AAA scoped config).
     */
    public JsWorldSystem(String module, Object cfg, Object sysDesc) {
        this.module = Objects.requireNonNull(module, "module");
        this.cfg = cfg;
        this.sysDesc = sysDesc;
    }

    /**
     * Back-compat constructor if something still calls new JsWorldSystem(module).
     * In that case, scripts will see null config unless you pass it via ctx externally.
     */
    public JsWorldSystem(String module) {
        this(module, null, null);
    }

    // -----------------------
    // KSystem lifecycle
    // -----------------------

    @Override
    public void onStart(SystemContext ctx) {
        withScopedConfig(ctx, () -> {
            ensureLoaded(ctx);
            invokeIfPresent("init", ctx);
            started = true;
            return null;
        });
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        withScopedConfig(ctx, () -> {
            ensureLoaded(ctx);
            invokeIfPresent("update", ctx, tpf);
            return null;
        });
    }

    @Override
    public void onStop(SystemContext ctx) {
        withScopedConfig(ctx, () -> {
            try { invokeIfPresent("destroy"); } catch (Throwable ignored) {}
            started = false;
            return null;
        });
    }

    // -----------------------
    // Scoped config binding
    // -----------------------

    private <T> T withScopedConfig(SystemContext ctx, Callable<T> call) {
        // save previous values from shared ctx (if any)
        final boolean hadConfig = safeHas(ctx, "config");
        final boolean hadCfg    = safeHas(ctx, "cfg");
        final boolean hadSystem = safeHas(ctx, "system");

        final Object prevConfig = hadConfig ? safeGet(ctx, "config") : null;
        final Object prevCfg    = hadCfg    ? safeGet(ctx, "cfg")    : null;
        final Object prevSystem = hadSystem ? safeGet(ctx, "system") : null;

        // set this system values (ONLY for this callback)
        if (cfg != null) {
            safePut(ctx, "config", cfg);
            safePut(ctx, "cfg", cfg);
        }
        if (sysDesc != null) {
            safePut(ctx, "system", sysDesc);
        }

        try {
            return call.call();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // restore exactly to previous state
            if (hadConfig) safePut(ctx, "config", prevConfig); else safeRemove(ctx, "config");
            if (hadCfg)    safePut(ctx, "cfg", prevCfg);       else safeRemove(ctx, "cfg");
            if (hadSystem) safePut(ctx, "system", prevSystem); else safeRemove(ctx, "system");
        }
    }

    private static boolean safeHas(SystemContext ctx, String k) {
        try { return ctx != null && ctx.has(k); } catch (Throwable ignored) { return false; }
    }

    private static Object safeGet(SystemContext ctx, String k) {
        try { return (ctx == null) ? null : ctx.get(k); } catch (Throwable ignored) { return null; }
    }

    private static void safePut(SystemContext ctx, String k, Object v) {
        try { if (ctx != null) ctx.put(k, v); } catch (Throwable ignored) {}
    }

    private static void safeRemove(SystemContext ctx, String k) {
        try { if (ctx != null) ctx.remove(k); } catch (Throwable ignored) {}
    }

    // -----------------------
    // Module loading
    // -----------------------

    private void ensureLoaded(SystemContext ctx) throws Exception {
        if (exports != null) return;

        // SystemContext.runtime() is package-private; JsWorldSystem is in same package => доступ есть.
        final GraalScriptRuntime rt;
        try {
            rt = ctx.runtime();
        } catch (Throwable t) {
            log.error("[jsSystem] cannot access ctx.runtime() for module {}: {}", module, t.toString());
            throw t;
        }

        try {
            exports = requireViaReflection(rt, module);
            if (exports == null) {
                throw new IllegalStateException("GraalScriptRuntime returned null exports for module=" + module);
            }
        } catch (Throwable t) {
            log.error("[jsSystem] failed to load module {}: {}", module, t.toString());
            throw t;
        }
    }

    /**
     * Tries several common runtime method names/signatures to load a module.
     * This avoids coupling to your exact GraalScriptRuntime API.
     */
    private static Value requireViaReflection(GraalScriptRuntime rt, String module) throws Exception {
        final Class<?> c = rt.getClass();

        // 1) require(String)
        Value v = tryInvokeValue(c, rt, "require", new Class<?>[]{String.class}, new Object[]{module});
        if (v != null) return v;

        // 2) requireModule(String)
        v = tryInvokeValue(c, rt, "requireModule", new Class<?>[]{String.class}, new Object[]{module});
        if (v != null) return v;

        // 3) loadModule(String)
        v = tryInvokeValue(c, rt, "loadModule", new Class<?>[]{String.class}, new Object[]{module});
        if (v != null) return v;

        // 4) evalModule(String)
        v = tryInvokeValue(c, rt, "evalModule", new Class<?>[]{String.class}, new Object[]{module});
        if (v != null) return v;

        // 5) evaluateModule(String)
        v = tryInvokeValue(c, rt, "evaluateModule", new Class<?>[]{String.class}, new Object[]{module});
        if (v != null) return v;

        // 6) require(String request, String parent) — parent null
        v = tryInvokeValue(c, rt, "require", new Class<?>[]{String.class, String.class}, new Object[]{module, null});
        if (v != null) return v;

        // 7) require(String request, Object parent) — parent null
        v = tryInvokeValue(c, rt, "require", new Class<?>[]{String.class, Object.class}, new Object[]{module, null});
        if (v != null) return v;

        // If none worked, give a useful error with available method names
        StringBuilder sb = new StringBuilder();
        for (Method m : c.getMethods()) {
            if (m.getName().toLowerCase().contains("require") ||
                    m.getName().toLowerCase().contains("module") ||
                    m.getName().toLowerCase().contains("eval")) {
                sb.append(m.getName()).append("(");
                Class<?>[] pt = m.getParameterTypes();
                for (int i = 0; i < pt.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(pt[i].getSimpleName());
                }
                sb.append(") -> ").append(m.getReturnType().getSimpleName()).append("; ");
            }
        }

        throw new IllegalStateException(
                "Cannot load module via GraalScriptRuntime reflection. " +
                        "Tried: require/requireModule/loadModule/evalModule/evaluateModule. " +
                        "Candidates: " + sb
        );
    }

    private static Value tryInvokeValue(Class<?> c, Object target, String name, Class<?>[] sig, Object[] args) {
        try {
            Method m = c.getMethod(name, sig);
            Object r = m.invoke(target, args);
            if (r instanceof Value vv) return vv;
            return null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Throwable t) {
            // method exists but failed — this is important
            throw new RuntimeException("runtime." + name + " invocation failed: " + t, t);
        }
    }

    // -----------------------
    // Invocations
    // -----------------------

    private void invokeIfPresent(String fnName, Object... args) {
        if (exports == null) return;
        if (!exports.hasMember(fnName)) return;

        Value fn = exports.getMember(fnName);
        if (fn == null || !fn.canExecute()) return;

        fn.execute(args);
    }
}