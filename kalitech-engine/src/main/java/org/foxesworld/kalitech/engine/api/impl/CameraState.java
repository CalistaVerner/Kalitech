package org.foxesworld.kalitech.engine.api.impl;

// Author: Calista Verner

import com.jme3.math.Vector3f;

/** Data-only camera state shared between CameraApiImpl and CameraSystem. */
public final class CameraState {

    public volatile boolean enabled = true;

    /** Supported: off, fly/free, firstPerson, thirdPerson */
    public volatile String mode = "free";

    // ---------- free/fly ----------
    public volatile double speed = 90.0;
    public volatile double accel = 18.0;
    public volatile double drag = 6.5;
    public volatile double smoothing = 0.15;
    public volatile double lookSensitivity = 0.002;
    public volatile boolean invertY = false;
    public volatile boolean invertX = false;
    public volatile boolean invertMoveX = false;
    public volatile boolean invertMoveZ = false;

    // ---------- follow target ----------
    /** Physics body id to follow (0 = none). */
    public volatile int followBodyId = 0;

    /** First-person camera offset from body position (meters). */
    public final Vector3f firstPersonOffset = new Vector3f(0f, 1.65f, 0f);

    /** Third-person camera parameters. */
    public volatile float thirdPersonDistance = 3.4f;
    public volatile float thirdPersonMinDistance = 1.2f;
    public volatile float thirdPersonMaxDistance = 8.0f;

    public volatile float thirdPersonHeight = 1.55f;
    public volatile float thirdPersonSide = 0.25f;

    // ---------- zoom ----------
    /** Mouse wheel zoom speed. */
    public volatile float zoomSpeed = 1.0f;

    // ---------- spring / lag ----------
    /** 0..1 : 0 = very snappy, 1 = very smooth */
    public volatile float followSmoothing = 0.18f;

    // ---------- collision ----------
    public volatile boolean thirdPersonCollision = true;
    /** Small padding from obstacles. */
    public volatile float collisionPadding = 0.15f;
}