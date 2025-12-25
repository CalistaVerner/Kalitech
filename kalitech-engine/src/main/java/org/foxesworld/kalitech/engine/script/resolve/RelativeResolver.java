package org.foxesworld.kalitech.engine.script.resolve;

// Author: Calista Verner

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class RelativeResolver implements ResolverStrategy {

    @Override
    public Optional<String> resolve(String parentModuleId, String request) {
        if (request == null) return Optional.empty();

        String req = request.trim().replace('\\', '/');
        if (!(req.startsWith("./") || req.startsWith("../"))) return Optional.empty();

        String parentDir = PathNorm.dirnameOf(parentModuleId);
        Deque<String> parts = new ArrayDeque<>();

        if (!parentDir.isEmpty()) {
            for (String p : parentDir.split("/")) {
                if (!p.isEmpty()) parts.addLast(p);
            }
        }

        for (String p : req.split("/")) {
            if (p.isEmpty() || ".".equals(p)) continue;
            if ("..".equals(p)) {
                if (!parts.isEmpty()) parts.removeLast();
            } else {
                parts.addLast(p);
            }
        }

        StringBuilder sb = new StringBuilder();
        var it = parts.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) sb.append('/');
        }

        String out = PathNorm.normalizeId(sb.toString());
        return out.isEmpty() ? Optional.empty() : Optional.of(out);
    }
}