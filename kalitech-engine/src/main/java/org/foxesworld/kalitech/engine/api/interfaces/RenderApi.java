// FILE: RenderApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * Render API (no legacy).
 * JS-first: all high-level behavior lives in scripts; Java exposes stable primitives.
 */
public interface RenderApi {

    /**
     * Ensures base render scene contract is created (lights, post pipeline root, etc.).
     * Idempotent.
     */
    @HostAccess.Export
    void ensureScene();

    // ---------------------------
    // Lighting (config style only)
    // ---------------------------

    /**
     * Ambient light configuration.
     * Expected fields:
     *   - r,g,b (or color:{r,g,b}) in 0..1
     *   - intensity (>=0)
     */
    @HostAccess.Export
    void ambientCfg(Value cfg);

    /**
     * Sun (directional) light configuration.
     * Expected fields:
     *   - dir: [x,y,z] or {x,y,z}
     *   - color: [r,g,b] or {r,g,b}
     *   - intensity (>=0)
     */
    @HostAccess.Export
    void sunCfg(Value cfg);

    // ---------------------------
    // Shadows (minimal)
    // ---------------------------

    /**
     * Sets directional sun shadow map size (e.g. 1024, 2048, 4096).
     * Pass <=0 to disable shadows.
     */
    @HostAccess.Export
    void sunShadows(int mapSize);

    /**
     * Same as sunShadows(mapSize), but accepts cfg:
     *   - mapSize
     */
    @HostAccess.Export
    void sunShadowsCfg(Value cfg);

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
    @HostAccess.Export
    void fogCfg(Value cfg);

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
    @HostAccess.Export
    void postCfg(Value cfg);
}
