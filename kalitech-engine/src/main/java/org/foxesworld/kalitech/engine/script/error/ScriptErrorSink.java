package org.foxesworld.kalitech.engine.script.error;

/**
 * Centralized error sink for script/runtime failures.
 */
public interface ScriptErrorSink {

    void onError(ScriptError error);
}