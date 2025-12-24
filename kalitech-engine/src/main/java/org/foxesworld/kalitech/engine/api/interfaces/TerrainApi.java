package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface TerrainApi {

    @HostAccess.Export
    SurfaceApi.SurfaceHandle terrain(Value cfg);

    @HostAccess.Export
    SurfaceApi.SurfaceHandle quad(Value cfg);

    @HostAccess.Export
    SurfaceApi.SurfaceHandle plane(Value cfg);

    // ✅ ECS attach/detach (forward to SurfaceApi)
    @HostAccess.Export
    void attach(SurfaceApi.SurfaceHandle handle, int entityId);

    @HostAccess.Export
    void detach(SurfaceApi.SurfaceHandle handle);
}