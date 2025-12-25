package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface MeshApi {
    @HostAccess.Export SurfaceApi.SurfaceHandle box(Value cfg);
    @HostAccess.Export SurfaceApi.SurfaceHandle sphere(Value cfg);
    @HostAccess.Export SurfaceApi.SurfaceHandle cylinder(Value cfg);

    // Визуальная капсула: Cylinder + 2 spheres (нет Capsule в shape)
    @HostAccess.Export SurfaceApi.SurfaceHandle capsule(Value cfg);

    // бонус: quad/grid можно позже
}