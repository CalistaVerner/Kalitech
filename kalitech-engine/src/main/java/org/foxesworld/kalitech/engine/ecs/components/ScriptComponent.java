package org.foxesworld.kalitech.engine.ecs.components;

import org.graalvm.polyglot.Value;

/**
 * Скрипт на сущность.
 * assetPath: "Scripts/player.js"
 */
public final class ScriptComponent {
    public final String assetPath;

    // runtime state (не сериализуем)
    public transient Value moduleObject; // returned object from JS
    public transient String lastLoadedCodeHash; // для cheap reload detection

    public ScriptComponent(String assetPath) {
        this.assetPath = assetPath;
    }
}