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

public interface TerrainSplatApi {
    @LuaExport
    public void apply(SurfaceApi.SurfaceHandle var1, LuaValueRef var2);

    @LuaExport
    public Object createMaterial(LuaValueRef var1);
}

