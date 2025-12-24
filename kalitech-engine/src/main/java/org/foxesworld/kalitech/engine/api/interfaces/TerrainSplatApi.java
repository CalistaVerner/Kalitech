package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

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
    @HostAccess.Export
    void apply(SurfaceApi.SurfaceHandle terrainHandle, Value cfg);

    /**
     * Convenience: create TerrainLighting.j3md configured for splat.
     */
    @HostAccess.Export
    Object createMaterial(Value cfg);
}