// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.interfaces.physics;

import org.graalvm.polyglot.HostAccess;

/**
 * Physics facade exposed to JS.
 *
 * <pre>
 * const body = engine.physics().body({
 *   surface: surfaceHandleOrId,
 *   mass: 80,
 *   friction: 0.8,
 *   restitution: 0.1,
 *   damping: { linear:0.05, angular:0.1 },
 *   kinematic: false,
 *   lockRotation: true,
 *   collider: { type:"box" }
 * })
 * </pre>
 */
@SuppressWarnings("unused")
public interface PhysicsApi {

    /**
     * Creates (or returns existing) rigid body bound to a surface.
     */
    @HostAccess.Export
    PhysicsBodyHandle body(Object cfg);

    /**
     * Removes a rigid body by handle or id.
     */
    @HostAccess.Export
    void remove(Object handleOrId);

    /**
     * Performs a raycast in the physics space.
     */
    @HostAccess.Export
    PhysicsRayHit raycast(Object cfg);

    /**
     * Enables or disables Bullet debug draw.
     */
    @HostAccess.Export
    void debug(boolean enabled);

    /**
     * Sets global gravity vector.
     */
    @HostAccess.Export
    void gravity(Object vec3);

    /* ==========================================================
       Engine-internal lifecycle hooks (NOT exported to JS)
       ========================================================== */

    /**
     * Cleans up physics objects bound to a specific surface.
     * Called by SurfaceApi during surface destruction.
     */
    void __cleanupSurface(int surfaceId);

    /**
     * Clears the entire physics state.
     * Called on world reload / hard reset.
     */
    void __clearAll();
}