// FILE: org/foxesworld/kalitech/engine/api/interfaces/CameraApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

// Author: Calista Verner

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * Script-facing Camera API (durable, thin).
 *
 * Rule:
 *  - Java provides stable primitives (transform + helpers).
 *  - All camera behaviors (modes/follow/collision/zoom/smoothing) live in JS.
 *
 * Backward compatibility:
 *  - Legacy "mode/fly/followConfig" methods are kept but marked @Deprecated.
 *  - They should not contain core behavior logic; only store config for scripts if needed.
 */
@HostAccess.Implementable
public interface CameraApi {

    // -------------------------------------------------------------------------
    // Core transform primitives (durable)
    // -------------------------------------------------------------------------

    /** Get camera world position as a JS object: {x,y,z}. */
    @HostAccess.Export
    Object location();

    /** Alias for location(). */
    @HostAccess.Export
    default Object position() { return location(); }

    /** Set camera world position from JS object {x,y,z}. */
    @HostAccess.Export
    void setLocation(Value v);

    /** Set camera world position by components. */
    @HostAccess.Export
    void setLocation(double x, double y, double z);

    /** Alias for setLocation(x,y,z). */
    @HostAccess.Export
    default void setPosition(double x, double y, double z) { setLocation(x, y, z); }

    /**
     * Set absolute yaw/pitch (radians).
     * Convention: yaw around +Y, pitch around +X (jME standard via fromAngles(pitch,yaw,0)).
     */
    @HostAccess.Export
    void setYawPitch(double yaw, double pitch);

    /** Current yaw (radians). */
    @HostAccess.Export
    double yaw();

    /** Current pitch (radians). */
    @HostAccess.Export
    double pitch();

    /** Camera forward direction as {x,y,z}. */
    @HostAccess.Export
    Object forward();

    /** Camera right direction as {x,y,z}. */
    @HostAccess.Export
    Object right();

    /** Camera up direction as {x,y,z}. */
    @HostAccess.Export
    Object up();

    /**
     * Relative motion in camera-local axes:
     *  dx -> right, dy -> up, dz -> forward
     */
    @HostAccess.Export
    void moveLocal(double dx, double dy, double dz);

    /** Relative motion in world axes. */
    @HostAccess.Export
    void moveWorld(double dx, double dy, double dz);

    /**
     * Incremental rotation (radians): adds to yaw/pitch.
     * Optional clamp can be applied by implementation.
     */
    @HostAccess.Export
    void rotateYawPitch(double dYaw, double dPitch);

    // -------------------------------------------------------------------------
    // Legacy / compatibility (deprecated)
    // -------------------------------------------------------------------------

    /**
     * Legacy mode switch. Deprecated: scripts should own camera behavior.
     * Keep for old scripts that call engine.camera().mode("fly") etc.
     */
    @Deprecated
    @HostAccess.Export
    void mode(String mode);

    /** @return last set legacy mode string (may be unused). */
    @Deprecated
    @HostAccess.Export
    String mode();

    /**
     * Legacy fly/free config bag. Deprecated: scripts should do input integration in JS.
     * Kept so old scripts don't crash; may store config only.
     */
    @Deprecated
    @HostAccess.Export
    void fly(Value cfg);

    /**
     * Legacy "follow target id". Deprecated: scripts should call setLocation/setYawPitch directly.
     * Kept for convenience/interop with older systems that still store followBodyId in state.
     */
    @Deprecated
    @HostAccess.Export
    void follow(int bodyId);

    /**
     * Legacy follow config (offsets, distance). Deprecated.
     * Kept so existing Scripts/camera/modes.js can set these values if desired.
     */
    @Deprecated
    @HostAccess.Export
    void followConfig(Value cfg);

    /** Enable/disable camera updates in legacy pipeline. Deprecated. */
    @Deprecated
    @HostAccess.Export
    void enabled(boolean enabled);

    /** @return enabled flag in legacy pipeline. Deprecated. */
    @Deprecated
    @HostAccess.Export
    boolean enabled();
}