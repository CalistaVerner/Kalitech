// FILE: org/foxesworld/kalitech/engine/api/interfaces/MaterialApi.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.interfaces;

import com.jme3.material.Material;
import org.foxesworld.kalitech.engine.api.types.MaterialHandle;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface MaterialApi {

    // Lua-visible

    @LuaExport
    MaterialHandle create(LuaValueRef cfg);

    @LuaExport
    int createId(LuaValueRef cfg);

    @LuaExport
    MaterialHandle getById(int id);

    @LuaExport
    void destroy(MaterialHandle handle);

    @LuaExport
    void destroyById(int id);

    @LuaExport
    void set(MaterialHandle handle, LuaValueRef params);

    @LuaExport
    void setById(int id, LuaValueRef params);

    // Java-only (engine-internal)
    Material material(MaterialHandle handle);

    Material materialById(int id);
}