// FILE: org/foxesworld/kalitech/engine/api/interfaces/EntityApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

/**
 * Entity API (UUID-only).
 * Public scripting surface MUST NOT expose any int entityId.
 */
public interface EntityApi {

    // -------------------------
    // lifecycle (UUID-only)
    // -------------------------

    /**
     * Create entity and return its UUID string.
     */
    @LuaExport
    String create(String name);

    /** Destroy entity by UUID. Throws if UUID is unknown. */
    @LuaExport
    void destroy(String uuid);

    /** True if UUID resolves to a live entity. */
    @LuaExport
    boolean exists(String uuid);

    // -------------------------
    // components (UUID-only)
    // -------------------------

    @LuaExport
    void setComponent(String uuid, String type, Object value);

    @LuaExport
    Object getComponent(String uuid, String type);

    @LuaExport
    boolean hasComponent(String uuid, String type);

    @LuaExport
    void removeComponent(String uuid, String type);

    // -------------------------
    // editor helpers
    // -------------------------

    /**
     * Snapshot entity state for UI/editor tooling.
     */
    @LuaExport
    java.util.Map<String, Object> snapshot(String uuid);

    /**
     * List up to {@code limit} entity UUIDs.
     */
    @LuaExport
    String[] list(int limit);
}
