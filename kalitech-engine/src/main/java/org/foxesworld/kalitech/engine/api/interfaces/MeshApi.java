// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

/**
 * Lua-first mesh constructor.
 *
 * <pre>
 * engine.mesh().create({
 *   type: "box"|"sphere"|"cylinder"|"capsule",
 *   size|radius|height|hx|hy|hz,
 *   name,
 *   pos|rot|scale,
 *   material: {def, params} | MaterialHandle,
 *   physics: {enabled?, mass, lockRotation?, kinematic?, friction?, restitution?, damping?, collider?},
 *   attach: true|false
 * })
 * </pre>
 */
public interface MeshApi {
    @LuaExport
    SurfaceApi.SurfaceHandle create(LuaValueRef cfg);
}
