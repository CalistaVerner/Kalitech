package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.Callable;

public final class JsWorldSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(JsWorldSystem.class);

    private final String module;
    private final Object cfg;
    private final Object sysDesc;
    private final String runtimeProfile;

    private volatile Value exports;
    private volatile boolean started = false;

    public JsWorldSystem(String module, Object cfg, Object sysDesc, String runtimeProfile) {
        this.module = Objects.requireNonNull(module, "module");
        this.cfg = cfg;
        this.sysDesc = sysDesc;
        this.runtimeProfile = (runtimeProfile == null || runtimeProfile.isBlank()) ? "world" : runtimeProfile.trim();
    }

    // back-compat
    public JsWorldSystem(String module, Object cfg, Object sysDesc) {
        this(module, cfg, sysDesc, "world");
    }

    public JsWorldSystem(String module) {
        this(module, null, null, "world");
    }

    private static boolean safeHas(SystemContext ctx, String k) {
        try {
            return ctx != null && ctx.has(k);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object safeGet(SystemContext ctx, String k) {
        try {
            return (ctx == null) ? null : ctx.get(k);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void safePut(SystemContext ctx, String k, Object v) {
        try {
            if (ctx != null) ctx.put(k, v);
        } catch (Throwable ignored) {
        }
    }

    private static void safeRemove(SystemContext ctx, String k) {
        try {
            if (ctx != null) ctx.remove(k);
        } catch (Throwable ignored) {
        }
    }

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

        v = tryInvokeValue(c, rt, "require", new Class<?>[]{String.class, String.class}, new Object[]{module, null});
        if (v != null) return v;

        v = tryInvokeValue(c, rt, "require", new Class<?>[]{String.class, Object.class}, new Object[]{module, null});
        if (v != null) return v;

        StringBuilder sb = new StringBuilder();
        for (Method m : c.getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("require") || n.contains("module") || n.contains("eval")) {
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
                "Cannot load module via ScriptRuntime reflection. " +
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
            throw new RuntimeException("runtime." + name + " invocation failed: " + t, t);
        }
    }

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
            try {
                invokeIfPresent("destroy");
            } catch (Throwable ignored) {
            }
            started = false;
            return null;
        });
    }

    private <T> T withScopedConfig(SystemContext ctx, Callable<T> call) {
        final boolean hadConfig = safeHas(ctx, "config");
        final boolean hadCfg = safeHas(ctx, "cfg");
        final boolean hadSystem = safeHas(ctx, "system");

        final Object prevConfig = hadConfig ? safeGet(ctx, "config") : null;
        final Object prevCfg = hadCfg ? safeGet(ctx, "cfg") : null;
        final Object prevSystem = hadSystem ? safeGet(ctx, "system") : null;

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
            if (hadConfig) safePut(ctx, "config", prevConfig);
            else safeRemove(ctx, "config");
            if (hadCfg) safePut(ctx, "cfg", prevCfg);
            else safeRemove(ctx, "cfg");
            if (hadSystem) safePut(ctx, "system", prevSystem);
            else safeRemove(ctx, "system");
        }
    }

    private void ensureLoaded(SystemContext ctx) throws Exception {
        if (exports != null) return;

        final ScriptRuntime rt = ctx.runtime(runtimeProfile);

        exports = requireViaReflection(rt, module);
        if (exports == null) {
            throw new IllegalStateException("ScriptRuntime returned null exports for module=" + module);
        }
    }

    private void invokeIfPresent(String fnName, Object... args) {
        if (exports == null) return;
        if (!exports.hasMember(fnName)) return;

        Value fn = exports.getMember(fnName);
        if (fn == null || !fn.canExecute()) return;

        fn.execute(args);
    }
}