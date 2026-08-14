package org.foxesworld.kalitech.engine.script.resolve;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical Lua/JSON module path handling.
 */
public final class PathNorm {

    private static final String[] MODULE_EXTS = {".lua", ".json"};

    private PathNorm() {
    }

    public static String normalizeId(String moduleId) {
        if (moduleId == null) return "";
        String id = moduleId.trim().replace('\\', '/');

        while (id.startsWith("./")) id = id.substring(2);
        while (id.startsWith("/")) id = id.substring(1);

        id = id.replaceAll("/{2,}", "/");
        while (id.endsWith("/")) id = id.substring(0, id.length() - 1);

        return id;
    }

    public static String dirnameOf(String moduleId) {
        String id = normalizeId(moduleId);
        int idx = id.lastIndexOf('/');
        return idx < 0 ? "" : id.substring(0, idx);
    }

    public static boolean hasExtension(String moduleId) {
        String id = normalizeId(moduleId);
        for (String extension : MODULE_EXTS) {
            if (id.endsWith(extension)) return true;
        }
        return false;
    }

    public static String join(String a, String b) {
        String aa = a == null ? "" : a.replace('\\', '/');
        String bb = b == null ? "" : b.replace('\\', '/');

        while (aa.endsWith("/")) aa = aa.substring(0, aa.length() - 1);
        while (bb.startsWith("/")) bb = bb.substring(1);

        return normalizeId(aa.isEmpty() ? bb : aa + "/" + bb);
    }

    public static List<String> expandCandidates(String resolvedId) {
        String base = normalizeId(resolvedId);
        if (base.isEmpty()) return List.of();
        if (hasExtension(base)) return List.of(base);

        List<String> out = new ArrayList<>(MODULE_EXTS.length * 2);
        for (String ext : MODULE_EXTS) out.add(join(base, "index" + ext));
        for (String ext : MODULE_EXTS) out.add(base + ext);
        return out;
    }
}
