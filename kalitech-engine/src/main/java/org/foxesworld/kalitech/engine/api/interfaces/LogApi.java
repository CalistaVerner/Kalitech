package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public interface LogApi {
    @LuaExport void info(String msg);
    @LuaExport void warn(String msg);
    @LuaExport void error(String msg);
    @LuaExport void debug(String msg);
    @LuaExport void unformatted(String msg);
}