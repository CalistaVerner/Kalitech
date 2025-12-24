package org.foxesworld.kalitech.engine.ecs.components;

import org.graalvm.polyglot.Value;

/**
 * Script component per-entity.
 * assetPath: "Scripts/entities/player.js"
 */
public final class ScriptComponent {
    public final String assetPath;

    // runtime state (not serialized)
    public transient Value instance;     // created instance: {init,update,destroy}
    public transient String moduleHash;  // last module hash applied to this entity

    public ScriptComponent(String assetPath) {
        this.assetPath = assetPath;
    }
}