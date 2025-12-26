// FILE: org/foxesworld/kalitech/engine/api/interfaces/CameraApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

// Author: Calista Verner

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * Script-facing Camera API.
 *
 * Design goals:
 *  - minimal surface for scripts (no hard engine coupling)
 *  - deterministic camera modes: free / firstPerson / thirdPerson
 *  - follow target configured once; CameraSystem drives motion (spring/lag, collision, zoom)
 */
public interface CameraApi {

    // ---------------- lifecycle / mode ----------------

    /**
     * Camera mode:
     *  - "free" (aka fly/free camera)
     *  - "firstPerson"
     *  - "thirdPerson"
     *  - "off"
     */
    @HostAccess.Export
    void mode(String mode);

    @HostAccess.Export
    String mode();

    @HostAccess.Export
    void enabled(boolean on);

    @HostAccess.Export
    boolean enabled();

    // ---------------- free/fly camera ----------------

    /**
     * Fly camera configuration (also used for look config in follow modes).
     * cfg fields (optional):
     *  enabled:boolean
     *  speed:number            (0 allowed; follow modes can lock translation)
     *  accel:number
     *  drag:number
     *  smoothing:number        (0..1)
     *  lookSensitivity:number
     *  invertY:boolean
     *  invertX:boolean
     *  invertMoveX:boolean
     *  invertMoveZ:boolean
     */
    @HostAccess.Export
    void fly(Value cfg);

    // ---------------- follow target (Java-driven follow) ----------------

    /**
     * Set physics body id as follow target (0 = none).
     * CameraSystem will compute and apply camera transforms each tick depending on mode.
     */
    @HostAccess.Export
    void followBody(int bodyId);

    /** Clear follow target. */
    @HostAccess.Export
    void clearFollow();

    /**
     * Follow configuration (optional).
     * cfg fields (all optional):
     *  firstPersonOffset:{x,y,z}
     *  thirdPersonDistance:number
     *  thirdPersonMinDistance:number
     *  thirdPersonMaxDistance:number
     *  thirdPersonHeight:number
     *  thirdPersonSide:number
     *  followSmoothing:number          (0..1)
     *  zoomSpeed:number
     *  thirdPersonCollision:boolean
     *  collisionPadding:number
     */
    @HostAccess.Export
    void followConfig(Value cfg);

    // ---------------- optional direct control (debug/tools) ----------------
    // These are useful for editor tools / cutscenes; gameplay should prefer follow modes.

    /** Set absolute camera location {x,y,z}. */
    @HostAccess.Export
    void setLocation(Value vec3);

    /** Alias. */
    @HostAccess.Export
    default void setPosition(Value vec3) { setLocation(vec3); }

    /** Get camera location {x,y,z}. */
    @HostAccess.Export
    Object location();

    /** Alias. */
    @HostAccess.Export
    default Object position() { return location(); }

    /** Set absolute yaw/pitch (radians). */
    @HostAccess.Export
    void setYawPitch(double yaw, double pitch);

    @HostAccess.Export
    double yaw();

    @HostAccess.Export
    double pitch();

    @HostAccess.Export
    Object forward();
}