package org.foxesworld.kalitech.engine.world.systems.registry;

/**
 * Module namespace grouping used to describe where a system logically belongs.
 * This is not the runtime thread model; it is a semantic grouping for logging
 * and diagnostics.
 */
public enum SystemModuleType {
    ENGINE,
    SCRIPTING,
    ECS,
    RENDER,
    PHYSICS,
    GAMEPLAY,
    CUSTOM
}
