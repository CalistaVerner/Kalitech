package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

public interface LogApi {
    @HostAccess.Export void info(String msg);
    @HostAccess.Export void warn(String msg);
    @HostAccess.Export void error(String msg);
    @HostAccess.Export void debug(String msg);
}