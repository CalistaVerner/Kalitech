package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface AssetsApi {

    @LuaExport
    String readText(String assetPath);

    @LuaExport
    String readLuaVerified(String assetPath);

    @LuaExport
    SurfaceApi.SurfaceHandle loadModel(String assetPath, LuaValueRef cfg);
}
