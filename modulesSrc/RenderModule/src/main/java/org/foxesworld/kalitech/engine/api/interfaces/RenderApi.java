/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface RenderApi {
    @LuaExport
    public void ensureScene();

    public void __resetWorldCache(String var1);

    @LuaExport
    public void ambientCfg(LuaValueRef var1);

    @LuaExport
    public void sunCfg(LuaValueRef var1);

    @LuaExport
    public void sunShadowsCfg(LuaValueRef var1);

    @LuaExport
    public void fogCfg(LuaValueRef var1);

    @LuaExport
    public void postCfg(LuaValueRef var1);
}

