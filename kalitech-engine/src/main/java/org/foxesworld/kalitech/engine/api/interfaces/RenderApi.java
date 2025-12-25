// FILE: RenderApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface RenderApi {

    @HostAccess.Export void ensureScene();

    // old style
    @HostAccess.Export void ambient(double r, double g, double b, double intensity);
    @HostAccess.Export void sun(double dirX, double dirY, double dirZ, double r, double g, double b, double intensity);

    @HostAccess.Export void sunShadows(double mapSize, double splits, double lambda);
    @HostAccess.Export void sunShadowsCfg(Value cfg);

    // config style
    @HostAccess.Export void ambientCfg(Value cfg);
    @HostAccess.Export void sunCfg(Value cfg);

    // scene visuals
    @HostAccess.Export void skyboxCube(String cubeMapAsset);
    @HostAccess.Export void fogCfg(Value cfg);
}