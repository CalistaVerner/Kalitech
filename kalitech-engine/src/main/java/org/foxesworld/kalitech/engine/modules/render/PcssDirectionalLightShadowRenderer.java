// FILE: org/foxesworld/kalitech/engine/modules/render/PcssDirectionalLightShadowRenderer.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import org.apache.logging.log4j.Logger;

/**
 * PCSS variant on top of StableDirectionalLightShadowRenderer.
 *
 * NOTE: PCSS requires shader support. This renderer only sets uniforms
 * if the material definition provides them.
 */
public final class PcssDirectionalLightShadowRenderer extends StableDirectionalLightShadowRenderer {

    // PCSS knobs (contact hardening)
    private float lightSize = 0.02f;   // bigger => softer
    private int searchSamples = 16;    // blocker search
    private int filterSamples = 16;    // PCF filter

    // logging
    private Logger log;
    private boolean dbg = false;

    public PcssDirectionalLightShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
        super(assets, shadowMapSize, nbSplits);
    }

    public void setPcssDebug(Logger log, boolean enabled) {
        this.log = log;
        this.dbg = enabled;
    }

    public void setLightSize(float lightSize) {
        this.lightSize = Math.max(0.0f, lightSize);
    }

    public void setSearchSamples(int searchSamples) {
        this.searchSamples = Math.max(4, searchSamples);
    }

    public void setFilterSamples(int filterSamples) {
        this.filterSamples = Math.max(4, filterSamples);
    }

    @Override
    protected void setMaterialParameters(Material material) {
        super.setMaterialParameters(material);
        if (material == null || material.getMaterialDef() == null) return;

        boolean any = false;

        if (material.getParam("LightSize") != null) {
            material.setFloat("LightSize", lightSize);
            any = true;
        }
        if (material.getParam("SearchSamples") != null) {
            material.setInt("SearchSamples", searchSamples);
            any = true;
        }
        if (material.getParam("FilterSamples") != null) {
            material.setInt("FilterSamples", filterSamples);
            any = true;
        }

        if (dbg && log != null && log.isDebugEnabled()) {
            log.debug("[shadow][pcss] uniformsApplied=" + any +
                    " lightSize=" + lightSize +
                    " searchSamples=" + searchSamples +
                    " filterSamples=" + filterSamples);
        }
    }
}