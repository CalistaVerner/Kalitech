package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface RenderApi {
    @HostAccess.Export void ensureScene();

    @HostAccess.Export
    void terrainFromHeightmap(String heightmapAsset, double patchSize, double size, double heightScale, double xzScale);

    // old style
    @HostAccess.Export void ambient(double r, double g, double b, double intensity);
    @HostAccess.Export void sun(double dirX, double dirY, double dirZ, double r, double g, double b, double intensity);

    @HostAccess.Export void sunShadows(double mapSize, double splits, double lambda);

    /** Config style shadows. Example: { mapSize: 2048, splits: 3, lambda: 0.65 } */
    @HostAccess.Export void sunShadowsCfg(Value cfg);

    // config style
    @HostAccess.Export void ambientCfg(Value cfg);
    @HostAccess.Export void sunCfg(Value cfg);

    // scene visuals
    @HostAccess.Export void skyboxCube(String cubeMapAsset);
    @HostAccess.Export void fogCfg(Value cfg);

    // ✅ JS-first
    @HostAccess.Export void terrain(Value cfg);

    // (optional compat)
    @HostAccess.Export void terrainCfg(Value cfg);

    // terrain splat
    @HostAccess.Export
    void terrainSplat3(String alphaMapAsset, String tex1, double scale1, String tex2, double scale2, String tex3, double scale3);
}