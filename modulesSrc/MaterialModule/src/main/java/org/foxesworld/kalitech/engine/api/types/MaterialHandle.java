package org.foxesworld.kalitech.engine.api.types;

import com.jme3.material.Material;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;

/**
 * Stable material handle. The optional direct material reference supports
 * module-internal handoff without exposing renderer objects to Lua.
 */
public final class MaterialHandle {

    private final int id;
    private final Material material;

    public MaterialHandle(int id) {
        this(id, null);
    }

    public MaterialHandle(int id, Material material) {
        this.id = id;
        this.material = material;
    }

    @LuaExport
    public int id() {
        return id;
    }

    public Material __material() {
        return material;
    }

    @Override
    public String toString() {
        return "MaterialHandle(" + id + ")";
    }
}
