// FILE: engine/api/interfaces/EditorLinesApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

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
    @HostAccess.Export
    SurfaceApi.SurfaceHandle createGridPlane(Value cfg);

    /**
     * Destroy a previously created editor surface handle.
     *
     * @param handle surface handle or host reference
     */
    @HostAccess.Export
    void destroy(Object handle);
}
