package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface WorldApi {

    /**
     * Spawn entity from prefab.
     * args: { name?: string, prefab: string }
     * returns entity UUID (string)
     */
    @LuaExport
    String spawn(LuaValueRef args);

    /**
     * Find entity UUID by Name (stored in ComponentStore byName "Name").
     * returns "" if not found.
     */
    @LuaExport
    String findByName(String name);

    /**
     * Destroy entity by UUID.
     */
    @LuaExport
    void destroy(String uuid);
}