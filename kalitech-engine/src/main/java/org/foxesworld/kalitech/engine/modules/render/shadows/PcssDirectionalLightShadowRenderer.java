// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/PcssDirectionalLightShadowRenderer.java
package org.foxesworld.kalitech.engine.modules.render.shadows;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import org.apache.logging.log4j.Logger;

/**
 * PCSS layer on top of StableDirectionalLightShadowRenderer.
 * Only sets uniforms if material supports them.
 */
public class PcssDirectionalLightShadowRenderer extends StableDirectionalLightShadowRenderer implements ShadowRenderer {

    private final PcssSettings pcss = new PcssSettings();

    public PcssDirectionalLightShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
        super(assets, shadowMapSize, nbSplits);
    }

    public PcssSettings pcssSettings() {
        return pcss;
    }

    @Override
    protected void setMaterialParameters(Material material) {
        super.setMaterialParameters(material);
        if (material == null || material.getMaterialDef() == null) return;

        boolean any = false;

        if (material.getParam("LightSize") != null) {
            material.setFloat("LightSize", Math.max(0f, pcss.lightSize));
            any = true;
        }
        if (material.getParam("SearchSamples") != null) {
            material.setInt("SearchSamples", Math.max(4, pcss.searchSamples));
            any = true;
        }
        if (material.getParam("FilterSamples") != null) {
            material.setInt("FilterSamples", Math.max(4, pcss.filterSamples));
            any = true;
        }

        if (pcss.debug && pcss.debugLog != null && pcss.debugLog.isDebugEnabled()) {
            pcss.debugLog.debug("[shadow][pcss] uniformsApplied=" + any +
                    " lightSize=" + pcss.lightSize +
                    " searchSamples=" + pcss.searchSamples +
                    " filterSamples=" + pcss.filterSamples);
        }
    }

    /**
     * PCSS clear = Stable clear (GPU FBO clear).
     * Keep override for future PCSS-specific caches.
     */
    @Override
    public void clearShadows(RenderManager rm, ViewPort vp) {
        super.clearShadows(rm, vp);
    }

    public static final class PcssSettings {
        public float lightSize = 0.004f; // было 0.02f -> мыло. 0.003..0.006 ок
        public int searchSamples = 12;   // 8..16
        public int filterSamples = 8;    // 6..10 (16 = сильно мягко)
        public Logger debugLog = null;
        public boolean debug = false;
    }

}
