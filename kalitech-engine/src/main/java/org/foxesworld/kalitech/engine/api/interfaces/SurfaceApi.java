package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface SurfaceApi {

    @HostAccess.Export
    SurfaceHandle handle(int id);

    @HostAccess.Export
    void setMaterial(SurfaceHandle target, Object materialHandle);

    /** Apply material to all child geometries (Node/model hierarchies). */
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
    int attachedEntity(SurfaceHandle target); // 0 if none

    @HostAccess.Export
    void attach(SurfaceHandle target, int entityId);

    @HostAccess.Export
    void detachFromEntity(SurfaceHandle target);

    /** World bounds snapshot (BoundingBox or BoundingSphere). */
    @HostAccess.Export
    WorldBounds getWorldBounds(SurfaceHandle target);

    /**
     * Raycast against this surface hierarchy.
     * cfg:
     *  {
     *    origin: [x,y,z] or {x,y,z},
     *    dir:    [x,y,z] or {x,y,z},
     *    max: 1000.0,     // optional
     *    limit: 16,       // optional
     *    onlyClosest: true|false // optional, default true
     *  }
     */
    @HostAccess.Export
    Hit[] raycast(SurfaceHandle target, Value cfg);

    /**
     * ✅ Editor/gameplay helper:
     * Ray from active camera through current mouse cursor and collide with target surface.
     *
     * Returns array of hits (0 or 1 hit by default).
     */
    @HostAccess.Export
    Hit[] pickUnderCursor(SurfaceHandle target);

    /**
     * Same as pickUnderCursor, but configurable.
     * cfg:
     *  {
     *    max: 1000.0,        // optional
     *    limit: 16,          // optional (used if onlyClosest=false)
     *    onlyClosest: true,  // optional (default true)
     *    screenX: 123,       // optional override (pixels)
     *    screenY: 456        // optional override (pixels)
     *  }
     */
    @HostAccess.Export
    Hit[] pickUnderCursorCfg(SurfaceHandle target, Value cfg);

    // -------------------------
    // Host-safe DTOs for JS
    // -------------------------

    final class SurfaceHandle {
        private final int id;
        private final String kind;

        public SurfaceHandle(int id, String kind) {
            this.id = id;
            this.kind = kind;
        }

        @HostAccess.Export public int id() { return id; }
        @HostAccess.Export public String kind() { return kind; }

        @Override public String toString() { return "SurfaceHandle{id=" + id + ", kind=" + kind + "}"; }
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