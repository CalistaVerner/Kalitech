package org.foxesworld.kalitech.engine.api.interfaces;

// Author: KΛYLΛ

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

/**
 * Script-facing Camera API (durable, thin).
 *
 * Design rules:
 *  - Java exposes ONLY stable primitives (transform + directions).
 *  - All camera behavior (modes, follow, collision, zoom, smoothing)
 *    lives entirely in Lua.
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
public interface CameraApi {

    // -------------------------------------------------------------------------
    // Position
    // -------------------------------------------------------------------------

    /** Get camera world position as a Lua object: {x,y,z}. */
    @LuaExport
    Object location();

    /** Alias for location(). */
    @LuaExport
    default Object position() {
        return location();
    }

    /** Set camera world position from Lua object {x,y,z}. */
    @LuaExport
    void setLocation(LuaValueRef v);

    /** Set camera world position by components. */
    @LuaExport
    void setLocation(double x, double y, double z);

    /** Alias for setLocation(x,y,z). */
    @LuaExport
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
    @LuaExport
    void setYawPitch(double yaw, double pitch);

    @LuaExport
    void __flush();

    /** Current yaw (radians). */
    @LuaExport
    double yaw();

    /** Current pitch (radians). */
    @LuaExport
    double pitch();

    // -------------------------------------------------------------------------
    // Basis vectors
    // -------------------------------------------------------------------------

    /** Camera forward direction as {x,y,z}. */
    @LuaExport
    Object forward();

    /** Camera right direction as {x,y,z}. */
    @LuaExport
    Object right();

    /** Camera up direction as {x,y,z}. */
    @LuaExport
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
    @LuaExport
    void moveLocal(double dx, double dy, double dz);

    /** Relative motion in world axes. */
    @LuaExport
    void moveWorld(double dx, double dy, double dz);

    /**
     * Incremental rotation (radians): adds to yaw/pitch.
     * Optional clamping may be applied by implementation.
     */
    @LuaExport
    void rotateYawPitch(double dYaw, double dPitch);
}