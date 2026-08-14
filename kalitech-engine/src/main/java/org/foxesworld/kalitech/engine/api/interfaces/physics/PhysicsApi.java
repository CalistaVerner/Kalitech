// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.api.interfaces.physics;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

/**
 * Physics facade exposed to Lua.
 *
 * <p>All methods annotated with {@link LuaExport} are part of the public scripting surface.</p>
 *
 * <pre>
 * const PH = engine.physics();
 * const body = PH.body({ type:"capsule", radius:0.35, height:1.6, mass:80, surface: surfaceId });
 * PH.warp(body, {x:0,y:5,z:0});
 * PH.velocity(body, {x:0,y:0,z:5});
 * </pre>
 */
public interface PhysicsApi {

    /* ==========================================================
       Exported to Lua
       ========================================================== */

    @LuaExport
    PhysicsBodyHandle body(Object cfg);

    @LuaExport
    int bodyOfSurface(int surfaceId);

    @LuaExport
    PhysicsBodyHandle handle(int bodyId);

    @LuaExport
    boolean exists(int bodyId);

    @LuaExport
    void remove(Object handleOrId);

    @LuaExport
    PhysicsRayHit raycast(Object cfg);

    @LuaExport
    Object raycastEx(Object cfg);

    @LuaExport
    Object raycastAll(Object cfg);

    @LuaExport
    Object position(Object handleOrId);

    @LuaExport
    void warp(Object handleOrId, Object vec3);

    @LuaExport
    Object velocity(Object handleOrId);

    @LuaExport
    void velocity(Object handleOrId, Object vec3);

    @LuaExport
    void yaw(Object handleOrId, double yaw);

    @LuaExport
    void applyImpulse(Object handleOrId, Object vec3);

    @LuaExport
    void lockRotation(Object handleOrId, boolean lock);

    @LuaExport
    void setKinematic(Object handleOrId, boolean kinematic);

    @LuaExport
    void collisionGroups(Object handleOrId, int group, int mask);

    @LuaExport
    void applyCentralForce(Object handleOrId, Object vec3);

    @LuaExport
    void applyTorque(Object handleOrId, Object vec3);

    @LuaExport
    Object angularVelocity(Object handleOrId);

    @LuaExport
    void angularVelocity(Object handleOrId, Object vec3);

    @LuaExport
    void clearForces(Object handleOrId);

    @LuaExport
    void debug(boolean enabled);

    @LuaExport
    void gravity(Object vec3);

    /* ==========================================================
       Engine-internal lifecycle hooks (NOT exported to Lua)
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