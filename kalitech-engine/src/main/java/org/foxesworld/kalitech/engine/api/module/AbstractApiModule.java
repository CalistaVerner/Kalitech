package org.foxesworld.kalitech.engine.api.module;

import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;

import java.util.Objects;
import java.util.function.Supplier;

public abstract class AbstractApiModule implements ApiModule {

    protected final String id;
    protected final String name;
    protected final String version;
    private final ApiStats stats = new ApiStats();
    protected EngineApiImpl engine;
    protected ApiContext ctx;
    protected Logger log;
    private volatile boolean attached;

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

    @Override
    public void attach(ApiContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.engine = ctx.engine;
        this.log = ctx.log;
        this.attached = true;
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
}
