package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface TerrainApi {

    @LuaExport
    SurfaceApi.SurfaceHandle terrain(LuaValueRef cfg);

    @LuaExport
    SurfaceApi.SurfaceHandle quad(LuaValueRef cfg);

    @LuaExport
    SurfaceApi.SurfaceHandle plane(LuaValueRef cfg);

    //  ECS attach/detach (UUID-only)
    @LuaExport
    void attachEntity(SurfaceApi.SurfaceHandle handle, Object entityUuid);

    @LuaExport
    void detachEntity(SurfaceApi.SurfaceHandle handle);

    @LuaExport
    void detach(SurfaceApi.SurfaceHandle handle);
}
