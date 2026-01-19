package org.foxesworld.kalitech.engine.world.systems.registry;

/**
 * High-level system category used for diagnostics, reporting, and safe defaults.
 * <p>
 * This is intentionally coarse-grained; it helps the engine explain what kind of
 * system is being registered without encoding runtime thread details.
 */
public enum SystemType {
    /** Core engine systems (ECS, transforms, internal scheduling). */
    CORE,
    /** Script-driven or script-authored systems. */
    SCRIPTED,
    /** Engine extensions (feature modules such as physics/render). */
    EXTENSION,
    /** Project-specific systems not owned by the engine. */
    CUSTOM
}
