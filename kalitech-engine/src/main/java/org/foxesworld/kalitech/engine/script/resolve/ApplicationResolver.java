package org.foxesworld.kalitech.engine.script.resolve;

import org.foxesworld.kalitech.engine.script.ScriptEntryPoint;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Preserves virtual application module ids under {@code @app/<namespace>/...}.
 */
public final class ApplicationResolver implements ResolverStrategy {

    private static final Pattern NAMESPACE_PATTERN =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");

    @Override
    public Optional<String> resolve(String parentModuleId, String request) {
        if (request == null || request.isBlank()) return Optional.empty();

        String id = PathNorm.normalizeId(request);
        if (!id.startsWith(ScriptEntryPoint.APP_PREFIX)) return Optional.empty();

        String rest = id.substring(ScriptEntryPoint.APP_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) return Optional.empty();

        String namespace = rest.substring(0, slash);
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) return Optional.empty();

        String path = rest.substring(slash + 1);
        for (String segment : path.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                return Optional.empty();
            }
        }

        return Optional.of(id);
    }
}
