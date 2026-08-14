// FILE: org/foxesworld/kalitech/engine/api/interfaces/HudApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public interface HudApi {

    final class HudLayerHandle {
        public final int id;
        public HudLayerHandle(int id) { this.id = id; }
        @LuaExport public int id() { return id; }
    }

    final class HudElementHandle {
        public final int id;
        public HudElementHandle(int id) { this.id = id; }
        @LuaExport public int id() { return id; }
    }

    // NEW: viewport DTO
    final class HudViewport {
        public final int w;
        public final int h;
        public HudViewport(int w, int h) { this.w = w; this.h = h; }
        @LuaExport public int w() { return w; }
        @LuaExport public int h() { return h; }
    }

    // lifecycle
    @LuaExport HudLayerHandle createLayer(String name);
    @LuaExport void destroyLayer(HudLayerHandle layer);
    @LuaExport void clearLayer(HudLayerHandle layer);

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = true,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    String getText(HudElementHandle element);

    // elements (root)
    @LuaExport HudElementHandle addContainer(HudLayerHandle layer, float x, float y);
    @LuaExport HudElementHandle addPanel(HudLayerHandle layer, float x, float y, float w, float h);
    @LuaExport HudElementHandle addLabel(HudLayerHandle layer, String text, float x, float y);

    @LuaExport void setCursorEnabled(boolean enabled);
    @LuaExport void setCursorEnabled(boolean enabled, boolean force);

    // elements (with parent)
    @LuaExport HudElementHandle addContainer(HudLayerHandle layer, HudElementHandle parent, float x, float y);
    @LuaExport HudElementHandle addPanel(HudLayerHandle layer, HudElementHandle parent, float x, float y, float w, float h);
    @LuaExport HudElementHandle addLabel(HudLayerHandle layer, HudElementHandle parent, String text, float x, float y);

    // ops
    @LuaExport void setText(HudElementHandle element, String text);
    @LuaExport void setVisible(HudElementHandle element, boolean visible);
    @LuaExport void setPosition(HudElementHandle element, float x, float y);
    @LuaExport void setSize(HudElementHandle element, float w, float h);

    @LuaExport
    void setBgColor(HudElementHandle element, double r, double g, double b, double a);

    @LuaExport
    void setTextColor(HudElementHandle element, double r, double g, double b, double a);

    @LuaExport void remove(HudElementHandle element);

    // NEW: viewport + typography
    @LuaExport HudViewport viewport();

    // Sets preferred font size for Label/Panel/Container where supported (Label is main target)
    @LuaExport void setFontSize(HudElementHandle element, float px);
}