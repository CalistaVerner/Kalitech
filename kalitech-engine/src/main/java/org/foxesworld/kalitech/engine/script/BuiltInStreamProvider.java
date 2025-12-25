// FILE: BuiltInStreamProvider.java
package org.foxesworld.kalitech.engine.script;

import java.io.InputStream;
import java.util.Objects;

/**
 * Built-in modules provider: serves @builtin/* from resources as InputStream.
 *
 * Author: Calista Verner
 */
public final class BuiltInStreamProvider implements GraalScriptRuntime.ModuleStreamProvider {

    private final ClassLoader classLoader;
    private final String resourceBasePath; // e.g. "kalitech/builtin/"
    private final String builtinPrefix;    // e.g. "@builtin/"

    public BuiltInStreamProvider(ClassLoader classLoader, String resourceBasePath, String builtinPrefix) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.resourceBasePath = Objects.requireNonNull(resourceBasePath, "resourceBasePath");
        this.builtinPrefix = Objects.requireNonNull(builtinPrefix, "builtinPrefix");
    }

    @Override
    public InputStream openStream(String moduleId) {
        if (moduleId == null) return null;
        if (!moduleId.startsWith(builtinPrefix)) return null;

        String rel = moduleId.substring(builtinPrefix.length());
        if (rel.isBlank()) return null;

        // allow "@builtin/bootstrap" and "@builtin/bootstrap.js"
        if (!rel.endsWith(".js")) rel = rel + ".js";

        String resPath = resourceBasePath + rel;
        return classLoader.getResourceAsStream(resPath);
    }
}