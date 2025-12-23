package org.foxesworld.kalitech.engine.world.systems.registry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

import java.util.*;

public final class SystemRegistry {
    private static final Logger log = LogManager.getLogger(SystemRegistry.class);

    private final Map<String, SystemProvider> providers;

    public SystemRegistry() {
        Map<String, SystemProvider> map = new LinkedHashMap<>();
        ServiceLoader<SystemProvider> loader = ServiceLoader.load(SystemProvider.class);

        for (SystemProvider p : loader) {
            String id = Objects.requireNonNull(p.id(), "provider.id()");
            if (map.containsKey(id)) {
                throw new IllegalStateException("Duplicate SystemProvider id: " + id);
            }
            map.put(id, p);
            log.info("SystemProvider registered: {}", id);
        }
        this.providers = Collections.unmodifiableMap(map);
    }

    public SystemProvider require(String id) {
        SystemProvider p = providers.get(id);
        if (p == null) throw new IllegalArgumentException("Unknown system id: " + id);
        return p;
    }

    public Set<String> ids() {
        return providers.keySet();
    }

    /**
     * Create a system by id via its provider.
     *
     * @param id system id (provider.id())
     * @param ctx system context
     * @param config optional config object (JS Value), may be null
     */
    public KSystem create(String id, SystemContext ctx, Value config) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ctx, "ctx");

        SystemProvider p = require(id);
        try {
            KSystem sys = p.create(ctx, config);
            if (sys == null) {
                throw new IllegalStateException("SystemProvider '" + id + "' returned null system");
            }
            log.debug("System created: id={} provider={}", id, p.getClass().getName());
            return sys;
        } catch (RuntimeException e) {
            log.error("Failed to create system id={} provider={}", id, p.getClass().getName(), e);
            throw e;
        }
    }
}