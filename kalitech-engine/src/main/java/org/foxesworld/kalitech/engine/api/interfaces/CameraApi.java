package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

public interface CameraApi {
    @HostAccess.Export void setLocation(double x, double y, double z);
    @HostAccess.Export void lookAt(double x, double y, double z, double upX, double upY, double upZ);

    @HostAccess.Export void setFlyEnabled(boolean enabled);
    @HostAccess.Export void setFlySpeed(double speed);
}