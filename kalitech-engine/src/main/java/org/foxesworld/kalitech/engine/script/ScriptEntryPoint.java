package org.foxesworld.kalitech.engine.script;

import org.foxesworld.kalitech.engine.script.resolve.PathNorm;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Virtual application-script mount.
 *
 * <p>Every application module has a stable canonical id under
 * {@code @app/<namespace>/...}; project-owned file paths never participate in
 * dependency identities or imports.</p>
 */
public record ScriptEntryPoint(String namespace, String scriptRoot, String entry) {

    public static final String APP_PREFIX = "@app/";

    private static final Pattern NAMESPACE_PATTERN =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");

    public ScriptEntryPoint {
        namespace = normalizeNamespace(namespace);
        scriptRoot = normalizeRelativePath(scriptRoot, "scripts.root");
        entry = normalizeRelativePath(entry, "scripts.entry");

        int slash = entry.lastIndexOf('/');
        int dot = entry.lastIndexOf('.');
        if (dot > slash) {
            if (!entry.endsWith(".lua")) {
                throw new IllegalArgumentException("scripts.entry must reference a Lua module");
            }
        } else {
            entry += ".lua";
        }
    }

    public String moduleRoot() {
        return APP_PREFIX + namespace;
    }

    public String moduleId() {
        return moduleRoot() + "/" + entry;
    }

    /**
     * Maps a canonical application module id to a path relative to projectOwned.
     * Returns {@code null} when the id belongs to another namespace.
     */
    public String projectOwnedPath(String canonicalModuleId) {
        String id = PathNorm.normalizeId(canonicalModuleId);
        String prefix = moduleRoot() + "/";
        if (!id.startsWith(prefix)) return null;

        String relative = id.substring(prefix.length());
        try {
            relative = normalizeRelativePath(relative, "application module id");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return PathNorm.join(scriptRoot, relative);
    }

    /**
     * Maps a physical hot-reload path relative to projectOwned back to the
     * canonical application module id.
     */
    public String moduleIdForProjectPath(String projectPath) {
        final String normalized;
        try {
            normalized = normalizeRelativePath(projectPath, "project-owned path");
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        String prefix = scriptRoot + "/";
        if (!normalized.startsWith(prefix)) return null;

        String relative = normalized.substring(prefix.length());
        if (relative.isBlank() || !PathNorm.hasExtension(relative)) return null;
        return moduleRoot() + "/" + relative;
    }

    private static String normalizeNamespace(String value) {
        String namespace = requireNonBlank(value, "scripts.namespace")
                .toLowerCase(Locale.ROOT);
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException(
                    "scripts.namespace must match " + NAMESPACE_PATTERN.pattern());
        }
        return namespace;
    }

    private static String normalizeRelativePath(String value, String field) {
        String raw = requireNonBlank(value, field).replace('\\', '/');
        if (raw.startsWith("/") || raw.startsWith("@")
                || raw.matches("^[A-Za-z]:/.*") || raw.indexOf(':') >= 0) {
            throw new IllegalArgumentException(field + " must be a relative project-owned path");
        }

        for (String segment : raw.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(field + " must not contain traversal segments");
            }
        }

        String normalized = PathNorm.normalizeId(raw);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("project descriptor missing '" + field + "'");
        }
        return value.trim();
    }
}
