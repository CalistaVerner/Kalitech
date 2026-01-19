// FILE: org/foxesworld/kalitech/engine/world/systems/ProviderWorldSystem.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.world.systems;

import org.foxesworld.kalitech.engine.world.systems.registry.SystemRegistry;
import org.graalvm.polyglot.Value;

import java.util.Objects;

/**
 * Deferred wrapper that instantiates a real system from SystemRegistry on world start
 * and delegates lifecycle calls to it.
 */
public final class ProviderWorldSystem implements KSystem {

    private final String providerId;
    private final Value config;

    private volatile KSystem delegate;

    public ProviderWorldSystem(String providerId, Value config) {
        this.providerId = Objects.requireNonNull(providerId, "providerId").trim();
        if (this.providerId.isEmpty()) throw new IllegalArgumentException("providerId is blank");
        this.config = config;
    }

    public String providerId() {
        return providerId;
    }

    @Override
    public ThreadMode threadMode() {
        final KSystem d = delegate;
        if (d != null) {
            try { return d.threadMode(); } catch (Throwable ignored) {}
        }
        return ThreadMode.MAIN;
    }

    @Override
    public int priority() {
        final KSystem d = delegate;
        if (d != null) {
            try { return d.priority(); } catch (Throwable ignored) {}
        }
        return 0;
    }

    @Override
    public double desiredHz() {
        final KSystem d = delegate;
        if (d != null) {
            try { return d.desiredHz(); } catch (Throwable ignored) {}
        }
        return 60.0;
    }

    @Override
    public void onStart(SystemContext ctx) {
        Objects.requireNonNull(ctx, "ctx");

        final KSystem created = SystemRegistry.get().create(providerId, ctx, config);
        if (created == null) {
            ctx.log().warn("[world] native system '{}' skipped (provider missing or failed)", providerId);
            return;
        }

        delegate = created;

        try {
            created.onStart(ctx);
        } catch (Throwable t) {
            ctx.log().error("[world] native system '{}' onStart failed: {}", providerId, t.toString(), t);
        }
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        final KSystem d = delegate;
        if (d == null) return;

        try {
            d.onUpdate(ctx, tpf);
        } catch (Throwable t) {
            ctx.log().error("[world] native system '{}' onUpdate failed: {}", providerId, t.toString(), t);
        }
    }

    @Override
    public void onStop(SystemContext ctx) {
        final KSystem d = delegate;
        delegate = null;
        if (d == null) return;

        try {
            d.onStop(ctx);
        } catch (Throwable t) {
            if (ctx != null) {
                ctx.log().error("[world] native system '{}' onStop failed: {}", providerId, t.toString(), t);
            }
        }
    }

    @Override
    public String toString() {
        return "ProviderWorldSystem{id='" + providerId + "'}";
    }
}