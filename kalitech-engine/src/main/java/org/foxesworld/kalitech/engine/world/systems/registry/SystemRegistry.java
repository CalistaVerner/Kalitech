// FILE: SystemRegistry.java
package org.foxesworld.kalitech.engine.world.systems.registry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.graalvm.polyglot.Value;

import java.util.*;

/**
 * SystemRegistry
 * <p>
 * Script-driven world building requires a stable Java-side registry that maps string IDs
 * (coming from JS world descriptors) to {@link SystemProvider} factories discovered via
 * {@link java.util.ServiceLoader}.
 * <p>
 * Goals:
 * - Zero magic: Java resolves only system IDs; no special-case keys like "mode"/"entities".
 * - Stable failures: unknown/failed systems are logged and safely skipped.
 * - Fast hot path: O(1) lookup by id.
 */
public final class SystemRegistry {

    private static final Logger log = LogManager.getLogger(SystemRegistry.class);

    private final Map<String, SystemProvider> providers;

    public SystemRegistry() {
        Map<String, SystemProvider> map = new LinkedHashMap<>();
        ServiceLoader<SystemProvider> loader = ServiceLoader.load(SystemProvider.class);

        for (SystemProvider p : loader) {
            String id = safeId(p);
            if (id == null) continue;

            if (map.containsKey(id)) {
                throw new IllegalStateException("Duplicate SystemProvider id: " + id);
            }

            map.put(id, p);
            log.info("SystemProvider registered: {} -> {}", id, p.getClass().getName());
        }

        this.providers = Collections.unmodifiableMap(map);
    }

    private static String safeId(SystemProvider p) {
        try {
            String id = (p != null) ? p.id() : null;
            if (id == null) return null;
            id = id.trim();
            if (id.isEmpty()) return null;
            return id;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Returns provider or throws if missing. Prefer {@link #create(String, SystemContext, Value)} for resilient builds. */
    public SystemProvider require(String id) {
        SystemProvider p = providers.get(id);
        if (p == null) throw new IllegalArgumentException("Unknown system id: " + id);
        return p;
    }

    /**
     * Returns provider or null if missing.
     */
    public SystemProvider get(String id) {
        return (id == null) ? null : providers.get(id);
    }

    /**
     * Resilient factory: creates a system instance from provider id.
     * Returns null if id unknown or provider threw; caller may skip.
     */
    public KSystem create(String id, SystemContext ctx, Value config) {
        if (id == null || id.isBlank()) return null;
        Objects.requireNonNull(ctx, "ctx");

        SystemProvider p = providers.get(id);
        if (p == null) {
            log.warn("Unknown system id: {} (known: {})", id, providers.keySet());
            return null;
        }

        try {
            KSystem sys = p.create(ctx, config);
            if (sys == null) {
                log.warn("SystemProvider {} returned null system (skipping)", id);
            }
            return sys;
        } catch (Throwable t) {
            log.error("SystemProvider {} failed to create system", id, t);
            return null;
        }
    }

    public Set<String> ids() {
        return providers.keySet();
    }
}