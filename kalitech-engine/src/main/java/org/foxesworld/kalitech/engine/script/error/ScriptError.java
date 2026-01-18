package org.foxesworld.kalitech.engine.script.error;

/**
 * Structured script error.
 */
public final class ScriptError {

    public final Type type;
    public final String systemId;
    public final Throwable cause;
    public ScriptError(Type type, String systemId, Throwable cause) {
        this.type = type;
        this.systemId = systemId;
        this.cause = cause;
    }

    public enum Type {
        INIT,
        UPDATE,
        DESTROY,
        JOB,
        RUNTIME
    }
}