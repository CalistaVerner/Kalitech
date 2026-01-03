package org.foxesworld.kalitech.engine.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

public final class JsSyntaxVerifier {

    // Один движок на процесс (дорогая штука, лучше не плодить)
    private static final Engine ENGINE = Engine.create();

    // Context не потокобезопасен -> держим per-thread.
    // Важно: parse не требует HostAccess/IO.
    private static final ThreadLocal<Context> CTX = ThreadLocal.withInitial(() ->
            Context.newBuilder("js")
                    .engine(ENGINE)
                    .allowAllAccess(false)
                    .build()
    );

    private JsSyntaxVerifier() {}

    public static void verify(String jsCode, String virtualName) {
        if (jsCode == null) throw new IllegalArgumentException("jsCode is null");
        String name = (virtualName == null || virtualName.isBlank()) ? "<js>" : virtualName;

        try {
            Source src = Source.newBuilder("js", jsCode, name)
                    .cached(false) // чтобы не держать мегакэш при hot-reload
                    .buildLiteral();

            // parse == синтаксическая проверка; НЕ выполняет код
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