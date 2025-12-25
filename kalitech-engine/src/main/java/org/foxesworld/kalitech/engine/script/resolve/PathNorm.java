// FILE: PathNorm.java
package org.foxesworld.kalitech.engine.script.resolve;

// Author: Calista Verner

import java.util.ArrayList;
import java.util.List;

public final class PathNorm {
    private PathNorm() {}

    public static String normalizeId(String moduleId) {
        if (moduleId == null) return "";
        String id = moduleId.trim().replace('\\', '/');

        while (id.startsWith("./")) id = id.substring(2);
        while (id.startsWith("/")) id = id.substring(1);

        id = id.replaceAll("/{2,}", "/");
        if (id.endsWith("/")) id = id.substring(0, id.length() - 1);

        return id;
    }

    public static String dirnameOf(String moduleId) {
        if (moduleId == null) return "";
        String id = moduleId.replace('\\', '/');
        int idx = id.lastIndexOf('/');
        return idx < 0 ? "" : id.substring(0, idx);
    }

    /** "has extension" only if '.' is in the last segment */
    public static boolean hasExtension(String moduleId) {
        if (moduleId == null) return false;
        String id = moduleId.replace('\\', '/');
        int slash = id.lastIndexOf('/');
        int dot = id.lastIndexOf('.');
        return dot > slash;
    }

    /** Join path segments and normalize slashes. Does NOT strip trailing slash. */
    public static String join(String a, String b) {
        String aa = (a == null) ? "" : a.replace('\\', '/');
        String bb = (b == null) ? "" : b.replace('\\', '/');

        if (aa.endsWith("/")) aa = aa.substring(0, aa.length() - 1);
        while (bb.startsWith("/")) bb = bb.substring(1);

        String out = aa.isEmpty() ? bb : (aa + "/" + bb);
        out = out.replaceAll("/{2,}", "/");
        return out;
    }

    /**
     * Expands a canonical resolved id into candidates following your rule:
     * - if id already has extension -> [id]
     * - else -> [id/index.js, id.js]
     *
     * IMPORTANT: existence check is done by the runtime (I/O layer), not here.
     */
    public static List<String> expandCandidates(String resolvedId) {
        String base = normalizeId(resolvedId);
        if (base.isEmpty()) return List.of();

        if (hasExtension(base)) {
            return List.of(base);
        }

        List<String> out = new ArrayList<>(2);
        out.add(join(base, "index.js"));
        out.add(base + ".js");
        return out;
    }
}