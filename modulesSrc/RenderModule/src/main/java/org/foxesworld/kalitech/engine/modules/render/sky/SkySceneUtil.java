/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.material.Material
 *  com.jme3.renderer.queue.RenderQueue$Bucket
 *  com.jme3.renderer.queue.RenderQueue$ShadowMode
 *  com.jme3.scene.Geometry
 *  com.jme3.scene.Spatial
 *  com.jme3.scene.Spatial$CullHint
 */
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.material.Material;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import java.util.ArrayList;

public final class SkySceneUtil {
    private SkySceneUtil() {
    }

    public static Geometry[] collectGeometriesAndBind(Spatial root, Material mat) {
        ArrayList<Geometry> list = new ArrayList<>(8);
        root.depthFirstTraversal(sp -> {
            if (!(sp instanceof Geometry)) {
                return;
            }
            Geometry g = (Geometry)sp;
            g.setMaterial(mat);
            g.setQueueBucket(RenderQueue.Bucket.Sky);
            g.setCullHint(Spatial.CullHint.Never);
            g.setShadowMode(RenderQueue.ShadowMode.Off);
            list.add(g);
        });
        return list.isEmpty() ? null : list.toArray(new Geometry[0]);
    }
}

