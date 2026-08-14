package org.foxesworld.kalitech.engine.moduleLoader;

import java.util.List;
import java.util.Objects;

/**
 * Immutable descriptor loaded from module.json inside a module JAR.
 */
public final class ModuleDescriptor {

    public final String id;
    public final String name;
    public final String version;
    public final String[] mainClass;
    public final List<String> depends;
    public final String lua;
    public final String docs;

    public ModuleDescriptor(
            String id,
            String name,
            String version,
            String[] mainClass,
            List<String> depends,
            String lua,
            String docs
    ) {
        this.id = requireNonBlank(id, "id");
        this.name = (name == null || name.isBlank()) ? this.id : name.trim();
        this.version = (version == null || version.isBlank()) ? "0.0.0" : version.trim();
        this.mainClass = requireNonEmpty(mainClass, "mainClass");
        this.depends = depends == null ? List.of() : List.copyOf(depends);
        this.lua = requireNonBlank(lua, "lua");
        this.docs = normalizeOptional(docs);
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String[] requireNonEmpty(String[] values, String name) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("module.json missing '" + name + "'");
        }

        int count = 0;
        for (String value : values) {
            if (value != null && !value.isBlank()) count++;
        }
        if (count == 0) {
            throw new IllegalArgumentException("module.json missing '" + name + "'");
        }

        String[] result = new String[count];
        int index = 0;
        for (String value : values) {
            if (value == null) continue;
            String normalized = value.trim();
            if (!normalized.isEmpty()) result[index++] = normalized;
        }
        return result;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("module.json missing '" + name + "'");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "ModuleDescriptor{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", mainClass=" + java.util.Arrays.toString(mainClass) +
                ", depends=" + depends +
                ", lua='" + lua + '\'' +
                ", docs='" + docs + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModuleDescriptor that)) return false;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(version, that.version)
                && java.util.Arrays.equals(mainClass, that.mainClass)
                && Objects.equals(depends, that.depends)
                && Objects.equals(lua, that.lua)
                && Objects.equals(docs, that.docs);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, name, version, depends, lua, docs);
        result = 31 * result + java.util.Arrays.hashCode(mainClass);
        return result;
    }
}
