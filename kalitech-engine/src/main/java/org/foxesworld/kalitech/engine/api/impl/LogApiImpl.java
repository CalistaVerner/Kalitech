package org.foxesworld.kalitech.engine.api.impl;

import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.api.interfaces.LogApi;

import java.util.Objects;

public final class LogApiImpl implements LogApi {

    private final Logger log;

    public LogApiImpl(EngineApiImpl engineApi) {
        this.log = engineApi.getLog();
    }

    @HostAccess.Export @Override public void info(String msg)  { log.info("{}", msg); }
    @HostAccess.Export @Override public void warn(String msg)  { log.warn("{}", msg); }
    @HostAccess.Export @Override public void error(String msg) { log.error("{}", msg); }
    @HostAccess.Export @Override public void debug(String msg) { log.debug("{}", msg); }
    @HostAccess.Export @Override public void unformatted(String msg) { System.out.println(msg);}
}