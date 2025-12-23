package org.foxesworld.kalitech.engine.ecs.components;

public final class TransformComponent {
    public float x, y, z;
    public float rotY; // для примера

    public TransformComponent() {}

    public TransformComponent(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
    }
}