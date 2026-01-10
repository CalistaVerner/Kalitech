// FILE: org/foxesworld/kalitech/engine/api/interfaces/SurfaceApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * Surface API
 * <p>
 * UUID-only entity binding:
 * - You can still address surfaces by numeric surfaceId (runtime registry).
 * - Attaching surfaces to entities is UUID-only (no entityId in public API).
 * <p>
 * Notes:
 * - "LEGACY" block remains for compatibility with older scripts, but legacy attach/attachedEntity now throws.
 * - Prefer fluent methods on SurfaceHandle.
 */
public interface SurfaceApi {

    @HostAccess.Export
    SurfaceHandle handle(int id);

    // --------------------------------------------------------------------
    // LEGACY (engine.surface().xxx(handle,...)) — keep for compatibility,
    // but prefer methods on SurfaceHandle: handle.xxx(...)
    // --------------------------------------------------------------------

    /** @deprecated use {@code target.setMaterial(materialHandle)} */
    @Deprecated
    @HostAccess.Export
    void setMaterial(SurfaceHandle target, Object materialHandle);

    /** Apply material to all child geometries (Node/model hierarchies).
     *  @deprecated use {@code target.applyMaterialToChildren(materialHandle)} */
    @Deprecated
    @HostAccess.Export
    void applyMaterialToChildren(SurfaceHandle target, Object materialHandle);

    /** @deprecated use {@code target.setTransform(cfg)} */
    @Deprecated
    @HostAccess.Export
    void setTransform(SurfaceHandle target, Value cfg);

    /** @deprecated use {@code target.setShadowMode(mode)} */
    @Deprecated
    @HostAccess.Export
    void setShadowMode(SurfaceHandle target, String mode); // Off|Receive|Cast|CastAndReceive

    /** @deprecated use {@code target.attachToRoot()} */
    @Deprecated
    @HostAccess.Export
    void attachToRoot(SurfaceHandle target);

    /** @deprecated use {@code target.detach()} */
    @Deprecated
    @HostAccess.Export
    void detach(SurfaceHandle target);

    /** @deprecated use {@code target.destroy()} */
    @Deprecated
    @HostAccess.Export
    void destroy(SurfaceHandle target);

    /** @deprecated use {@code target.exists()} */
    @Deprecated
    @HostAccess.Export
    boolean exists(SurfaceHandle target);

    /**
     * LEGACY entityId access is removed in UUID-only mode.
     * @deprecated use {@code target.attachedEntityUuid()}
     */
    @Deprecated
    @HostAccess.Export
    default int attachedEntity(SurfaceHandle target) {
        throw new IllegalStateException("SurfaceApi.attachedEntity(entityId) removed (UUID-only). Use attachedEntityUuid(target).");
    }

    /**
     * LEGACY entityId attach is removed in UUID-only mode.
     * @deprecated use {@code target.attachEntity(uuid)} / {@code attachEntity(target, uuid)}
     */
    @Deprecated
    @HostAccess.Export
    default void attach(SurfaceHandle target, int entityId) {
        throw new IllegalStateException("SurfaceApi.attach(handle, entityId) removed (UUID-only). Use attachEntity(handle, uuid).");
    }

    /** @deprecated use {@code target.detachFromEntity()} */
    @Deprecated
    @HostAccess.Export
    void detachFromEntity(SurfaceHandle target);

    /** @deprecated use {@code target.getWorldBounds()} */
    @Deprecated
    @HostAccess.Export
    WorldBounds getWorldBounds(SurfaceHandle target);

    /** @deprecated use {@code target.raycast(cfg)} */
    @Deprecated
    @HostAccess.Export
    Hit[] raycast(SurfaceHandle target, Value cfg);

    /** @deprecated use {@code target.pickUnderCursor()} */
    @Deprecated
    @HostAccess.Export
    Hit[] pickUnderCursor(SurfaceHandle target);

    /** @deprecated use {@code target.pickUnderCursorCfg(cfg)} */
    @Deprecated
    @HostAccess.Export
    Hit[] pickUnderCursorCfg(SurfaceHandle target, Value cfg);

    // --------------------------------------------------------------------
    // Modern exports
    // --------------------------------------------------------------------

    // These 2 — stay on API because they are “world pick”, not “handle pick”.
    @HostAccess.Export
    Hit[] pickUnderCursor();

    @HostAccess.Export
    Hit[] pickUnderCursorCfg(Value cfg);

    @HostAccess.Export
    void setCull(SurfaceHandle target, String hint);

    @HostAccess.Export
    void setVisible(SurfaceHandle target, boolean visible);

    // -------------------------
    // UUID-only entity binding
    // -------------------------

    /**
     * Attach a surface to entity UUID.
     * Public UUID-only contract. (No entityId accepted.)
     */
    @HostAccess.Export
    void attachEntity(SurfaceHandle target, Object entityUuid);

    /**
     * Detach mapping by surface handle (if any).
     */
    @HostAccess.Export
    void detachFromEntityUuid(SurfaceHandle target);

    /**
     * Get attached entity UUID, or empty string if none.
     */
    @HostAccess.Export
    String attachedEntityUuid(SurfaceHandle target);

    // -------------------------
    // Host-safe DTOs for JS
    // -------------------------

    final class SurfaceHandle {
        public final int id;
        private final String kind;

        // not exported, but kept inside host object
        final SurfaceApi api;

        public SurfaceHandle(int id, String kind, SurfaceApi api) {
            this.id = id;
            this.kind = kind;
            this.api = api;
        }

