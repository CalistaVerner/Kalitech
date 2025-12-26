// FILE: org/foxesworld/kalitech/engine/api/impl/CameraState.java
package org.foxesworld.kalitech.engine.api.impl;

// Author: Calista Verner

import com.jme3.math.Vector3f;

/**
 * Data-only camera state.
 *
 * NOTE:
 *  - This state exists mostly for legacy bridging and optional script convenience.
 *  - The long-term plan: scripts drive camera directly using CameraApi primitives,
 *    so most of these fields become optional/legacy.
 */
public final class CameraState {

    // -------------------------------------------------------------------------
    // Legacy pipeline switch
    // -------------------------------------------------------------------------
    public volatile boolean enabled = true;

    /**
     * Legacy mode string. Scripts may set it, but Java must not hardcode behavior.
     * Supported historically: "free", "fly", "firstPerson", "thirdPerson", "off"
     */
    public volatile String mode = "free";

    // -------------------------------------------------------------------------
    // "Fly/free" tuning (legacy bag; JS can still read/write)
    // -------------------------------------------------------------------------
    public volatile double speed = 90.0;
    public volatile double accel = 18.0;
    public volatile double drag = 6.5;

    /** 0..1: higher = smoother (more lag). */
    public volatile double smoothing = 0.15;

    public volatile double lookSensitivity = 0.002;

    public volatile boolean invertY = false;
    public volatile boolean invertX = false;

    public volatile boolean invertMoveX = false;
    public volatile boolean invertMoveZ = false;

    // -------------------------------------------------------------------------
    // Follow target (legacy bridge)
    // -------------------------------------------------------------------------

    /** Physics body id to follow (0 = none). */
    public volatile int followBodyId = 0;

    /** First-person camera offset from body position (meters). */
    public final Vector3f firstPersonOffset = new Vector3f(0f, 1.65f, 0f);

    // -------------------------------------------------------------------------
    // Third-person parameters (legacy bridge)
    // -------------------------------------------------------------------------
    public volatile float thirdPersonDistance = 3.4f;
    public volatile float thirdPersonMinDistance = 0.8f;
    public volatile float thirdPersonMaxDistance = 10.0f;

    public volatile float thirdPersonHeight = 1.55f;
    public volatile float thirdPersonSide = 0.25f;

    // -------------------------------------------------------------------------
    // Zoom (legacy bridge)
    // -------------------------------------------------------------------------
    public volatile float zoomSpeed = 1.0f;

    // -------------------------------------------------------------------------
    // Follow smoothing (legacy bridge)
    // -------------------------------------------------------------------------
    /** 0..1: 0 = snappy, 1 = very smooth. */
    public volatile float followSmoothing = 0.18f;

    // -------------------------------------------------------------------------
    // Collision (legacy bridge)
    // -------------------------------------------------------------------------
    public volatile boolean thirdPersonCollision = true;
    public volatile float collisionPadding = 0.15f;

    // -------------------------------------------------------------------------
    // Pitch clamp (useful even for thin API helpers)
    // -------------------------------------------------------------------------
    /** Clamp pitch to [-pitchLimit, +pitchLimit] radians. */
    public volatile float pitchLimit = (float) (Math.PI * 0.49); // ~88 deg
}