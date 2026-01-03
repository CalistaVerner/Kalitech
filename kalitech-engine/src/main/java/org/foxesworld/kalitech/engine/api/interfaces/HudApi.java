// FILE: org/foxesworld/kalitech/engine/api/interfaces/HudApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

public interface HudApi {

    final class HudLayerHandle {
        public final int id;
        public HudLayerHandle(int id) { this.id = id; }
        @HostAccess.Export public int id() { return id; }
    }

    final class HudElementHandle {
        public final int id;
        public HudElementHandle(int id) { this.id = id; }
        @HostAccess.Export public int id() { return id; }
    }

    // NEW: viewport DTO
    final class HudViewport {
        public final int w;
        public final int h;
        public HudViewport(int w, int h) { this.w = w; this.h = h; }
        @HostAccess.Export public int w() { return w; }
        @HostAccess.Export public int h() { return h; }
    }

    // lifecycle
    @HostAccess.Export HudLayerHandle createLayer(String name);
    @HostAccess.Export void destroyLayer(HudLayerHandle layer);
    @HostAccess.Export void clearLayer(HudLayerHandle layer);

    // elements (root)
    @HostAccess.Export HudElementHandle addContainer(HudLayerHandle layer, float x, float y);
    @HostAccess.Export HudElementHandle addPanel(HudLayerHandle layer, float x, float y, float w, float h);
    @HostAccess.Export HudElementHandle addLabel(HudLayerHandle layer, String text, float x, float y);

    @HostAccess.Export void setCursorEnabled(boolean enabled);
    @HostAccess.Export void setCursorEnabled(boolean enabled, boolean force);

    // elements (with parent)
    @HostAccess.Export HudElementHandle addContainer(HudLayerHandle layer, HudElementHandle parent, float x, float y);
    @HostAccess.Export HudElementHandle addPanel(HudLayerHandle layer, HudElementHandle parent, float x, float y, float w, float h);
    @HostAccess.Export HudElementHandle addLabel(HudLayerHandle layer, HudElementHandle parent, String text, float x, float y);

    // ops
    @HostAccess.Export void setText(HudElementHandle element, String text);
    @HostAccess.Export void setVisible(HudElementHandle element, boolean visible);
    @HostAccess.Export void setPosition(HudElementHandle element, float x, float y);
    @HostAccess.Export void setSize(HudElementHandle element, float w, float h);
    @HostAccess.Export void remove(HudElementHandle element);

    // NEW: viewport + typography
    @HostAccess.Export HudViewport viewport();

    // Sets preferred font size for Label/Panel/Container where supported (Label is main target)
    @HostAccess.Export void setFontSize(HudElementHandle element, float px);
}
