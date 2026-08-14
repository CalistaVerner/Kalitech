/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.material.Material
 */
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.material.Material;

public final class SkyMaterialUtil {
    private SkyMaterialUtil() {
    }

    public static void safeClearParam(Material m, String name) {
        if (m == null || name == null) {
            return;
        }
        if (m.getMaterialDef() == null) {
            return;
        }
        if (m.getMaterialDef().getMaterialParam(name) == null) {
            return;
        }
        if (m.getParam(name) == null) {
            return;
        }
        m.clearParam(name);
    }
}

