package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

import java.util.Map;

/**
 * Runtime API module catalog for tooling and scripts.
 */
public interface ModulesApi {

    /**
     * List registered API module ids.
     */
    @LuaExport
    String[] list();

    /**
     * Describe a module by id, or null if not found.
     */
    @LuaExport
    Map<String, Object> describe(String id);

    /**
     * Describe all modules in registration order.
     */
    @LuaExport
    Map<String, Object>[] describeAll();
}
