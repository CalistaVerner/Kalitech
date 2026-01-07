// FILE: org/foxesworld/kalitech/engine/api/impl/LogApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.LogApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.graalvm.polyglot.HostAccess;

public final class LogApiImpl extends AbstractApiModule implements LogApi {

    private Logger sink;

    public LogApiImpl() {
        super("log", "Log", "1.0.0");
    }

    @Override
    public void attach(org.foxesworld.kalitech.engine.api.module.ApiContext ctx) {
        super.attach(ctx);
        this.sink = ctx.log;
    }

    @HostAccess.Export
    @Override
    public void info(String msg) {
        profiledVoid(() -> sink.info("{}", msg));
    }

    @HostAccess.Export
    @Override
    public void warn(String msg) {
        profiledVoid(() -> sink.warn("{}", msg));
    }

    @HostAccess.Export
    @Override
    public void error(String msg) {
        profiledVoid(() -> sink.error("{}", msg));
    }

    @HostAccess.Export
    @Override
    public void debug(String msg) {
        profiledVoid(() -> sink.debug("{}", msg));
    }

    @HostAccess.Export
    @Override
    public void unformatted(String msg) {
        profiledVoid(() -> System.out.println(msg));
    }
}