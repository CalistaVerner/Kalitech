package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

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

    /** @deprecated use {@code target.attachedEntity()} */
    @Deprecated
    @HostAccess.Export
    int attachedEntity(SurfaceHandle target); // 0 if none

    /** @deprecated use {@code target.attach(entityId)} */
    @Deprecated
    @HostAccess.Export
    void attach(SurfaceHandle target, int entityId);

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

    // Эти 2 — остаются на API, т.к. они “world pick”, а не “handle pick”.
    @HostAccess.Export
    Hit[] pickUnderCursor();

    @HostAccess.Export
    Hit[] pickUnderCursorCfg(Value cfg);

    // -------------------------
    // Host-safe DTOs for JS
    // -------------------------

    final class SurfaceHandle {
        public final int id;
        private final String kind;

        // 👇 НЕ экспортируемое поле, но внутри host-объекта доступно
        final SurfaceApi api;

        public SurfaceHandle(int id, String kind, SurfaceApi api) {
            this.id = id;
            this.kind = kind;
            this.api = api;
        }

        // Если где-то ещё создаётся старым конструктором — оставим,
        // но тогда handle-methods будут падать (api=null).
        // Лучше убрать после миграции.
        @Deprecated
        public SurfaceHandle(int id, String kind) {
            this.id = id;
            this.kind = kind;
            this.api = null;
        }

        @HostAccess.Export public int id() { return id; }
        @HostAccess.Export public String kind() { return kind; }

        // -------------------------
        // NEW fluent methods (JS: ground.setMaterial(mat))
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

        @HostAccess.Export
        public int attachedEntity() {
            requireApi("attachedEntity");
            return api.attachedEntity(this);
        }

        @HostAccess.Export
        public SurfaceHandle attach(int entityId) {
            requireApi("attach");
            api.attach(this, entityId);
            return this;
        }

        @HostAccess.Export
        public SurfaceHandle detachFromEntity() {
            requireApi("detachFromEntity");
            api.detachFromEntity(this);
            return this;
        }

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