// FILE: org/foxesworld/kalitech/engine/world/systems/SystemRegistry.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.world.systems.registry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemProvider;
import org.graalvm.polyglot.Value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Registry of world system providers discovered via ServiceLoader.
 * Lives in the world subsystem (not API) by design.
 */
public final class SystemRegistry {

    private static final Logger log = LogManager.getLogger(SystemRegistry.class);

    private static final class Holder {
        private static final SystemRegistry INSTANCE = new SystemRegistry();
    }

    public static SystemRegistry get() {
        return Holder.INSTANCE;
    }

    private final Map<String, SystemProvider> providers;

    private SystemRegistry() {
        final Map<String, SystemProvider> map = new LinkedHashMap<>();
        final ServiceLoader<SystemProvider> loader = ServiceLoader.load(SystemProvider.class);

        for (SystemProvider p : loader) {
            final String id = safeId(p);
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
            return id.isEmpty() ? null : id;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public SystemProvider getProvider(String id) {
        if (id == null || id.isBlank()) return null;
        return providers.get(id);
    }

    public KSystem create(String id, SystemContext ctx, Value config) {
        Objects.requireNonNull(ctx, "ctx");
        if (id == null || id.isBlank()) return null;

        final SystemProvider p = providers.get(id);
        if (p == null) {
            log.warn("Unknown system id: {} (known: {})", id, providers.keySet());
            return null;
        }

        try {
            final KSystem sys = p.create(ctx, config);
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