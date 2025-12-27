package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface DebugDrawApi {

    @HostAccess.Export void enabled(boolean v);
    @HostAccess.Export boolean enabled();

    @HostAccess.Export void clear();

    @HostAccess.Export void line(Value cfg);
    @HostAccess.Export void ray(Value cfg);
    @HostAccess.Export void axes(Value cfg);

    @HostAccess.Export void tick(double tpf);
}