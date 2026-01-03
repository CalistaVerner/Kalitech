package org.foxesworld.kalitech.engine.api.impl.terrain;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.api.impl.terrain.TerrainValues.member;
import static org.foxesworld.kalitech.engine.api.impl.terrain.TerrainValues.num;

public final class TerrainMaterial {

    private final AssetManager assets;

    public TerrainMaterial(AssetManager assets) {
        this.assets = assets;
    }

    public void applyTerrainDefault(TerrainQuad tq, Value cfg) {
        Material def = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        def.setColor("Color", readColor(cfg, "color", TerrainDefaults.TERRAIN_COLOR));
        tq.setMaterial(def);
    }

    public void applyGeometryDefault(Geometry g, Value cfg) {
        Material def = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        def.setColor("Color", readColor(cfg, "color", TerrainDefaults.GEOM_COLOR));
        g.setMaterial(def);
    }

    public static ColorRGBA readColor(Value cfg, String key, ColorRGBA def) {
        Value v = member(cfg, key);
        if (v == null || v.isNull()) return def;

        try {
            if (v.hasArrayElements() && v.getArraySize() >= 3) {
                float r = (float) v.getArrayElement(0).asDouble();
                float g = (float) v.getArrayElement(1).asDouble();
                float b = (float) v.getArrayElement(2).asDouble();
                float a = (v.getArraySize() >= 4) ? (float) v.getArrayElement(3).asDouble() : 1f;
                return new ColorRGBA(r, g, b, a);
            }
            if (v.hasMembers()) {
                float r = (float) num(v, "r", def.r);
                float g = (float) num(v, "g", def.g);
                float b = (float) num(v, "b", def.b);
                float a = (float) num(v, "a", def.a);
                return new ColorRGBA(r, g, b, a);
            }
        } catch (Throwable ignored) {}
        return def;
    }
}