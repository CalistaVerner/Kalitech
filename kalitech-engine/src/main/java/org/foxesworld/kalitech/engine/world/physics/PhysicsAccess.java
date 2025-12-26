package org.foxesworld.kalitech.engine.world.physics;

// Author: Calista Verner

import com.jme3.math.Vector3f;

@Deprecated
public interface PhysicsAccess {
    boolean getBodyPosition(int bodyId, Vector3f out);
    float rayFraction(Vector3f from, Vector3f to);
}