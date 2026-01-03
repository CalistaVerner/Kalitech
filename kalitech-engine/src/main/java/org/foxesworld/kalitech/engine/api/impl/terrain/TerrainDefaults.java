package org.foxesworld.kalitech.engine.api.impl.terrain;

import com.jme3.math.ColorRGBA;

public final class TerrainDefaults {
    private TerrainDefaults() {}

    public static final String NAME_TERRAIN = "terrain";
    public static final String NAME_PLANE = "plane";
    public static final String NAME_QUAD  = "quad";

    public static final int PATCH_SIZE = 65;     // clamp 17..257
    public static final int SIZE       = 513;    // clamp 33..8193

    public static final double HEIGHT_SCALE = 2.0;
    public static final double XZ_SCALE     = 2.0;
    public static final double Y_SCALE      = 1.0;

    public static final double PLANE_W = 1.0;
    public static final double PLANE_H = 1.0;

    public static final boolean SHADOWS_BOOL_DEFAULT = true;

    public static final boolean ATTACH_DEFAULT = true;

    public static final ColorRGBA TERRAIN_COLOR = new ColorRGBA(0.25f, 0.7f, 0.3f, 1f);
    public static final ColorRGBA GEOM_COLOR    = ColorRGBA.White;
}