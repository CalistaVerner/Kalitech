package org.foxesworld.kalitech.engine.ecs.components;

import com.jme3.scene.Spatial;

public final class RenderableComponent {
    public Spatial spatial;

    public RenderableComponent(Spatial spatial) {
        this.spatial = spatial;
    }
}