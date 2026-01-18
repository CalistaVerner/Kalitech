package org.foxesworld.kalitech.engine.script.error;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DefaultScriptErrorSink implements ScriptErrorSink {

    private static final Logger log = LogManager.getLogger("ScriptErrors");

    @Override
    public void onError(ScriptError error) {
        log.error(
                "[script-error] type={} system={}",
                error.type,
                error.systemId,
                error.cause
        );
    }
}