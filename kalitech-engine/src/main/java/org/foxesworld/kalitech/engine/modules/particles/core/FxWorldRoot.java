// FILE: org/foxesworld/kalitech/engine/modules/particles/core/FxWorldRoot.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.core;

import com.jme3.scene.Node;

/**
 * Dedicated root node for world FX.
 * Allows future routing (world/ui/weapon/vehicle) without breaking API.
 */
public final class FxWorldRoot {

    private final Node root = new Node("fxWorldRoot");

    public Node node() {
        return root;
    }
}