// FILE: org/foxesworld/kalitech/engine/modules/render/sky/SkySceneUtil.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.material.Material;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

import java.util.ArrayList;

/**
 * Scene helpers for sky rendering.
 */
public final class SkySceneUtil {

    private SkySceneUtil() {
    }

    /**
     * Collects all geometries in a spatial subtree, binds the material, and enforces sky render states.
     *
     * @param root spatial root
     * @param mat  shared sky material
     * @return array of geometries or null if none found
     */
    public static Geometry[] collectGeometriesAndBind(Spatial root, Material mat) {
        ArrayList<Geometry> list = new ArrayList<>(8);

        root.depthFirstTraversal(sp -> {
            if (!(sp instanceof Geometry g)) return;

            g.setMaterial(mat);
            g.setQueueBucket(RenderQueue.Bucket.Sky);
            g.setCullHint(Spatial.CullHint.Never);
            g.setShadowMode(RenderQueue.ShadowMode.Off);

            list.add(g);
        });

        return list.isEmpty() ? null : list.toArray(new Geometry[0]);
    }
}