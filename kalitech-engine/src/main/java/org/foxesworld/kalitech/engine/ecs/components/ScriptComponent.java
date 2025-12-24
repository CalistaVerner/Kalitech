// FILE: ScriptComponent.java
package org.foxesworld.kalitech.engine.ecs.components;

import org.graalvm.polyglot.Value;

/**
 * Script component per-entity.
 *
 * <p>assetPath example: {@code "Scripts/entities/player.js"}.
 * This component is intentionally small: it stores only per-entity script binding state.
 */
public final class ScriptComponent {

    /** Original asset path as provided by content/tools. */
    public final String assetPath;

    /** Normalized module id (cached to avoid per-frame string work). */
    public final transient String moduleId;

    /**
     * Stable hash for quick indexing/logging/debug (no per-frame string hashing).
     * NOTE: Not a security hash; just a fast stable 64-bit hash.
     */
    public final transient long moduleHash;

    // runtime state (not serialized)
    public transient Value instance;      // created instance: {init,update,destroy}
    public transient long moduleVersion;  // last runtime module version applied to this entity

    public ScriptComponent(String assetPath) {
        this.assetPath = assetPath;
        this.moduleId = normalize(assetPath);
        this.moduleHash = hash64(this.moduleId);
    }

    private static String normalize(String id) {
        if (id == null) return "";
        String s = id.trim().replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);
        return s;
    }

    /**
     * FNV-1a 64-bit hash (fast + stable across runs/JVMs).
     * Good for ids/keys, not for cryptography.
     */
    private static long hash64(String s) {
        if (s == null || s.isEmpty()) return 0L;
        long h = 0xcbf29ce484222325L; // offset basis
        for (int i = 0, n = s.length(); i < n; i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L; // prime
        }
        return h;
    }
}