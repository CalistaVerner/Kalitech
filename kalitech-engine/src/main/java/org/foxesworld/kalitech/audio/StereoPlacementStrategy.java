package org.foxesworld.kalitech.audio;

import com.jme3.math.Vector3f;

public interface StereoPlacementStrategy {

    record Result(Vector3f axisWorld) {}

    /**
     * @param listener snapshot of listener pose (may be fallback/default)
     * @param sourceWorldPos current source world position
     * @param cfg stereo config
     */
    Result compute(ListenerSnapshot listener, Vector3f sourceWorldPos, StereoConfig cfg);
}
