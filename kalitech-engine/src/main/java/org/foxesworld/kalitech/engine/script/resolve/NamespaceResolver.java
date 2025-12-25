package org.foxesworld.kalitech.engine.script.resolve;

// Author: Calista Verner

import java.util.Optional;

public final class NamespaceResolver implements ResolverStrategy {

    private final String modsRoot; // e.g. "Mods"

    public NamespaceResolver(String modsRoot) {
        this.modsRoot = PathNorm.normalizeId(modsRoot == null ? "Mods" : modsRoot);
    }

    @Override
    public Optional<String> resolve(String parentModuleId, String request) {
        if (request == null) return Optional.empty();
        String req = request.trim();

        int colon = req.indexOf(':');
        if (colon <= 0 || colon == req.length() - 1) return Optional.empty();

        String ns = req.substring(0, colon).trim();
        String path = req.substring(colon + 1).trim();

        if (ns.isEmpty() || path.isEmpty()) return Optional.empty();

        // "kalitech:ui" or "kalitech:ui/menu"
        String normalizedPath = PathNorm.normalizeId(path);
        String out = modsRoot + "/" + ns + "/" + normalizedPath;

        return Optional.of(PathNorm.normalizeId(out));
    }
}