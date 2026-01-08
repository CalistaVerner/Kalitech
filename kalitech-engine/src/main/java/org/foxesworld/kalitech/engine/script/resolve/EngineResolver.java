// FILE: EngineResolver.java
package org.foxesworld.kalitech.engine.script.resolve;

import java.util.Optional;

/**
 * Maps virtual engine namespace "@engine/*" to built-in resource namespace "@builtin/engine/*".
 * <p>
 * This lets engine internals live in resources (InputStream) while scripts can require("@engine/...").
 */
public final class EngineResolver implements ResolverStrategy {

    private final String enginePrefix;        // "@engine" or "@engine/"
    private final String builtinEnginePrefix; // "@builtin/engine"

    public EngineResolver() {
        this("@engine", "@builtin/engine");
    }

    public EngineResolver(String enginePrefix, String builtinEnginePrefix) {
        String ep = (enginePrefix == null || enginePrefix.isBlank()) ? "@engine" : enginePrefix.trim();
        if (ep.endsWith("/")) ep = ep.substring(0, ep.length() - 1);
        this.enginePrefix = ep;

        String bp = (builtinEnginePrefix == null || builtinEnginePrefix.isBlank())
                ? "@builtin/engine"
                : builtinEnginePrefix.trim();
        while (bp.endsWith("/")) bp = bp.substring(0, bp.length() - 1);
        this.builtinEnginePrefix = bp;
    }

    @Override
    public Optional<String> resolve(String parentModuleId, String request) {
        if (request == null || request.isEmpty()) return Optional.empty();
        String req = request.trim();

        // require("@engine") -> "@builtin/engine"
        if (req.equals(enginePrefix)) {
            return Optional.of(builtinEnginePrefix);
        }

        // require("@engine/...") -> "@builtin/engine/..."
        String prefixWithSlash = enginePrefix + "/";
        if (req.startsWith(prefixWithSlash)) {
            String tail = req.substring(prefixWithSlash.length());
            if (tail.isEmpty()) return Optional.of(builtinEnginePrefix);
            return Optional.of(PathNorm.normalizeId(builtinEnginePrefix + "/" + tail));
        }

        return Optional.empty();
    }
}