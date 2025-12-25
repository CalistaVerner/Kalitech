package org.foxesworld.kalitech.engine.script.resolve;

// Author: Calista Verner

import java.util.Optional;

public final class PassThroughResolver implements ResolverStrategy {

    @Override
    public Optional<String> resolve(String parentModuleId, String request) {
        if (request == null) return Optional.empty();
        String req = PathNorm.normalizeId(request.trim());
        if (req.isEmpty()) return Optional.empty();

        // policy: only allow specific roots (security)
        if (req.startsWith("Scripts/") || req.startsWith("Mods/")) {
            return Optional.of(req);
        }
        return Optional.empty();
    }
}