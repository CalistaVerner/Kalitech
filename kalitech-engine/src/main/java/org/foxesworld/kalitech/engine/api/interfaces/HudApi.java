// FILE: org/foxesworld/kalitech/engine/api/interfaces/HudApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

public interface HudApi {

    /**
     * Create HUD element.
     * cfg.kind: "group" | "rect" (MVP)
     *
     * Common cfg:
     * - parent: HudHandle | id | {id}
     * - anchor: "topLeft"|"top"|"topRight"|"left"|"center"|"right"|"bottomLeft"|"bottom"|"bottomRight"
     * - pivot:  "topLeft"|"top"|"topRight"|"left"|"center"|"right"|"bottomLeft"|"bottom"|"bottomRight"
     * - offset: {x,y}
     * - visible: boolean
     *
     * rect cfg:
     * - size: {w,h}
     * - color: {r,g,b,a} in 0..1
     */
    @HostAccess.Export
    HudHandle create(Object cfg);

    /** Update element properties (partial). */
    @HostAccess.Export
    void set(Object handleOrId, Object cfg);

    /** Destroy element (recursive for groups). */
    @HostAccess.Export
    void destroy(Object handleOrId);

    /** Viewport info for UI logic in JS (w,h,cx,cy). */
    @HostAccess.Export
    Viewport viewport();

    /** Internal: called every frame to re-layout (handles resize). */
    void __tick();

    // ------------------------
    // JS-visible handle + DTOs
    // ------------------------

    final class HudHandle {
        public final int id;
        public HudHandle(int id) { this.id = id; }
        @HostAccess.Export public int id() { return id; }
        @Override public String toString() { return "HudHandle(" + id + ")"; }
    }

    final class Viewport {
        public final int w;
        public final int h;
        public final float cx;
        public final float cy;

        public Viewport(int w, int h) {
            this.w = w;
            this.h = h;
            this.cx = w * 0.5f;
            this.cy = h * 0.5f;
        }
    }
}