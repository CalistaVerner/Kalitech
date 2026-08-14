// FILE: ScriptComponent.java
package org.foxesworld.kalitech.engine.ecs.components;

import org.foxesworld.kalitech.engine.script.ScriptEntryPoint;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.script.resolve.PathNorm;

/**
 * Per-entity Lua component addressed by a canonical application module id.
 *
 * <p>Example: {@code "@app/game/entities/player.lua"}.</p>
 */
public final class ScriptComponent {

    public final String moduleId;
    public final transient long moduleHash;

    public transient LuaValueRef instance;
    public transient long moduleVersion;
    public transient Object stateCapsule;

    /** A failed instance stays disabled until its module version changes. */
    public transient boolean quarantined;
    public transient long quarantineVersion;
    public transient String quarantineReason;

    public ScriptComponent(String moduleId) {
        String normalized = PathNorm.normalizeId(moduleId);
        if (!normalized.startsWith(ScriptEntryPoint.APP_PREFIX) || !normalized.endsWith(".lua")) {
            throw new IllegalArgumentException(
                    "Entity script must be a canonical Lua module id: @app/<namespace>/.../*.lua"
            );
        }
        this.moduleId = normalized;
        this.moduleHash = hash64(normalized);
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
