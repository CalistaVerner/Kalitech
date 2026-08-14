// FILE: RenderApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

/**
 * Render API.
 * Lua-first: all high-level behavior lives in scripts; Java exposes stable primitives.
 */
public interface RenderApi {

    /**
     * Ensures base render scene contract is created (lights, post pipeline root, etc.).
     * Idempotent.
     */
    @LuaExport
    void ensureScene();

    // ---------------------------
    // Lighting (config style only)
    // ---------------------------

    void __resetWorldCache(String reason);

    /**
     * Ambient light configuration.
     * Expected fields:
     *   - r,g,b (or color:{r,g,b}) in 0..1
     *   - intensity (>=0)
     */
    @LuaExport
    void ambientCfg(LuaValueRef cfg);

    /**
     * Sun (directional) light configuration.
     * Expected fields:
     *   - dir: [x,y,z] or {x,y,z}
     *   - color: [r,g,b] or {r,g,b}
     *   - intensity (>=0)
     */
    @LuaExport
    void sunCfg(LuaValueRef cfg);

    // ---------------------------
    // Shadows (minimal)
    // ---------------------------

    /**
     * Same as sunShadows(mapSize), but accepts cfg:
     *   - mapSize
     */
    @LuaExport
    void sunShadowsCfg(LuaValueRef cfg);

    // ---------------------------
    // Atmosphere / scene visuals
    // ---------------------------

    /**
     * Fog configuration.
     * Expected fields:
     *   - color:{r,g,b} or r,g,b
     *   - density (>0 enables, <=0 disables)
     *   - distance (>0 enables, <=0 disables)
     */
    @LuaExport
    void fogCfg(LuaValueRef cfg);

    /**
     * Post-processing configuration (optional).
     * Note: no exposure control here (engine currently doesn't support setExposure).
     * Suggested fields:
     *   - fxaa: boolean
     *   - bloom: boolean
     *   - bloomIntensity: number
     *   - bloomExposure: number
     *   - tonemap: boolean  (no exposure param)
     *   - ssao: boolean (optional, if filter exists in classpath)
     */
    @LuaExport
    void postCfg(LuaValueRef cfg);
}
