// FILE: MutableAliasResolver.java
package org.foxesworld.kalitech.engine.script.resolve;

// Author: Calista Verner

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableAliasResolver implements ResolverStrategy {

    private final AtomicReference<Map<String, String>> aliasesRef =
            new AtomicReference<>(Map.of());

    public void setAliases(Map<String, String> aliases) {
        aliasesRef.set(Map.copyOf(Objects.requireNonNull(aliases, "aliases")));
    }

    public Map<String, String> getAliases() {
        return aliasesRef.get();
    }

    @Override
    public Optional<String> resolve(String parentModuleId, String request) {
        if (request == null || request.isEmpty()) return Optional.empty();

        Map<String, String> aliases = aliasesRef.get();
        if (aliases.isEmpty()) return Optional.empty();

        String req = request.trim();

        for (Map.Entry<String, String> e : aliases.entrySet()) {
            String prefix = e.getKey();
            String base = trimTrailingSlash(e.getValue());

            // require("@core")
            if (req.equals(prefix)) {
                return Optional.of(PathNorm.normalizeId(base));
            }

            // require("@core/...")
            if (req.startsWith(prefix + "/")) {
                String tail = req.substring(prefix.length() + 1);
                // DO NOT force .js here — runtime will expand candidates:
                //   base/tail/index.js then base/tail.js
                return Optional.of(PathNorm.normalizeId(base + "/" + tail));
            }
        }

        return Optional.empty();
    }

    private static String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}