package org.foxesworld.kalitech.engine.script.resolve;

// Author: Calista Verner

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ResolverChain {

    private final List<ResolverStrategy> chain = new ArrayList<>();

    public ResolverChain add(ResolverStrategy r) {
        chain.add(Objects.requireNonNull(r));
        return this;
    }

    /**
     * Returns canonical base id (normalized), without applying "index.js / .js" expansion.
     */
    public String resolveOrThrow(String parentModuleId, String request) {
        String req = request == null ? "" : request.trim();
        for (ResolverStrategy r : chain) {
            Optional<String> out = r.resolve(parentModuleId, req);
            if (out.isPresent()) {
                return PathNorm.normalizeId(out.get());
            }
        }
        throw new IllegalArgumentException("Unresolved require: '" + req + "' from '" + parentModuleId + "'");
    }

    /**
     * Returns candidate ids in strict order:
     *  - if resolved has extension -> [resolved]
     *  - else -> [resolved/index.js, resolved.js]
     *
     * Existence check is done by runtime (I/O layer).
     */
    public List<String> resolveCandidatesOrThrow(String parentModuleId, String request) {
        String base = resolveOrThrow(parentModuleId, request);
        return PathNorm.expandCandidates(base);
    }
}