package org.foxesworld.kalitech.engine.world.systems.registry;

import java.util.Objects;

/**
 * Describes the module origin of a system (e.g., "ecs", "scripting").
 * Used by {@link SystemDescriptor} for readable logging and diagnostics.
 */
public final class SystemModule {

    private final String name;
    private final SystemModuleType type;

    private SystemModule(String name, SystemModuleType type) {
        this.name = normalizeName(name, type);
        this.type = Objects.requireNonNull(type, "type");
    }

    public static SystemModule of(String name, SystemModuleType type) {
        return new SystemModule(name, type);
    }

    public static SystemModule engine(String name) {
        return new SystemModule(name, SystemModuleType.ENGINE);
    }

    public static SystemModule scripting(String name) {
        return new SystemModule(name, SystemModuleType.SCRIPTING);
    }

    public static SystemModule ecs(String name) {
        return new SystemModule(name, SystemModuleType.ECS);
    }

    public static SystemModule custom(String name) {
        return new SystemModule(name, SystemModuleType.CUSTOM);
    }

    public String name() {
        return name;
    }

    public SystemModuleType type() {
        return type;
    }

    private static String normalizeName(String name, SystemModuleType type) {
        if (name == null || name.isBlank()) {
            return type.name().toLowerCase();
        }
        return name.trim();
    }

    @Override
    public String toString() {
        return type + ":" + name;
    }
}
