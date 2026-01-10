// FILE: org/foxesworld/kalitech/engine/api/interfaces/EntityApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

public interface EntityApi {

    // -------------------------
    // Entity lifecycle (UUID-only)
    // -------------------------

    /**
     * UUID-only: must return UUID string
     */
    @HostAccess.Export
    String create(String name);

    @HostAccess.Export
    void destroy(String uuid);

    @HostAccess.Export
    boolean exists(String uuid);

    // -------------------------
    // UUID <-> internal id bridge
    // -------------------------

    /**
     * UUID -> internal entityId (for engine internals only)
     */
    @HostAccess.Export
    int entityIdOf(String uuid);

    /**
     * internal entityId -> UUID
     */
    @HostAccess.Export
    String uuidOf(int entityId);

    // -------------------------
    // Components (UUID-only)
    // -------------------------

    /**
     * Set/replace a named component for entity UUID.
     */
    @HostAccess.Export
    void setComponent(String uuid, String type, Object value);

    /**
     * Read a named component; returns null if absent.
     */
    @HostAccess.Export
    Object getComponent(String uuid, String type);

    /**
     * True if entity has named component.
     */
    @HostAccess.Export
    boolean hasComponent(String uuid, String type);

    /**
     * Remove named component (no-op if absent).
     */
    @HostAccess.Export
    void removeComponent(String uuid, String type);
}