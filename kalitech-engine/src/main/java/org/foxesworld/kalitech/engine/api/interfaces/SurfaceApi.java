// FILE: org/foxesworld/kalitech/engine/api/interfaces/SurfaceApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

/**
 * Surface API.
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

    @LuaExport
    void setMaterial(SurfaceHandle target, Object materialHandleOrCfg);

    @LuaExport
    void applyMaterialToChildren(SurfaceHandle target, Object materialHandle);

    @LuaExport
    void setTransform(SurfaceHandle target, LuaValueRef cfg);

    @LuaExport
    void setShadowMode(SurfaceHandle target, String mode); // Off|Receive|Cast|CastAndReceive

    @LuaExport
    void attachToRoot(SurfaceHandle target);

    @LuaExport
    void detach(SurfaceHandle target);

    @LuaExport
    void destroy(SurfaceHandle target);

    @LuaExport
    boolean exists(SurfaceHandle target);

    @LuaExport
    void setCull(SurfaceHandle target, String hint);

    @LuaExport
    int attachedBody(int surfaceId);

    @LuaExport
    void setVisible(SurfaceHandle target, boolean visible);

    // ------------------------------------------------------------
    // Bounds / picking (handle-scoped)
    // ------------------------------------------------------------

    @LuaExport
    WorldBounds getWorldBounds(SurfaceHandle target);

    @LuaExport
    Hit[] raycast(SurfaceHandle target, LuaValueRef cfg);

    @LuaExport
    Hit[] pickUnderCursor(SurfaceHandle target);

    @LuaExport
    Hit[] pickUnderCursorCfg(SurfaceHandle target, LuaValueRef cfg);

    // ------------------------------------------------------------
    // World picking (API-scoped)
    // ------------------------------------------------------------

    @LuaExport
    Hit[] pickUnderCursor();

    @LuaExport
    Hit[] pickUnderCursorCfg(LuaValueRef cfg);

    // ------------------------------------------------------------
    // UUID-only entity binding
    // ------------------------------------------------------------

    /**
     * Attach a surface to entity UUID.
     * (Public UUID-only contract. No entityId accepted.)
     */
    @LuaExport
    void attachEntity(SurfaceHandle target, Object entityUuid);

    /**
     * Detach mapping by surface handle (if any).
     * Kept name for scripts: "detachFromEntity".
     */
    @LuaExport
    void detachFromEntity(SurfaceHandle target);

    /**
     * Get attached entity UUID, or empty string if none.
     */
    @LuaExport
    String attachedEntityUuid(SurfaceHandle target);

    // ------------------------------------------------------------
    // Host-safe DTOs for Lua
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

        @LuaExport public int id() { return id; }
        @LuaExport public String kind() { return kind; }

        // -------------------------
        // Fluent methods
        // -------------------------

        @LuaExport
        public SurfaceHandle setMaterial(Object materialHandleOrCfg) {
            requireApi("setMaterial");
            api.setMaterial(this, materialHandleOrCfg);
            return this;
        }

        @LuaExport
        public SurfaceHandle applyMaterialToChildren(Object materialHandle) {
            requireApi("applyMaterialToChildren");
            api.applyMaterialToChildren(this, materialHandle);
            return this;
        }

        @LuaExport
        public SurfaceHandle setTransform(LuaValueRef cfg) {
            requireApi("setTransform");
            api.setTransform(this, cfg);
            return this;
        }

        @LuaExport
        public SurfaceHandle setShadowMode(String mode) {
            requireApi("setShadowMode");
            api.setShadowMode(this, mode);
            return this;
        }

        @LuaExport
        public SurfaceHandle attachToRoot() {
            requireApi("attachToRoot");
            api.attachToRoot(this);
            return this;
        }

        @LuaExport
        public SurfaceHandle detach() {
            requireApi("detach");
            api.detach(this);
            return this;
        }

        @LuaExport
        public SurfaceHandle destroy() {
            requireApi("destroy");
            api.destroy(this);
            return this;
        }

        @LuaExport
        public boolean exists() {
            requireApi("exists");
            return api.exists(this);
        }

        @LuaExport
        public SurfaceHandle setCull(String hint) {
            requireApi("setCull");
            api.setCull(this, hint);
            return this;
        }

        @LuaExport
        public SurfaceHandle setVisible(boolean visible) {
            requireApi("setVisible");
            api.setVisible(this, visible);
            return this;
        }

        // -------------------------
        // UUID-only entity binding (fluent)
        // -------------------------

        @LuaExport
        public String attachedEntityUuid() {
            requireApi("attachedEntityUuid");
            return api.attachedEntityUuid(this);
        }

        @LuaExport
        public SurfaceHandle attachEntity(Object entityUuid) {
            requireApi("attachEntity");
            api.attachEntity(this, entityUuid);
            return this;
        }

        @LuaExport
        public SurfaceHandle detachFromEntity() {
            requireApi("detachFromEntity");
            api.detachFromEntity(this);
            return this;
        }

        // -------------------------
        // Bounds / picking (handle-scoped)
        // -------------------------

        @LuaExport
        public WorldBounds getWorldBounds() {
            requireApi("getWorldBounds");
            return api.getWorldBounds(this);
        }

        @LuaExport
        public Hit[] raycast(LuaValueRef cfg) {
            requireApi("raycast");
            return api.raycast(this, cfg);
        }

        @LuaExport
        public Hit[] pickUnderCursor() {
            requireApi("pickUnderCursor");
            return api.pickUnderCursor(this);
        }

        @LuaExport
        public Hit[] pickUnderCursorCfg(LuaValueRef cfg) {
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
        @LuaExport public final String type; // "box" | "sphere" | "none" | "other"
        @LuaExport public final float cx, cy, cz;
        @LuaExport public final float ex, ey, ez; // extents (box)
        @LuaExport public final float radius;     // sphere

        public WorldBounds(String type, float cx, float cy, float cz,
                           float ex, float ey, float ez, float radius) {
            this.type = type;
            this.cx = cx; this.cy = cy; this.cz = cz;
            this.ex = ex; this.ey = ey; this.ez = ez;
            this.radius = radius;
        }
    }

    final class Hit {
        @LuaExport public final String geometry; // name
        @LuaExport public final float distance;
        @LuaExport public final float px, py, pz; // contact point
        @LuaExport public final float nx, ny, nz; // contact normal

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