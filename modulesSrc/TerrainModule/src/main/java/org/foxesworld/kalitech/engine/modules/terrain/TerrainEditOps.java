/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector2f
 *  com.jme3.math.Vector3f
 *  com.jme3.terrain.geomipmap.TerrainQuad
 */
package org.foxesworld.kalitech.engine.modules.terrain;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.terrain.geomipmap.TerrainQuad;

public final class TerrainEditOps {
    static Vector2f worldXZToLocalXZ(TerrainQuad tq, float wx, float wz) {
        Vector3f local = tq.worldToLocal(new Vector3f(wx, 0.0f, wz), null);
        return new Vector2f(local.x, local.z);
    }

    public void setHeightmap(TerrainQuad tq, float[] heights, boolean rebuild) {
        if (heights == null) {
            throw new IllegalArgumentException("heights is null");
        }
        if (rebuild) {
            this.rebuild(tq);
        }
    }

    public float[] heightmapCopy(TerrainQuad tq) {
        float[] hm = tq.getHeightMap();
        if (hm == null || hm.length == 0) {
            return new float[0];
        }
        float[] out = new float[hm.length];
        System.arraycopy(hm, 0, out, 0, hm.length);
        return out;
    }

    public void setHeight(TerrainQuad tq, double x, double z, double height, boolean world) {
        Vector2f p = world ? new Vector2f((float)x, (float)z) : TerrainEditOps.worldXZToLocalXZ(tq, (float)x, (float)z);
        tq.setHeight(p, (float)height);
        tq.updateModelBound();
    }

    public void adjustHeight(TerrainQuad tq, double x, double z, double delta, boolean world) {
        Vector2f p = world ? new Vector2f((float)x, (float)z) : TerrainEditOps.worldXZToLocalXZ(tq, (float)x, (float)z);
        tq.adjustHeight(p, (float)delta);
        tq.updateModelBound();
    }

    public void rebuild(TerrainQuad tq) {
        tq.updateModelBound();
        tq.updateGeometricState();
    }
}

