package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface DebugDrawApi {

    @LuaExport void enabled(boolean v);
    @LuaExport boolean enabled();

    @LuaExport void clear();

    @LuaExport void line(LuaValueRef cfg);
    @LuaExport void ray(LuaValueRef cfg);
    @LuaExport void axes(LuaValueRef cfg);

    @LuaExport void tick(double tpf);
}