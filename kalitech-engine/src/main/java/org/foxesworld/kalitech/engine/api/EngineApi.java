package org.foxesworld.kalitech.engine.api;

import org.graalvm.polyglot.HostAccess;

public interface EngineApi {
    @HostAccess.Export LogApi log();
    @HostAccess.Export AssetsApi assets();
    @HostAccess.Export EventsApi events();
    @HostAccess.Export EntityApi entity();
    @HostAccess.Export String engineVersion();
}