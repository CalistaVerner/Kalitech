package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface CameraApi {

    @HostAccess.Export void mode(String mode);
    @HostAccess.Export String mode();

    @HostAccess.Export void enabled(boolean on);
    @HostAccess.Export boolean enabled();

    @HostAccess.Export void fly(Value cfg);
}