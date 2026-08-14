// FILE: engine/api/interfaces/EditorLinesApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

/**
 * Editor line helpers (grid plane and related debug primitives).
 */
public interface EditorLinesApi {

    /**
     * Create a grid plane surface for editor visualization.
     *
     * @param cfg configuration object (grid size, step, colors, etc.)
     * @return surface handle for the created grid
     */
    @LuaExport
    SurfaceApi.SurfaceHandle createGridPlane(LuaValueRef cfg);

    /**
     * Destroy a previously created editor surface handle.
     *
     * @param handle surface handle or host reference
     */
    @LuaExport
    void destroy(Object handle);
}
