/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport$Implementable
 */
package org.foxesworld.kalitech.engine.api.interfaces.physics;

import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public interface PhysicsApi {
    @LuaExport
    public PhysicsBodyHandle body(Object var1);

    @LuaExport
    public int bodyOfSurface(int var1);

    @LuaExport
    public PhysicsBodyHandle handle(int var1);

    @LuaExport
    public boolean exists(int var1);

    @LuaExport
    public void remove(Object var1);

    @LuaExport
    public PhysicsRayHit raycast(Object var1);

    @LuaExport
    public Object raycastEx(Object var1);

    @LuaExport
    public Object raycastAll(Object var1);

    @LuaExport
    public Object position(Object var1);

    @LuaExport
    public void warp(Object var1, Object var2);

    @LuaExport
    public Object velocity(Object var1);

    @LuaExport
    public void velocity(Object var1, Object var2);

    @LuaExport
    public void yaw(Object var1, double var2);

    @LuaExport
    public void applyImpulse(Object var1, Object var2);

    @LuaExport
    public void lockRotation(Object var1, boolean var2);

    @LuaExport
    public void setKinematic(Object var1, boolean var2);

    @LuaExport
    public void collisionGroups(Object var1, int var2, int var3);

    @LuaExport
    public void applyCentralForce(Object var1, Object var2);

    @LuaExport
    public void applyTorque(Object var1, Object var2);

    @LuaExport
    public Object angularVelocity(Object var1);

    @LuaExport
    public void angularVelocity(Object var1, Object var2);

    @LuaExport
    public void clearForces(Object var1);

    @LuaExport
    public void debug(boolean var1);

    @LuaExport
    public void gravity(Object var1);

    public void __cleanupSurface(int var1);

    public void __clearAll();
}

