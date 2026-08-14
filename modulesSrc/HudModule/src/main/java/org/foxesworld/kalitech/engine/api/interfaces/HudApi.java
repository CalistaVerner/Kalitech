/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 */
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public interface HudApi {
    @LuaExport
    public HudLayerHandle createLayer(String var1);

    @LuaExport
    public void destroyLayer(HudLayerHandle var1);

    @LuaExport
    public void clearLayer(HudLayerHandle var1);

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=true, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public String getText(HudElementHandle var1);

    @LuaExport
    public HudElementHandle addContainer(HudLayerHandle var1, float var2, float var3);

    @LuaExport
    public HudElementHandle addPanel(HudLayerHandle var1, float var2, float var3, float var4, float var5);

    @LuaExport
    public HudElementHandle addLabel(HudLayerHandle var1, String var2, float var3, float var4);

    @LuaExport
    public void setCursorEnabled(boolean var1);

    @LuaExport
    public void setCursorEnabled(boolean var1, boolean var2);

    @LuaExport
    public HudElementHandle addContainer(HudLayerHandle var1, HudElementHandle var2, float var3, float var4);

    @LuaExport
    public HudElementHandle addPanel(HudLayerHandle var1, HudElementHandle var2, float var3, float var4, float var5, float var6);

    @LuaExport
    public HudElementHandle addLabel(HudLayerHandle var1, HudElementHandle var2, String var3, float var4, float var5);

    @LuaExport
    public void setText(HudElementHandle var1, String var2);

    @LuaExport
    public void setVisible(HudElementHandle var1, boolean var2);

    @LuaExport
    public void setPosition(HudElementHandle var1, float var2, float var3);

    @LuaExport
    public void setSize(HudElementHandle var1, float var2, float var3);

    @LuaExport
    public void setBgColor(HudElementHandle var1, double var2, double var4, double var6, double var8);

    @LuaExport
    public void setTextColor(HudElementHandle var1, double var2, double var4, double var6, double var8);

    @LuaExport
    public void remove(HudElementHandle var1);

    @LuaExport
    public HudViewport viewport();

    @LuaExport
    public void setFontSize(HudElementHandle var1, float var2);

    public static final class HudViewport {
        public final int w;
        public final int h;

        public HudViewport(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @LuaExport
        public int w() {
            return this.w;
        }

        @LuaExport
        public int h() {
            return this.h;
        }
    }

    public static final class HudElementHandle {
        public final int id;

        public HudElementHandle(int id) {
            this.id = id;
        }

        @LuaExport
        public int id() {
            return this.id;
        }
    }

    public static final class HudLayerHandle {
        public final int id;

        public HudLayerHandle(int id) {
            this.id = id;
        }

        @LuaExport
        public int id() {
            return this.id;
        }
    }
}

