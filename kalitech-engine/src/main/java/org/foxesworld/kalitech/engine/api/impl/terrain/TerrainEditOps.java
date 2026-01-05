package org.foxesworld.kalitech.engine.api.impl.terrain;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.terrain.geomipmap.TerrainQuad;

/**
 * Terrain editing ops (tooling/runtime sculpt).
 *
 * Separated from {@link TerrainOps} so query/LOD stays lean.
 */
public final class TerrainEditOps {

    public TerrainEditOps() {}

    /**
     * Replace terrain heightmap.
     * Note: TerrainQuad#setHeightmap expects size*size.
     */
    public void setHeightmap(TerrainQuad tq, float[] heights, boolean rebuild) {
        if (heights == null) throw new IllegalArgumentException("heights is null");
        //tq.setHeightmap(heights);
        if (rebuild) rebuild(tq);
    }

    /** Copy current heightmap (defensive). */
    public float[] heightmapCopy(TerrainQuad tq) {
        float[] hm = tq.getHeightMap();
        if (hm == null || hm.length == 0) return new float[0];
        float[] out = new float[hm.length];
        System.arraycopy(hm, 0, out, 0, hm.length);
        return out;
    }

    /** Set height at (x,z). world=true uses world x/z, otherwise local. */
    public void setHeight(TerrainQuad tq, double x, double z, double height, boolean world) {
        Vector2f p = world ? new Vector2f((float) x, (float) z) : worldXZToLocalXZ(tq, (float) x, (float) z);
        tq.setHeight(p, (float) height);
        tq.updateModelBound();
    }

    public void adjustHeight(TerrainQuad tq, double x, double z, double delta, boolean world) {
        Vector2f p = world ? new Vector2f((float) x, (float) z) : worldXZToLocalXZ(tq, (float) x, (float) z);
        tq.adjustHeight(p, (float) delta);
        tq.updateModelBound();
    }

    public void rebuild(TerrainQuad tq) {
        tq.updateModelBound();
        tq.updateGeometricState();
    }

    static Vector2f worldXZToLocalXZ(TerrainQuad tq, float wx, float wz) {
        Vector3f local = tq.worldToLocal(new Vector3f(wx, 0f, wz), null);
        return new Vector2f(local.x, local.z);
    }
}