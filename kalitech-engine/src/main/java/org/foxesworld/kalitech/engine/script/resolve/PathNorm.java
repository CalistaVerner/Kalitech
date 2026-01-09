// FILE: PathNorm.java
package org.foxesworld.kalitech.engine.script.resolve;

// Author: KΛYLΛ

import java.util.ArrayList;
import java.util.List;

public final class PathNorm {
    private PathNorm() {
    }

    // Supported require() module suffixes (data/code)
    // IMPORTANT: runtime MUST know how to load each of these.
    private static final String[] MODULE_EXTS = new String[]{
            ".js",
            ".json"
    };

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

    /**
     * "has extension" only if '.' is in the last segment
     */
    public static boolean hasExtension(String moduleId) {
        if (moduleId == null) return false;
        String id = moduleId.replace('\\', '/');
        int slash = id.lastIndexOf('/');
        int dot = id.lastIndexOf('.');
        return dot > slash;
    }

    /**
     * Join path segments and normalize slashes. Does NOT strip trailing slash.
     */
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
     * Expands a canonical resolved id into candidates.
     *
     * Rule:
     * - if id already has extension -> [id]
     * - else -> try directory index + base name for every supported ext
     *
     * Example: "@module/manifest" ->
     *   "@module/manifest/index.js"
     *   "@module/manifest/index.json"
     *   "@module/manifest.js"
     *   "@module/manifest.json"
     *
     * IMPORTANT: existence check is done by the runtime (I/O layer), not here.
     */
    public static List<String> expandCandidates(String resolvedId) {
        String base = normalizeId(resolvedId);
        if (base.isEmpty()) return List.of();

        if (hasExtension(base)) {
            return List.of(base);
        }

        // 2 * ext count: index.* + base.*
        List<String> out = new ArrayList<>(MODULE_EXTS.length * 2);

        // 1) directory index.<ext>
        for (String ext : MODULE_EXTS) {
            out.add(join(base, "index" + ext));
        }

        // 2) base.<ext>
        for (String ext : MODULE_EXTS) {
            out.add(base + ext);
        }

        return out;
    }
}