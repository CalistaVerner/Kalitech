package org.foxesworld.kalitech.engine.api.impl;

public final class CameraState {
    public volatile String mode = "off";
    public volatile boolean enabled = true;

    public volatile double speed = 30.0;
    public volatile double accel = 18.0;
    public volatile double drag = 7.0;
    public volatile double smoothing = 0.10;

    public volatile double lookSensitivity = 0.12;
    public volatile boolean invertY = false;
    public volatile boolean invertX = false; // ✅ NEW
    public volatile boolean invertMoveX = false; // A/D
    public volatile boolean invertMoveZ = false; // W/S

}
