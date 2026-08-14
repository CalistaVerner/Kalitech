package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.api.impl.LightApiImpl;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface LightApi {

    @LuaExport
    LightApiImpl.LightHandle create(LuaValueRef cfg);

    @LuaExport
    void set(LightApiImpl.LightHandle handle, LuaValueRef cfg);

    @LuaExport
    void enable(LightApiImpl.LightHandle handle, boolean enabled);

    @LuaExport
    boolean exists(LightApiImpl.LightHandle handle);

    @LuaExport
    void destroy(LightApiImpl.LightHandle handle);

    @LuaExport
    LuaValueRef get(LightApiImpl.LightHandle handle);

    @LuaExport
    LuaValueRef list();
}