        /**
         * Legacy ctor kept for binary compatibility, but UUID-only fluent ops will throw (api=null).
         * Prefer: new SurfaceHandle(id, kind, api)
         */
        @Deprecated
        public SurfaceHandle(int id, String kind) {
            this.id = id;
            this.kind = kind;
            this.api = null;
        }

        @HostAccess.Export public int id() { return id; }
        @HostAccess.Export public String kind() { return kind; }

        // -------------------------
        // Fluent methods
        // -------------------------

        @HostAccess.Export
        public SurfaceHandle setMaterial(Object materialHandle) {
            requireApi("setMaterial");
            api.setMaterial(this, materialHandle);
            return this;
        }

        @HostAccess.Export
        public SurfaceHandle applyMaterialToChildren(Object materialHandle) {
            requireApi("applyMaterialToChildren");
            api.applyMaterialToChildren(this, materialHandle);
            return this;
        }

        @HostAccess.Export
        public SurfaceHandle setTransform(Value cfg) {
            requireApi("setTransform");
            api.setTransform(this, cfg);
            return this;
        }

        @HostAccess.Export
        public SurfaceHandle setShadowMode(String mode) {
            requireApi("setShadowMode");
            api.setShadowMode(this, mode);
            return this;
        }

        @HostAccess.Export
        public SurfaceHandle attachToRoot() {
            requireApi("attachToRoot");
            api.attachToRoot(this);
            return this;
        }

        @HostAccess.Export
        public SurfaceHandle detach() {
            requireApi("detach");
            api.detach(this);
            return this;
        }

        @HostAccess.Export
        public SurfaceHandle destroy() {
            requireApi("destroy");
            api.destroy(this);
            return this;
        }

        @HostAccess.Export
        public boolean exists() {
            requireApi("exists");
            return api.exists(this);
        }

        // -------------------------
        // UUID-only entity binding (fluent)
        // -------------------------

        @HostAccess.Export
        public String attachedEntityUuid() {
            requireApi("attachedEntityUuid");
            return api.attachedEntityUuid(this);
        }

        @HostAccess.Export
        public SurfaceHandle attachEntity(Object entityUuid) {
            requireApi("attachEntity");
            api.attachEntity(this, entityUuid);
            return this;
        }

        @HostAccess.Export
        public SurfaceHandle detachFromEntity() {
            // keep name for scripts, but UUID-only internally
            requireApi("detachFromEntityUuid");
            api.detachFromEntityUuid(this);
            return this;
        }

        // -------------------------
        // Legacy entityId binding (kept but throws)
        // -------------------------

        /**
         * @deprecated removed in UUID-only mode.
         */
        @Deprecated
        @HostAccess.Export
        public int attachedEntity() {
            throw new IllegalStateException("SurfaceHandle.attachedEntity(entityId) removed (UUID-only). Use attachedEntityUuid().");
        }

        /**
         * @deprecated removed in UUID-only mode.
         */
        @Deprecated
        @HostAccess.Export
        public SurfaceHandle attach(int entityId) {
            throw new IllegalStateException("SurfaceHandle.attach(entityId) removed (UUID-only). Use attachEntity(uuid).");
        }

        // -------------------------
        // Bounds / picking (handle-scoped)
        // -------------------------

        @HostAccess.Export
        public WorldBounds getWorldBounds() {
            requireApi("getWorldBounds");
            return api.getWorldBounds(this);
        }

        @HostAccess.Export
        public Hit[] raycast(Value cfg) {
            requireApi("raycast");
            return api.raycast(this, cfg);
        }

        @HostAccess.Export
        public Hit[] pickUnderCursor() {
            requireApi("pickUnderCursor");
            return api.pickUnderCursor(this);
        }

        @HostAccess.Export
        public Hit[] pickUnderCursorCfg(Value cfg) {
            requireApi("pickUnderCursorCfg");
            return api.pickUnderCursorCfg(this, cfg);
        }

        @HostAccess.Export
        public SurfaceHandle setCull(String hint) {
            requireApi("setCull");
            api.setCull(this, hint);
            return this;
        }

        @HostAccess.Export
        public SurfaceHandle setVisible(boolean visible) {
            requireApi("setVisible");
            api.setVisible(this, visible);
            return this;
        }

        private void requireApi(String op) {
            if (api == null) {
                throw new IllegalStateException(
                        "SurfaceHandle." + op + ": api is null. " +
                                "This handle was created with legacy constructor SurfaceHandle(id,kind) " +
                                "— update registry/handle creation to pass SurfaceApi reference."
                );
            }
        }

        @Override public String toString() {
            return "SurfaceHandle{id=" + id + ", kind=" + kind + "}";
        }
    }

    final class WorldBounds {
        @HostAccess.Export public final String type; // "box" | "sphere" | "none" | "other"
        @HostAccess.Export public final float cx, cy, cz;
        @HostAccess.Export public final float ex, ey, ez; // extents (box)
        @HostAccess.Export public final float radius;     // sphere

        public WorldBounds(String type, float cx, float cy, float cz,
                           float ex, float ey, float ez, float radius) {
            this.type = type;
            this.cx = cx; this.cy = cy; this.cz = cz;
            this.ex = ex; this.ey = ey; this.ez = ez;
            this.radius = radius;
        }
    }

    final class Hit {
        @HostAccess.Export public final String geometry; // name
        @HostAccess.Export public final float distance;
        @HostAccess.Export public final float px, py, pz; // contact point
        @HostAccess.Export public final float nx, ny, nz; // contact normal

        public Hit(String geometry, float distance,
                   float px, float py, float pz,
                   float nx, float ny, float nz) {
            this.geometry = geometry;
            this.distance = distance;
            this.px = px; this.py = py; this.pz = pz;
            this.nx = nx; this.ny = ny; this.nz = nz;
        }
    }
}
