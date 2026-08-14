package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public interface TimeApi {
    /** last frame tpf (seconds) */
    @LuaExport double tpf();

    /** alias for tpf (seconds) */
    @LuaExport double dt();

    /** monotonic time since engine start (seconds) */
    @LuaExport double now();

    /** frame counter since start */
    @LuaExport long frame();
}