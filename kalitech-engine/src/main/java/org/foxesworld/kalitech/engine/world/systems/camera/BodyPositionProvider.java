package org.foxesworld.kalitech.engine.world.systems.camera;

import com.jme3.math.Vector3f;

public interface BodyPositionProvider {
    boolean getBodyPosition(int bodyId, Vector3f out);
}
