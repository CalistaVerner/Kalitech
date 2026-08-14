/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi$SurfaceHandle
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface TerrainApi {
    @LuaExport
    public SurfaceApi.SurfaceHandle terrain(LuaValueRef var1);

    @LuaExport
    public SurfaceApi.SurfaceHandle quad(LuaValueRef var1);

    @LuaExport
    public SurfaceApi.SurfaceHandle plane(LuaValueRef var1);

    @LuaExport
    public void attachEntity(SurfaceApi.SurfaceHandle var1, Object var2);

    @LuaExport
    public void detachEntity(SurfaceApi.SurfaceHandle var1);

    @LuaExport
    public void detach(SurfaceApi.SurfaceHandle var1);
}

