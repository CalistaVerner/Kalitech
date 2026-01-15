// FILE: org/foxesworld/kalitech/engine/modules/render/sky/SkyModule.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

/**
 * Public facade for the sky rendering subsystem.
 * <p>
 * This module is designed to be scripting-friendly while keeping internal rendering state isolated
 * and deterministic. No per-frame allocations are performed during configuration updates.
 */
public final class SkyModule {

    private final SkyDomeRenderer dome;

    public SkyModule(SimpleApplication app, AssetManager assets, Logger log) {
        this.dome = new SkyDomeRenderer(app, assets, log);
    }

    /**
     * Enables or disables sky rendering without destroying loaded assets.
     *
     * @param enabled true to attach the dome to the scene, false to detach it
     */
    public void setEnabled(boolean enabled) {
        dome.setEnabled(enabled);
    }

    /**
     * Removes the sky dome from the scene and resets all internal cached state.
     * Subsequent calls will lazily recreate the dome.
     */
    public void skyDomeClear() {
        dome.clearAll();
    }

    /**
     * Clears all sky textures (A/B) and resets texture type mode (2D vs cubemap).
     * The dome stays alive.
     */
    public void skyDomeTexClear() {
        dome.clearTextures();
    }

    /**
     * Assigns texture A (2D panorama or cubemap).
     *
     * @param asset asset path
     */
    public void skyDomeTexA(String asset) {
        dome.setTextureA(asset);
    }

    /**
     * Assigns texture B (2D panorama or cubemap).
     *
     * @param asset asset path
     */
    public void skyDomeTexB(String asset) {
        dome.setTextureB(asset);
    }

    /**
     * Applies sky shader parameters. Only changed values are pushed to the GPU.
     *
     * @param cfg a config object from scripts
     */
    public void skyDomeCfg(Value cfg) {
        dome.applyConfig(SkyDomeConfig.from(cfg));
    }
}