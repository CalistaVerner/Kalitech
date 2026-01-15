// FILE: org/foxesworld/kalitech/engine/modules/render/sky/SkyMaterialUtil.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.material.Material;

/**
 * Material safety helpers.
 */
public final class SkyMaterialUtil {

    private SkyMaterialUtil() {
    }

    /**
     * Clears a material parameter only if it exists and is declared in the material definition.
     *
     * @param m    material
     * @param name param name
     */
    public static void safeClearParam(Material m, String name) {
        if (m == null || name == null) return;
        if (m.getMaterialDef() == null) return;
        if (m.getMaterialDef().getMaterialParam(name) == null) return;
        if (m.getParam(name) == null) return;
        m.clearParam(name);
    }
}