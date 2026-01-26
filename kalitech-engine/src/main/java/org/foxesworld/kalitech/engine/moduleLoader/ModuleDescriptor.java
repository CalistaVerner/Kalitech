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

    /**
     * mainClass can be either a single string or an array in module.json.
     * Here it's always normalized to a non-empty array.
     */
    public final String[] mainClass;

    public final List<String> depends;

    /**
     * Optional: JS entrypoint path inside the JAR.
     */
    public final String js;

    /** Optional: TS declaration path inside the JAR. */
    public final String types;

    /** Optional: docs path inside the JAR. */
    public final String docs;

    /** Optional: list of global aliases to expose. */
    public final String[] globals;

    public ModuleDescriptor(
            String id,
            String name,
            String version,
            String[] mainClass,
            List<String> depends,
            String js,
            String types,
            String docs,
            String[] globals
    ) {
        this.id = requireNonBlank(id, "id");
        this.name = (name == null || name.isBlank()) ? this.id : name.trim();
        this.version = (version == null || version.isBlank()) ? "0.0.0" : version.trim();
        this.mainClass = requireNonEmpty(mainClass, "mainClass");

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

    private static String[] requireNonEmpty(String[] v, String name) {
        if (v == null || v.length == 0) {
            throw new IllegalArgumentException("module.json missing '" + name + "'");
        }
        int n = 0;
        for (String s : v) {
            if (s != null && !s.isBlank()) n++;
        }
        if (n == 0) {
            throw new IllegalArgumentException("module.json missing '" + name + "'");
        }
        String[] out = new String[n];
        int i = 0;
        for (String s : v) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) out[i++] = t;
        }
        return out;
    }

    private static String requireNonBlank(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("module.json missing '" + name + "'");
        }
        return v.trim();
    }

    @Override
    public String toString() {
        return "ModuleDescriptor{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", mainClass=" + java.util.Arrays.toString(mainClass) +
                ", depends=" + depends +
                ", js='" + js + '\'' +
                ", types='" + types + '\'' +
                ", docs='" + docs + '\'' +
                ", globals=" + java.util.Arrays.toString(globals) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleDescriptor that)) return false;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(version, that.version)
                && java.util.Arrays.equals(mainClass, that.mainClass)
                && Objects.equals(depends, that.depends)
                && Objects.equals(js, that.js)
                && Objects.equals(types, that.types)
                && Objects.equals(docs, that.docs)
                && java.util.Arrays.equals(globals, that.globals);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, name, version, depends, js, types, docs);
        result = 31 * result + java.util.Arrays.hashCode(mainClass);
        result = 31 * result + java.util.Arrays.hashCode(globals);
        return result;
    }
}