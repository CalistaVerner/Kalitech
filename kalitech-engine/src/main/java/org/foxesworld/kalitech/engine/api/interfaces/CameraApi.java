package org.foxesworld.kalitech.engine.api.interfaces;

// Author: KΛYLΛ

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * Script-facing Camera API (durable, thin).
 *
 * Design rules:
 *  - Java exposes ONLY stable primitives (transform + directions).
 *  - All camera behavior (modes, follow, collision, zoom, smoothing)
 *    lives entirely in JS.
 *  - No engine-side state machines, no hidden modes.
 *
 * Threading:
 *  - Calls are expected on the world/JME thread via engine dispatcher.
 *
 * Coordinates:
 *  - Right-handed (jME).
 *  - Y is up.
 *  - Yaw around +Y, pitch around +X.
 */
@HostAccess.Implementable
public interface CameraApi {

    // -------------------------------------------------------------------------
    // Position
    // -------------------------------------------------------------------------

    /** Get camera world position as a JS object: {x,y,z}. */
    @HostAccess.Export
    Object location();

    /** Alias for location(). */
    @HostAccess.Export
    default Object position() {
        return location();
    }

    /** Set camera world position from JS object {x,y,z}. */
    @HostAccess.Export
    void setLocation(Value v);

    /** Set camera world position by components. */
    @HostAccess.Export
    void setLocation(double x, double y, double z);

    /** Alias for setLocation(x,y,z). */
    @HostAccess.Export
    default void setPosition(double x, double y, double z) {
        setLocation(x, y, z);
    }

    // -------------------------------------------------------------------------
    // Orientation
    // -------------------------------------------------------------------------

    /**
     * Set absolute yaw/pitch (radians).
     * Convention: yaw around +Y, pitch around +X.
     */
    @HostAccess.Export
    void setYawPitch(double yaw, double pitch);

    @HostAccess.Export
    void __flush();

    /** Current yaw (radians). */
    @HostAccess.Export
    double yaw();

    /** Current pitch (radians). */
    @HostAccess.Export
    double pitch();

    // -------------------------------------------------------------------------
    // Basis vectors
    // -------------------------------------------------------------------------

    /** Camera forward direction as {x,y,z}. */
    @HostAccess.Export
    Object forward();

    /** Camera right direction as {x,y,z}. */
    @HostAccess.Export
    Object right();

    /** Camera up direction as {x,y,z}. */
    @HostAccess.Export
    Object up();

    // -------------------------------------------------------------------------
    // Motion
    // -------------------------------------------------------------------------

    /**
     * Relative motion in camera-local axes:
     *  dx -> right
     *  dy -> up
     *  dz -> forward
     */
    @HostAccess.Export
    void moveLocal(double dx, double dy, double dz);

    /** Relative motion in world axes. */
    @HostAccess.Export
    void moveWorld(double dx, double dy, double dz);

    /**
     * Incremental rotation (radians): adds to yaw/pitch.
     * Optional clamping may be applied by implementation.
     */
    @HostAccess.Export
    void rotateYawPitch(double dYaw, double dPitch);
}