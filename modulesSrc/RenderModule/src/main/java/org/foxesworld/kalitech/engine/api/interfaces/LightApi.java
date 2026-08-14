/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.api.impl.LightApiImpl;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface LightApi {
    @LuaExport
    public LightApiImpl.LightHandle create(LuaValueRef var1);

    @LuaExport
    public void set(LightApiImpl.LightHandle var1, LuaValueRef var2);

    @LuaExport
    public void enable(LightApiImpl.LightHandle var1, boolean var2);

    @LuaExport
    public boolean exists(LightApiImpl.LightHandle var1);

    @LuaExport
    public void destroy(LightApiImpl.LightHandle var1);

    @LuaExport
    public LuaValueRef get(LightApiImpl.LightHandle var1);

    @LuaExport
    public LuaValueRef list();
}

