package org.foxesworld.kalitech.engine.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

public final class JsSyntaxVerifier {

    private static final Engine ENGINE = Engine.create();

    // Context is not thread-safe, keep per-thread.
    // Parsing does not require HostAccess/IO.
    private static final ThreadLocal<Context> CTX = ThreadLocal.withInitial(() ->
            Context.newBuilder("js")
                    .engine(ENGINE)
                    .allowAllAccess(false)
                    .build()
    );

    private JsSyntaxVerifier() {
    }

    public static void verify(String jsCode, String virtualName) {
        if (jsCode == null) throw new IllegalArgumentException("jsCode is null");
        String name = (virtualName == null || virtualName.isBlank()) ? "<js>" : virtualName;

        try {
            Source src = Source.newBuilder("js", jsCode, name).cached(false).buildLiteral();
            // parse performs syntax validation; it does not execute code
            CTX.get().parse(src);

        } catch (PolyglotException pe) {
            String loc = "";
            if (pe.getSourceLocation() != null) {
                loc = " @ " + pe.getSourceLocation().getSource().getName()
                        + ":" + pe.getSourceLocation().getStartLine()
                        + ":" + pe.getSourceLocation().getStartColumn();
            }
            throw new IllegalArgumentException("JS syntax error" + loc + ": " + pe.getMessage(), pe);
        } catch (Throwable t) {
            throw new IllegalStateException("JS verification failed: " + name, t);
        }
    }
}
