// FILE: RenderApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface RenderApi {

    @HostAccess.Export void ensureScene();

    // old style
    @HostAccess.Export void ambient(double r, double g, double b, double intensity);
    @HostAccess.Export void sun(double dirX, double dirY, double dirZ, double r, double g, double b, double intensity);

    @HostAccess.Export void sunShadows(double mapSize, double splits, double lambda);
    @HostAccess.Export void sunShadowsCfg(Value cfg);

    // config style
    @HostAccess.Export void ambientCfg(Value cfg);
    @HostAccess.Export void sunCfg(Value cfg);

    // scene visuals
    @HostAccess.Export void skyboxCube(String cubeMapAsset);
    @HostAccess.Export void fogCfg(Value cfg);

    // ------------------------------------------------------------
    // Editor / Tools (debug visuals)
    // ------------------------------------------------------------

    /**
     * Create a visual grid plane for editor mode.
     * Returns a JS-friendly handle (usually a RenderApiImpl.SpatialHandle host object).
     *
     * cfg:
     *  - size (double): half-extent in world units (default 200)
     *  - step (double): cell size (default 1)
     *  - majorStep (double): reserved for future styling (default 10)
     *  - y (double): height offset to avoid z-fighting (default 0.01)
     *  - opacity (double): 0..1 (default 0.35)
     */
    @HostAccess.Export Object createGridPlane(Value cfg);

    /** Destroy grid plane (or any debug plane created by createGridPlane). */
    @HostAccess.Export void destroyGridPlane(Object handle);

    /**
     * Optional helper: toggle visibility of a spatial by handle.
     * (Nice for editor UI toggles; can be implemented later.)
     */
    @HostAccess.Export default void setVisible(Object handle, boolean visible) {
        // optional, implement in RenderApiImpl when needed
    }
}