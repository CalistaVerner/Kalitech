/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.asset.AssetManager
 *  com.jme3.material.Material
 *  com.jme3.math.ColorRGBA
 *  com.jme3.scene.Geometry
 *  com.jme3.terrain.geomipmap.TerrainQuad
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.terrain;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.foxesworld.kalitech.engine.modules.terrain.TerrainDefaults;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class TerrainMaterial {
    private final AssetManager assets;

    public TerrainMaterial(AssetManager assets) {
        this.assets = assets;
    }

    public static ColorRGBA readColor(LuaValueRef cfg, String key, ColorRGBA def) {
        LuaValueRef v = LuaCfg.member((LuaValueRef)cfg, (String)key);
        if (v == null || v.isNull()) {
            return def;
        }
        try {
            if (v.hasArrayElements() && v.getArraySize() >= 3L) {
                float r = (float)v.getArrayElement(0L).asDouble();
                float g = (float)v.getArrayElement(1L).asDouble();
                float b = (float)v.getArrayElement(2L).asDouble();
                float a = v.getArraySize() >= 4L ? (float)v.getArrayElement(3L).asDouble() : 1.0f;
                return new ColorRGBA(r, g, b, a);
            }
            if (v.hasMembers()) {
                float r = (float)LuaCfg.num((LuaValueRef)v, (String)"r", (double)def.r);
                float g = (float)LuaCfg.num((LuaValueRef)v, (String)"g", (double)def.g);
                float b = (float)LuaCfg.num((LuaValueRef)v, (String)"b", (double)def.b);
                float a = (float)LuaCfg.num((LuaValueRef)v, (String)"a", (double)def.a);
                return new ColorRGBA(r, g, b, a);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return def;
    }

    public void applyTerrainDefault(TerrainQuad tq, LuaValueRef cfg) {
        Material def = new Material(this.assets, "Common/MatDefs/Misc/Unshaded.j3md");
        def.setColor("Color", TerrainMaterial.readColor(cfg, "color", TerrainDefaults.TERRAIN_COLOR));
        tq.setMaterial(def);
    }

    public void applyGeometryDefault(Geometry g, LuaValueRef cfg) {
        Material def = new Material(this.assets, "Common/MatDefs/Misc/Unshaded.j3md");
        def.setColor("Color", TerrainMaterial.readColor(cfg, "color", TerrainDefaults.GEOM_COLOR));
        g.setMaterial(def);
    }
}

