// FILE: org/foxesworld/kalitech/engine/api/interfaces/SurfaceApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * Surface API (NO legacy).
 *
 * Contract:
 * - API accepts ONLY SurfaceHandle (no raw ids, no "Object target", no coercion).
 * - Entity binding is UUID-only (string) in public API.
 * - World-pick methods stay on API (not handle-scoped).
 */
public interface SurfaceApi {

    // ------------------------------------------------------------
    // Core surface operations (handle-scoped)
    // ------------------------------------------------------------

    @HostAccess.Export
    void setMaterial(SurfaceHandle target, Object materialHandleOrCfg);

    @HostAccess.Export
    void applyMaterialToChildren(SurfaceHandle target, Object materialHandle);

    @HostAccess.Export
    void setTransform(SurfaceHandle target, Value cfg);

    @HostAccess.Export
    void setShadowMode(SurfaceHandle target, String mode); // Off|Receive|Cast|CastAndReceive

    @HostAccess.Export
    void attachToRoot(SurfaceHandle target);

    @HostAccess.Export
    void detach(SurfaceHandle target);

    @HostAccess.Export
    void destroy(SurfaceHandle target);

    @HostAccess.Export
    boolean exists(SurfaceHandle target);

    @HostAccess.Export
    void setCull(SurfaceHandle target, String hint);

    @HostAccess.Export
    int attachedBody(int surfaceId);

    @HostAccess.Export
    void setVisible(SurfaceHandle target, boolean visible);

    // ------------------------------------------------------------
    // Bounds / picking (handle-scoped)
    // ------------------------------------------------------------

    @HostAccess.Export
    WorldBounds getWorldBounds(SurfaceHandle target);

    @HostAccess.Export
    Hit[] raycast(SurfaceHandle target, Value cfg);

    @HostAccess.Export
    Hit[] pickUnderCursor(SurfaceHandle target);

    @HostAccess.Export
    Hit[] pickUnderCursorCfg(SurfaceHandle target, Value cfg);

    // ------------------------------------------------------------
    // World picking (API-scoped)
    // ------------------------------------------------------------

    @HostAccess.Export
    Hit[] pickUnderCursor();

    @HostAccess.Export
    Hit[] pickUnderCursorCfg(Value cfg);

    // ------------------------------------------------------------
    // UUID-only entity binding
    // ------------------------------------------------------------

    /**
     * Attach a surface to entity UUID.
     * (Public UUID-only contract. No entityId accepted.)
     */
    @HostAccess.Export
    void attachEntity(SurfaceHandle target, Object entityUuid);

    /**
     * Detach mapping by surface handle (if any).
     * Kept name for scripts: "detachFromEntity".
     */
    @HostAccess.Export
    void detachFromEntity(SurfaceHandle target);

    /**
     * Get attached entity UUID, or empty string if none.
     */
    @HostAccess.Export
    String attachedEntityUuid(SurfaceHandle target);

    // ------------------------------------------------------------
    // Host-safe DTOs for JS
    // ------------------------------------------------------------

    final class SurfaceHandle {
        public final int id;
        private final String kind;

        // host-only reference; required for fluent calls
        final SurfaceApi api;

        public SurfaceHandle(int id, String kind, SurfaceApi api) {
            this.id = id;
            this.kind = kind;
            this.api = api;
        }

        @HostAccess.Export public int id() { return id; }
        @HostAccess.Export public String kind() { return kind; }

        // -------------------------
        // Fluent methods
        // -------------------------

        @HostAccess.Export
        public SurfaceHandle setMaterial(Object materialHandleOrCfg) {
            requireApi("setMaterial");
            api.setMaterial(this, materialHandleOrCfg);
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
            requireApi("detachFromEntity");
            api.detachFromEntity(this);
            return this;
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

        private void requireApi(String op) {
            if (api == null) {
                throw new IllegalStateException(
                        "SurfaceHandle." + op + ": api is null. " +
                                "Handle must be created by registry/api with SurfaceApi reference."
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