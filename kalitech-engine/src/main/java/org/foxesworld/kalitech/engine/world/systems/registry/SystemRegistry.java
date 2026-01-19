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
    private final Map<String, SystemDescriptor> descriptors;
    private final Map<SystemType, List<SystemDescriptor>> descriptorsByType;

    public SystemRegistry() {
        Map<String, SystemProvider> map = new LinkedHashMap<>();
        Map<String, SystemDescriptor> meta = new LinkedHashMap<>();
        Map<SystemType, List<SystemDescriptor>> byType = new EnumMap<>(SystemType.class);
        ServiceLoader<SystemProvider> loader = ServiceLoader.load(SystemProvider.class);

        for (SystemProvider p : loader) {
            SystemDescriptor descriptor = safeDescriptor(p);
            if (descriptor == null) continue;
            String id = descriptor.id();

            if (map.containsKey(id)) {
                throw new IllegalStateException("Duplicate SystemProvider id: " + id);
            }

            map.put(id, p);
            meta.put(id, descriptor);
            byType.computeIfAbsent(descriptor.type(), ignored -> new ArrayList<>()).add(descriptor);
            log.info("SystemProvider registered: id={} type={} module={} provider={}",
                    id, descriptor.type(), descriptor.module(), p.getClass().getName());
        }

        this.providers = Collections.unmodifiableMap(map);
        this.descriptors = Collections.unmodifiableMap(meta);
        this.descriptorsByType = freezeTypeMap(byType);
        log.info("SystemRegistry ready: providers={} types={}", providers.size(), summarizeTypes());
    }

    private static SystemDescriptor safeDescriptor(SystemProvider p) {
        try {
            if (p == null) return null;
            SystemDescriptor descriptor = p.descriptor();
            if (descriptor == null) return null;
            if (descriptor.id() == null || descriptor.id().isBlank()) return null;
            String providerId = p.id();
            if (providerId != null && !providerId.equals(descriptor.id())) {
                log.warn("SystemProvider id mismatch: provider={} descriptor={}", providerId, descriptor.id());
            }
            return descriptor;
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
     * Returns a descriptor for the given id or null if not known.
     */
    public SystemDescriptor descriptor(String id) {
        return (id == null) ? null : descriptors.get(id);
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
            SystemDescriptor descriptor = descriptors.get(id);
            String module = (descriptor != null) ? descriptor.module().toString() : "unknown";
            log.error("SystemProvider {} failed to create system (module={})", id, module, t);
            return null;
        }
    }

    public Set<String> ids() {
        return providers.keySet();
    }

    /**
     * Returns immutable descriptors grouped by {@link SystemType}.
     */
    public Map<SystemType, List<SystemDescriptor>> descriptorsByType() {
        return descriptorsByType;
    }

    private Map<SystemType, List<SystemDescriptor>> freezeTypeMap(
            Map<SystemType, List<SystemDescriptor>> source) {
        Map<SystemType, List<SystemDescriptor>> frozen = new EnumMap<>(SystemType.class);
        for (Map.Entry<SystemType, List<SystemDescriptor>> entry : source.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private String summarizeTypes() {
        if (descriptorsByType.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<SystemType, List<SystemDescriptor>> entry : descriptorsByType.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(entry.getKey()).append('=').append(entry.getValue().size());
        }
        return sb.toString();
    }
}
