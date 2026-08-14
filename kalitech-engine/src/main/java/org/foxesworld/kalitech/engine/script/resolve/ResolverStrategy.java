// FILE: ResolverStrategy.java
package org.foxesworld.kalitech.engine.script.resolve;

// Author: KΛYLΛ

import java.util.Optional;

/**
 * Strategy interface for resolving Lua require() requests to a canonical moduleId.
 * <p>
 * Contract:
 * - Implementations MUST be pure: no I/O, no caching, no side effects.
 * - Return Optional.empty() if not applicable.
 * - Return a module id (not necessarily normalized) if applicable.
 * - Caller (ResolverChain / runtime) will normalize the final id.
 * <p>
 * Notes:
 * - parentModuleId can be "" for root/global require().
 * - request is the raw require("...") string after basic trimming.
 */
@FunctionalInterface
public interface ResolverStrategy {

    /**
     * @param parentModuleId current module (who calls require), may be "" for root
     * @param request        raw require("...") string
     * @return resolved moduleId (e.g. "Scripts/core/math.lua") or Optional.empty() if not applicable
     */
    Optional<String> resolve(String parentModuleId, String request);
}