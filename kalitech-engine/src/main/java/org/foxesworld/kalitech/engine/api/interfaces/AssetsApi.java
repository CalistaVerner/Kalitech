package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface AssetsApi {

    @HostAccess.Export
    String readText(String assetPath);

    @HostAccess.Export
    SurfaceApi.SurfaceHandle loadModel(String assetPath, Value cfg);
}
