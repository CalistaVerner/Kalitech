package org.foxesworld.kalitech.engine.script.resolve;

// Author: Calista Verner

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
}