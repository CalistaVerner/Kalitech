// FILE: BuiltinResolver.java
package org.foxesworld.kalitech.engine.script.resolve;

// Author: Calista Verner

import java.util.Optional;

/**
 * Keeps @builtin/* module ids stable.
 * Put it first in ResolverChain so nothing else rewrites built-in ids.
 *
 * Contract:
 *  - If request starts with prefix, returns it as-is (stable).
 *  - Otherwise returns Optional.empty().
 */
public final class BuiltinResolver implements ResolverStrategy {

    private final String prefix;

    public BuiltinResolver(String prefix) {
        this.prefix = (prefix == null || prefix.isBlank()) ? "@builtin/" : prefix;
    }

    @Override
    public Optional<String> resolve(String parentModuleId, String request) {
        if (request == null || request.isEmpty()) return Optional.empty();
        return request.startsWith(prefix) ? Optional.of(request) : Optional.empty();
    }
}