/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 */
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public interface InputApi {
    @LuaExport
    public boolean keyDown(String var1);

    @LuaExport
    public boolean keyDown(int var1);

    @LuaExport
    public int keyCode(String var1);

    @LuaExport
    public double mouseX();

    @LuaExport
    public double mouseY();

    @LuaExport
    public double mouseDx();

    @LuaExport
    public double mouseDy();

    @LuaExport
    public Object cursorPosition();

    @LuaExport
    public double mouseDX();

    @LuaExport
    public double mouseDY();

    @LuaExport
    public Object mouseDelta();

    @LuaExport
    public Object consumeMouseDelta();

    @LuaExport
    public double wheelDelta();

    @LuaExport
    public double consumeWheelDelta();

    @LuaExport
    public boolean mouseDown(int var1);

    @LuaExport
    public void cursorVisible(boolean var1);

    @LuaExport
    public boolean cursorVisible();

    @LuaExport
    public void grabMouse(boolean var1);

    @LuaExport
    public boolean grabbed();

    @LuaExport
    public void endFrame();
}

