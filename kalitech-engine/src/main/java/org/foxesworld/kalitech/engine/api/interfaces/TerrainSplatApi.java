package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface TerrainSplatApi {

    /**
     * Apply splat params to an existing terrain surface.
     * cfg:
     *  {
     *    alpha: "Textures/terrain/alpha.png",
     *    layers: [ {tex:"...", scale:64}, ... ],
     *    material: <MaterialHandle> (optional; if omitted, will create TerrainLighting material)
     *  }
     */
    @LuaExport
    void apply(SurfaceApi.SurfaceHandle terrainHandle, LuaValueRef cfg);

    /**
     * Convenience: create TerrainLighting.j3md configured for splat.
     */
    @LuaExport
    Object createMaterial(LuaValueRef cfg);
}