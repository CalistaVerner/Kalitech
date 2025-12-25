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
}