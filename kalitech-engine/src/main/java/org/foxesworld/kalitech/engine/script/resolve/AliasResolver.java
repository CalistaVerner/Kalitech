package org.foxesworld.kalitech.engine.script.resolve;

// Author: Calista Verner

import java.util.Map;
import java.util.Optional;

public final class AliasResolver implements ResolverStrategy {

    private final Map<String, String> aliases; // "@core" -> "Scripts/core"

    public AliasResolver(Map<String, String> aliases) {
        this.aliases = aliases;
    }

    @Override
    public Optional<String> resolve(String parentModuleId, String request) {
        if (request == null) return Optional.empty();
        String req = request.trim();

        if (!req.startsWith("@")) return Optional.empty();

        // match longest alias
        String best = null;
        for (String a : aliases.keySet()) {
            if (req.equals(a) || req.startsWith(a + "/")) {
                if (best == null || a.length() > best.length()) best = a;
            }
        }
        if (best == null) return Optional.empty();

        String base = PathNorm.normalizeId(aliases.get(best));
        String tail = req.length() == best.length() ? "" : req.substring(best.length() + 1); // skip "/"
        String out = tail.isEmpty() ? base : (base + "/" + PathNorm.normalizeId(tail));

        // policy: default extension
        if (!out.endsWith(".js")) out += ".js";
        return Optional.of(out);
    }
}