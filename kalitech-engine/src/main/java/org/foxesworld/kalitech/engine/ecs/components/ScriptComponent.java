// FILE: ScriptComponent.java
package org.foxesworld.kalitech.engine.ecs.components;

import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

/**
 * Script component per entity.
 *
 * <p>assetPath example: {@code "Scripts/entities/player.lua"}.</p>
 */
public final class ScriptComponent {

    public final String assetPath;
    public final transient String moduleId;
    public final transient long moduleHash;

    public transient LuaValueRef instance;
    public transient long moduleVersion;
    public transient Object stateCapsule;

    /** A failed instance stays disabled until its module version changes. */
    public transient boolean quarantined;
    public transient long quarantineVersion;
    public transient String quarantineReason;

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

    private static long hash64(String s) {
        if (s == null || s.isEmpty()) return 0L;
        long h = 0xcbf29ce484222325L;
        for (int i = 0, n = s.length(); i < n; i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }
}
