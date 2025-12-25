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

        for (Map.Entry<String, String> e : aliases.entrySet()) {
            String prefix = e.getKey();
            String base = trimTrailingSlash(e.getValue());

            // require("@core")
            if (request.equals(prefix)) {
                return Optional.of(base);
            }

            // require("@core/...")
            if (request.startsWith(prefix + "/")) {
                String tail = request.substring(prefix.length() + 1);

                // IMPORTANT: dots in module names are allowed (editor.grid.system),
                // so "extension" must be detected by known suffix, not by any dot.
                if (!hasKnownExtension(tail)) {
                    tail = tail + ".js";
                }

                return Optional.of(base + "/" + tail);
            }
        }

        return Optional.empty();
    }

    private static boolean hasKnownExtension(String path) {
        String p = path.toLowerCase();

        // keep this list tight: only real extensions you support
        return p.endsWith(".js")
                || p.endsWith(".mjs")
                || p.endsWith(".cjs")
                || p.endsWith(".json");
    }

    private static String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}