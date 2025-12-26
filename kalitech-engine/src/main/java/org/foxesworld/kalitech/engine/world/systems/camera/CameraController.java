package org.foxesworld.kalitech.engine.world.systems.camera;

// Author: Calista Verner

import org.foxesworld.kalitech.engine.api.impl.CameraState;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

public interface CameraController {
    default void onEnter(SystemContext ctx, CameraState s) {}
    default void onExit(SystemContext ctx, CameraState s) {}
    void update(SystemContext ctx, CameraState s, float tpf);
}