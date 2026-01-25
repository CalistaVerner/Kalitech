package org.foxesworld.kalitech.engine.api.module;

import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.contract.ApiContracts;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.contract.ContractMode;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Base class for API modules.
 *
 * <p>Threading contract:
 * - Any JME scenegraph work must run on the JME render thread.
 * - Use {@link #onJmeVoid(String, Runnable)} for async hops.
 * - Use {@link #onJmeSyncStrict(String, Callable)} for sync hops (throws on failure).
 * - Use {@link #onJmeSync(String, Callable, Object)} when a safe fallback is acceptable.
 *
 * <p>Contracts (annotations):
 * - Method-level: {@link ApiMethod} for execution rules (thread/sync/mode).
 * - Param-level: @Range/@NotNull/@Finite etc (validated via {@link ApiContracts}).
 * - Header params: {@link org.foxesworld.kalitech.engine.api.contract.ApiParam} (optional).
 *
 * <p>NOTE: This integration does NOT change existing onJme* stack.
 * New helpers {@link #apiVoid(Method, Object[], Runnable)} and {@link #apiCall(Method, Object[], Supplier)}
 * are opt-in and can be introduced gradually.
 */
public abstract class AbstractApiModule implements ApiModule {

    protected final String id;
    protected final String name;
    protected final String version;

    private final ApiStats stats = new ApiStats();

    protected EngineApiImpl engine;
    protected ApiContext ctx;
    protected Logger log;

    private volatile boolean attached;

    private static final long DEFAULT_TIMEOUT_MS = 2_000;

    // ---- Contracts (opt-in) ----
    private final ApiContracts contracts = new ApiContracts();
    private volatile ContractMode contractMode = ContractMode.STRICT;

    protected AbstractApiModule(String id, String name, String version) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = (name == null || name.isBlank()) ? id : name;
        this.version = (version == null || version.isBlank()) ? "0.0.0" : version;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public final ApiStats stats() {
        return stats;
    }

    /**
     * Helper to resolve Method once (use static final in ApiImpl).
     */
    protected static Method method(Class<?> owner, String name, Class<?>... paramTypes) {
        try {
            return owner.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Method not found: " + owner.getName() + "." + name, e);
        }
    }

    @Override
    public void detach() {
        this.attached = false;
        this.ctx = null;
        this.engine = null;
        this.log = null;
    }

    protected final void requireAttached() {
        if (!attached) throw new IllegalStateException("API module '" + id + "' is not attached");
    }

    protected final void profiledVoid(Runnable fn) {
        long t0 = System.nanoTime();
        try {
            fn.run();
            stats.onCall(System.nanoTime() - t0);
        } catch (Throwable t) {
            stats.onError();
            stats.onCall(System.nanoTime() - t0);
            throw t;
        }
    }

    private static ContractMode resolveContractMode(ApiContext ctx) {
        // Best path: add in ApiContext:
        // public ContractMode contractMode = ContractMode.STRICT;
        try {
            Object v = ctx.getClass().getField("contractMode").get(ctx);
            if (v instanceof ContractMode cm) return cm;
        } catch (Throwable ignored) {
        }

        String p = System.getProperty("kalitech.api.contract");
        if (p == null || p.isBlank()) return ContractMode.STRICT;

        try {
            return ContractMode.valueOf(p.trim().toUpperCase());
        } catch (Throwable ignored) {
            return ContractMode.STRICT;
        }
    }

    @Override
    public void attach(ApiContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.engine = (EngineApiImpl) ctx.engine;
        this.log = ctx.log;
        this.attached = true;

        this.contractMode = resolveContractMode(ctx);
    }

    protected final <T> T profiled(Supplier<T> fn) {
        long t0 = System.nanoTime();
        try {
            T v = fn.get();
            stats.onCall(System.nanoTime() - t0);
            return v;
        } catch (Throwable t) {
            stats.onError();
            stats.onCall(System.nanoTime() - t0);
            throw t;
        }
    }

    protected final boolean isJmeThread() {
        EngineApiImpl e = engine;
        return e != null && e.isJmeThread();
    }

    private String tag() {
        return "[api][" + id + "]";
    }

    private EngineApiImpl requireEngine(String where) {
        requireAttached();
        EngineApiImpl e = engine;
        if (e == null) throw new IllegalStateException(tag() + " " + where + ": engine is null");
        if (e.getApp() == null) throw new IllegalStateException(tag() + " " + where + ": app is null");
        return e;
    }

    /**
     * Async hop to JME thread (fire-and-forget).
     * The runnable will run on the JME thread as soon as possible.
     */
    protected final void onJmeVoid(String where, Runnable r) {
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(r, "r");

        if (isJmeThread()) {
            r.run();
            return;
        }

        EngineApiImpl e = requireEngine(where);
        e.getApp().enqueue(() -> {
            r.run();
            return null;
        });
    }

    /**
     * Sync hop to JME thread.
     * Throws on failure/timeout.
     */
    protected final <T> T onJmeSyncStrict(String where, Callable<T> c) {
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(c, "c");

        if (isJmeThread()) {
            try {
                return c.call();
            } catch (Throwable t) {
                throw new RuntimeException(tag() + " " + where + ": failed on JME thread", t);
            }
        }

        EngineApiImpl e = requireEngine(where);

        try {
            Future<T> f = e.getApp().enqueue(c);
            return f.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            throw new RuntimeException(tag() + " " + where + ": JME hop failed/timeout", t);
        }
    }

    // =====================================================================
    // Contracts (opt-in): do not break existing stack
    // =====================================================================

    /**
     * Sync hop to JME thread (void).
     * Safe variant: logs a warning on failure instead of throwing.
     */
    protected final void onJmeSyncVoid(String where, Runnable r) {
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(r, "r");

        if (isJmeThread()) {
            r.run();
            return;
        }

        EngineApiImpl e = requireEngine(where);

        try {
            Future<?> f = e.getApp().enqueue(() -> {
                r.run();
                return null;
            });
            f.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            Logger l = log;
            if (l != null) l.warn("{} {}: JME hop failed/timeout", tag(), where, t);
        }
    }

    /**
     * Sync hop to JME thread (safe fallback).
     * Returns fallback on failure/timeout.
     */
    protected final <T> T onJmeSync(String where, Callable<T> c, T fallback) {
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(c, "c");

        if (isJmeThread()) {
            try {
                return c.call();
            } catch (Throwable t) {
                Logger l = log;
                if (l != null) l.warn("{} {}: failed", tag(), where, t);
                return fallback;
            }
        }

        EngineApiImpl e = requireEngine(where);

        try {
            Future<T> f = e.getApp().enqueue(c);
            return f.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            Logger l = log;
            if (l != null) l.warn("{} {}: JME hop failed/timeout", tag(), where, t);
            return fallback;
        }
    }

    /**
     * Current module contract mode (default STRICT).
     * Can be overridden by:
     * 1) ApiContext.contractMode (recommended field)
     * 2) system property: -Dkalitech.api.contract=OFF|STRICT|CLAMP
     */
    protected final ContractMode contractMode() {
        return contractMode;
    }

    /**
     * Opt-in wrapper for void API methods.
     * Applies @ApiMethod(thread/sync/mode) + param contracts, then executes body.
     */
    protected final void apiVoid(Method m, Object[] args, Runnable body) {
        Objects.requireNonNull(m, "m");
        Objects.requireNonNull(body, "body");

        final ApiContracts.CompiledMethod cm = contracts.compile(m);

        // validate params + enforce @ApiThreadRule (throws if invalid)
        contracts.validateAndMaybeFix(contractModeEffective(cm), id, m, args, this::isJmeThread);

        // auto-hop if requested
        if (cm.sync && cm.threadRule == ApiThreadRule.JME && !isJmeThread()) {
            onJmeSyncVoid(whereOf(m), body);
            return;
        }

        // if threadRule=JME and not on JME, validator already threw
        body.run();
    }

    /**
     * Opt-in wrapper for returning API methods.
     * Applies @ApiMethod(thread/sync/mode) + param contracts, then executes body.
     */
    protected final <T> T apiCall(Method m, Object[] args, Supplier<T> body) {
        Objects.requireNonNull(m, "m");
        Objects.requireNonNull(body, "body");

        final ApiContracts.CompiledMethod cm = contracts.compile(m);

        contracts.validateAndMaybeFix(contractModeEffective(cm), id, m, args, this::isJmeThread);

        if (cm.sync && cm.threadRule == ApiThreadRule.JME && !isJmeThread()) {
            return onJmeSyncStrict(whereOf(m), body::get);
        }

        return body.get();
    }

    private ContractMode contractModeEffective(ApiContracts.CompiledMethod cm) {
        if (cm == null) return contractMode;
        ApiMethod.Mode mm = cm.methodMode;
        if (mm == null || mm == ApiMethod.Mode.DEFAULT) return contractMode;
        return switch (mm) {
            case OFF -> ContractMode.OFF;
            case STRICT -> ContractMode.STRICT;
            case CLAMP -> ContractMode.CLAMP;
            case DEFAULT -> contractMode;
        };
    }

    private String whereOf(Method m) {
        return m.getDeclaringClass().getSimpleName() + "." + m.getName();
    }
}