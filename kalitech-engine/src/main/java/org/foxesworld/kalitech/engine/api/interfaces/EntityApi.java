// FILE: org/foxesworld/kalitech/engine/api/interfaces/EntityApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

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
    @HostAccess.Export
    String create(String name);

    /** Destroy entity by UUID. Throws if UUID is unknown. */
    @HostAccess.Export
    void destroy(String uuid);

    /** True if UUID resolves to a live entity. */
    @HostAccess.Export
    boolean exists(String uuid);

    // -------------------------
    // components (UUID-only)
    // -------------------------

    @HostAccess.Export
    void setComponent(String uuid, String type, Object value);

    @HostAccess.Export
    Object getComponent(String uuid, String type);

    @HostAccess.Export
    boolean hasComponent(String uuid, String type);

    @HostAccess.Export
    void removeComponent(String uuid, String type);
}