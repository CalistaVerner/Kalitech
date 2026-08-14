package org.foxesworld.kalitech.engine.script;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Shared failure policy for callbacks entered from Lua.
 *
 * <p>Ordinary script failures, including Lua errors and stack overflows, are isolated by
 * their owner. Conditions that indicate a damaged JVM or broken engine linkage are never
 * hidden.</p>
 */
public final class ScriptFailureBoundary {

    private ScriptFailureBoundary() {
    }

    public static void rethrowIfFatal(Throwable failure) {
        if (failure == null) return;

        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            if (current instanceof ThreadDeath fatal) throw fatal;
            if (current instanceof OutOfMemoryError fatal) throw fatal;
            if (current instanceof InternalError fatal) throw fatal;
            if (current instanceof UnknownError fatal) throw fatal;
            if (current instanceof LinkageError fatal) throw fatal;
            if (current instanceof InterruptedException) Thread.currentThread().interrupt();
            current = current.getCause();
        }
    }

    public static String summary(Throwable failure) {
        if (failure == null) return "unknown script failure";
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return message;
    }
}
