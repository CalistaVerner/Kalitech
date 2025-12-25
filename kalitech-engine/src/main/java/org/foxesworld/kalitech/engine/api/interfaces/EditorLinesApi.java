// FILE: engine/api/interfaces/EditorLinesApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface EditorLinesApi {

    @HostAccess.Export
    SurfaceApi.SurfaceHandle createGridPlane(Value cfg);

    @HostAccess.Export
    void destroy(Object handle);
}