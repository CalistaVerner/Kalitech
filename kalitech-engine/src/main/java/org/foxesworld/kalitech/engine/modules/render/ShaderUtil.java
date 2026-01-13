// FILE: org/foxesworld/kalitech/engine/modules/render/ShaderUtil.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

/**
 * Utility to apply KaliLighting material (resources/Shaders/material/KaliLighting.j3md)
 * to a whole scene subtree.
 * <p>
 * Important: clones material per-geometry to allow JME to set per-material shadow defines/params safely.
 */
public final class ShaderUtil {

    private ShaderUtil() {
    }

    public static void applyKaliLightingRecursive(AssetManager assets, Spatial root) {
        if (assets == null || root == null) return;

        Material base = new Material(assets, "Shaders/material/KaliLighting.j3md");
        base.setColor("DiffuseColor", ColorRGBA.White);
        base.setFloat("AlphaDiscardThreshold", 0f);

        applyRecursive(root, base);
    }

    private static void applyRecursive(Spatial s, Material base) {
        if (s instanceof Geometry g) {
            g.setMaterial(base.clone());
            return;
        }
        if (s instanceof Node n) {
            for (Spatial c : n.getChildren()) applyRecursive(c, base);
        }
    }
}