// FILE: org/foxesworld/kalitech/engine/modules/render/SnappingDirectionalLightShadowRenderer.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.asset.AssetManager;
import com.jme3.renderer.Camera;
import com.jme3.shadow.DirectionalLightShadowRenderer;

/**
 * DirectionalLightShadowRenderer + always-on texel snapping.
 * <p>
 * No thresholds, no smoothing, no time gating.
 * Snapping runs every frame for each split camera -> smooth updates without shimmer.
 */
public final class SnappingDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer {

    private ShadowSnapper snapper;
    private boolean snapEnabled = true;

    public SnappingDirectionalLightShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
        super(assets, shadowMapSize, nbSplits);
        this.snapper = new ShadowSnapper(shadowMapSize);
    }

    public boolean isSnapEnabled() {
        return snapEnabled;
    }

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
    }

    @Override
    protected void updateShadowCams(Camera viewCam) {
        super.updateShadowCams(viewCam);

        if (!snapEnabled) return;

        final int n = getNumShadowMaps();
        for (int i = 0; i < n; i++) {
            Camera c = getShadowCam(i);
            if (c != null) {
                snapper.snap(c);
            }
        }
    }
}