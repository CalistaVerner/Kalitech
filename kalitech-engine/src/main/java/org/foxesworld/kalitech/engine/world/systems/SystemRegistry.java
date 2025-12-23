package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemProvider;

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
}
