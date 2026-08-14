/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.material.Material
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.interfaces;

import com.jme3.material.Material;
import org.foxesworld.kalitech.engine.api.types.MaterialHandle;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface MaterialApi {
    @LuaExport
    public MaterialHandle create(LuaValueRef var1);

    @LuaExport
    public int createId(LuaValueRef var1);

    @LuaExport
    public MaterialHandle getById(int var1);

    @LuaExport
    public void destroy(MaterialHandle var1);

    @LuaExport
    public void destroyById(int var1);

    @LuaExport
    public void set(MaterialHandle var1, LuaValueRef var2);

    @LuaExport
    public void setById(int var1, LuaValueRef var2);

    public Material material(MaterialHandle var1);

    public Material materialById(int var1);
}

