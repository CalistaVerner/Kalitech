// FILE: org/foxesworld/kalitech/engine/modules/physics/BodyState.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

public final class BodyState {
    public final Vector3f pos = new Vector3f();
    public final Quaternion rot = new Quaternion();
    public final Vector3f linVel = new Vector3f();
    public final Vector3f angVel = new Vector3f();
    public boolean active;
    public boolean init;
}
