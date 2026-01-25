package org.foxesworld.kalitech.engine.modules.moduleLoader;

import java.util.List;
import java.util.Objects;

/**
 * Immutable descriptor loaded from module.json inside a module JAR.
 */
public final class ModuleDescriptor {

    public final String id;
    public final String name;
    public final String version;
    public final String mainClass;

    public final List<String> depends;

    /**
     * Optional: JS entrypoint path inside the JAR.
     */
    public final String js;

    /**
     * Optional: TS declaration path inside the JAR.
     */
    public final String types;

    /**
     * Optional: docs path inside the JAR.
     */
    public final String docs;

    /**
     * Optional: list of global aliases to expose.
     */
    public final String[] globals;

    public ModuleDescriptor(
            String id,
            String name,
            String version,
            String mainClass,
            List<String> depends,
            String js,
            String types,
            String docs,
            String[] globals
    ) {
        this.id = requireNonBlank(id, "id");
        this.name = (name == null || name.isBlank()) ? this.id : name.trim();
        this.version = (version == null || version.isBlank()) ? "0.0.0" : version.trim();
        this.mainClass = requireNonBlank(mainClass, "mainClass");

        this.depends = (depends == null) ? List.of() : List.copyOf(depends);

        this.js = normalizeOpt(js);
        this.types = normalizeOpt(types);
        this.docs = normalizeOpt(docs);
        this.globals = (globals == null) ? new String[0] : globals;
    }

    private static String normalizeOpt(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    private static String requireNonBlank(String v, String name) {
        Objects.requireNonNull(v, name);
        String s = v.trim();
        if (s.isEmpty()) throw new IllegalArgumentException(name + " is blank");
        return s;
    }
}