package org.foxesworld.kalitech.engine.script.resolve;

// Author: KΛYLΛ

import java.util.Optional;

public final class PassThroughResolver implements ResolverStrategy {

    @Override
    public Optional<String> resolve(String parentModuleId, String request) {
        if (request == null) return Optional.empty();
        String req = PathNorm.normalizeId(request.trim());
        if (req.isEmpty()) return Optional.empty();

        // Direct application asset paths are intentionally forbidden.
        // Application code must use @app/<namespace>/... so cache and dependency
        // identities cannot collide across projects.
        if (req.startsWith("Mods/")) {
            return Optional.of(req);
        }
        return Optional.empty();
    }
}