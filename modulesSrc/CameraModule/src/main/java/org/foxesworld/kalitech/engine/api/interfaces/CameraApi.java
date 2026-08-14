/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport$Implementable
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface CameraApi {
    @LuaExport
    public Object location();

    @LuaExport
    default public Object position() {
        return this.location();
    }

    @LuaExport
    public void setLocation(LuaValueRef var1);

    @LuaExport
    public void setLocation(double var1, double var3, double var5);

    @LuaExport
    default public void setPosition(double x, double y, double z) {
        this.setLocation(x, y, z);
    }

    @LuaExport
    public void setYawPitch(double var1, double var3);

    @LuaExport
    public double yaw();

    @LuaExport
    public double pitch();

    @LuaExport
    public Object forward();

    @LuaExport
    public Object right();

    @LuaExport
    public Object up();

    @LuaExport
    public void moveLocal(double var1, double var3, double var5);

    @LuaExport
    public void moveWorld(double var1, double var3, double var5);

    @LuaExport
    public void rotateYawPitch(double var1, double var3);
}

