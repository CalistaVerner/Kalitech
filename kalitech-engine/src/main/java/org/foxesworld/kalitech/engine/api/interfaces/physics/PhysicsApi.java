// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.api.interfaces.physics;

import org.graalvm.polyglot.HostAccess;

/**
 * Physics facade exposed to JS.
 *
 * <p>All methods annotated with {@link HostAccess.Export} are part of the public scripting surface.</p>
 *
 * <pre>
 * const PH = engine.physics();
 * const body = PH.body({ type:"capsule", radius:0.35, height:1.6, mass:80, surface: surfaceId });
 * PH.warp(body, {x:0,y:5,z:0});
 * PH.velocity(body, {x:0,y:0,z:5});
 * </pre>
 */
@HostAccess.Implementable
public interface PhysicsApi {

    /* ==========================================================
       Exported to JS
       ========================================================== */

    @HostAccess.Export
    PhysicsBodyHandle body(Object cfg);

    @HostAccess.Export
    int bodyOfSurface(int surfaceId);

    @HostAccess.Export
    PhysicsBodyHandle handle(int bodyId);

    @HostAccess.Export
    boolean exists(int bodyId);

    @HostAccess.Export
    void remove(Object handleOrId);

    @HostAccess.Export
    PhysicsRayHit raycast(Object cfg);

    @HostAccess.Export
    Object raycastEx(Object cfg);

    @HostAccess.Export
    Object raycastAll(Object cfg);

    @HostAccess.Export
    Object position(Object handleOrId);

    @HostAccess.Export
    void warp(Object handleOrId, Object vec3);

    @HostAccess.Export
    Object velocity(Object handleOrId);

    @HostAccess.Export
    void velocity(Object handleOrId, Object vec3);

    @HostAccess.Export
    void yaw(Object handleOrId, double yaw);

    @HostAccess.Export
    void applyImpulse(Object handleOrId, Object vec3);

    @HostAccess.Export
    void lockRotation(Object handleOrId, boolean lock);

    @HostAccess.Export
    void setKinematic(Object handleOrId, boolean kinematic);

    @HostAccess.Export
    void collisionGroups(Object handleOrId, int group, int mask);

    @HostAccess.Export
    void applyCentralForce(Object handleOrId, Object vec3);

    @HostAccess.Export
    void applyTorque(Object handleOrId, Object vec3);

    @HostAccess.Export
    Object angularVelocity(Object handleOrId);

    @HostAccess.Export
    void angularVelocity(Object handleOrId, Object vec3);

    @HostAccess.Export
    void clearForces(Object handleOrId);

    @HostAccess.Export
    void debug(boolean enabled);

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