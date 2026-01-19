package org.foxesworld.kalitech.engine.world.systems.registry;

import java.util.Objects;

/**
 * Immutable metadata describing a world system provider.
 * <p>
 * This descriptor is used for registration validation and diagnostics. It does not
 * affect runtime scheduling; that remains defined by {@code KSystem.threadMode()}.
 */
public final class SystemDescriptor {

    private final String id;
    private final SystemType type;
    private final SystemModule module;
    private final String description;

    public SystemDescriptor(String id, SystemType type, SystemModule module, String description) {
        this.id = normalizeId(id);
        this.type = Objects.requireNonNull(type, "type");
        this.module = Objects.requireNonNull(module, "module");
        this.description = (description == null) ? "" : description.trim();
    }

    public static SystemDescriptor simple(String id) {
        return new SystemDescriptor(id, SystemType.CORE, SystemModule.engine("engine"), "");
    }

    public String id() {
        return id;
    }

    public SystemType type() {
        return type;
    }

    public SystemModule module() {
        return module;
    }

    public String description() {
        return description;
    }

    private static String normalizeId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("System id is null");
        }
        String trimmed = id.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("System id is blank");
        }
        return trimmed;
    }
}
