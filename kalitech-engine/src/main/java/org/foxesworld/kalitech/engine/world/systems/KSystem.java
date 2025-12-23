package org.foxesworld.kalitech.engine.world.systems;

public interface KSystem {
    default void onStart(SystemContext ctx) {}
    default void onUpdate(SystemContext ctx, float tpf) {}
    default void onStop(SystemContext ctx) {}
}