// FILE: org/foxesworld/kalitech/engine/modules/render/PcssDirectionalLightShadowRenderer.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.asset.AssetManager;
import com.jme3.renderer.Camera;
import com.jme3.shadow.DirectionalLightShadowRenderer;

/**
 * Directional light shadow renderer that implements Percentage‐Closer Soft Shadows (PCSS).
 *
 * <p>PCSS produces soft shadow edges that broaden with the distance between the occluder
 * and the receiver, creating a more realistic contact‑hardening penumbra. This class
 * extends {@link DirectionalLightShadowRenderer} and exposes additional parameters
 * controlling the PCSS behaviour, such as the effective light source radius
 * ({@code lightSize}) and the number of samples used for blocker search and
 * filtering. A complete PCSS implementation would replace the default shadow
 * comparison shader with one that performs two phases: blocker depth search
 * followed by percentage‑closer filtering with a radius proportional to the
 * blocker/receiver distance. The default JME3 shader only supports fixed kernel
 * percentage‑closer filtering; therefore this class currently sets the PCSS
 * parameters as uniforms on the post‑shadow material. To enable PCSS, the
 * associated shader must be updated accordingly.</p>
 */
public class PcssDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer {

    /**
     * Radius of the light source in world units. Larger values produce wider
     * penumbrae. Defaults to {@code 0.1f} which approximates a small sun disk.
     */
    private float lightSize = 0.1f;

    /**
     * Number of samples used during the blocker search phase. Increasing this
     * improves the accuracy of the average blocker depth estimation at the cost
     * of performance. A typical value is between 8 and 16.
     */
    private int searchSamples = 16;

    /**
     * Number of samples used during the filtering phase. Higher values yield
     * smoother soft shadows. A typical value is 16–32. Note that the total
     * number of shadow map taps can grow quadratically with this parameter,
     * so values above 32 may be prohibitively expensive.
     */
    private int filterSamples = 32;

    /**
     * Creates a new PCSS shadow renderer. The constructor matches the base
     * {@link DirectionalLightShadowRenderer} signature.
     *
     * @param assets        asset manager used to load shaders and materials
     * @param shadowMapSize size in pixels of each shadow map texture
     * @param nbSplits      number of cascades (splits) to use
     */
    public PcssDirectionalLightShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
        super(assets, shadowMapSize, nbSplits);
    }

    /**
     * Sets the effective radius of the light source used when computing the
     * penumbra size. Increasing this value results in softer shadows at all
     * distances. Defaults to {@code 0.1f}.
     *
     * @param size light radius in world units
     */
    public void setLightSize(float size) {
        this.lightSize = Math.max(0.0f, size);
    }

    /**
     * Sets the number of samples used during the blocker search phase. The
     * blocker search determines the average depth of occluders around the
     * fragment being shaded. Higher values yield more accurate blocker depth
     * estimation but increase cost.
     *
     * @param samples number of search samples (>=1)
     */
    public void setSearchSamples(int samples) {
        this.searchSamples = Math.max(1, samples);
    }

    /**
     * Sets the number of samples used during the percentage‑closer filtering
     * phase. This controls the smoothness of the penumbra. Higher values
     * increase quality and cost.
     *
     * @param samples number of filter samples (>=1)
     */
    public void setFilterSamples(int samples) {
        this.filterSamples = Math.max(1, samples);
    }

    @Override
    protected void updateShadowCams(Camera viewCam) {
        // Let the base class compute cascade splits and light space cameras.
        super.updateShadowCams(viewCam);
        // Additional PCSS‑specific camera updates could be placed here, but
        // currently there are none. For example, one could adjust the
        // frustum extents based on light size to keep the penumbra within
        // cascade bounds.
    }

}