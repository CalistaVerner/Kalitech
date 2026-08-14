/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.material.Material
 *  com.jme3.math.Vector2f
 *  com.jme3.scene.Geometry
 *  com.jme3.scene.Node
 *  com.jme3.scene.Spatial
 *  com.jme3.terrain.geomipmap.TerrainQuad
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.terrain;

import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class TerrainUV {
    private static void scaleUvRecursively(Spatial root, Vector2f scale) {
        if (root == null || scale == null) {
            return;
        }
        if (root instanceof Geometry) {
            Geometry g = (Geometry)root;
            try {
                if (g.getMesh() != null) {
                    g.getMesh().scaleTextureCoordinates(scale);
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            return;
        }
        if (root instanceof Node) {
            Node n = (Node)root;
            try {
                for (Spatial ch : n.getChildren()) {
                    if (ch == null) continue;
                    TerrainUV.scaleUvRecursively(ch, scale);
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    private static Vector2f readUvScale(LuaValueRef uv, float defX, float defY) {
        try {
            if (uv == null || uv.isNull()) {
                return null;
            }
            if (uv.hasArrayElements()) {
                float sx = (float)(uv.getArraySize() > 0L ? uv.getArrayElement(0L).asDouble() : (double)defX);
                float sy = (float)(uv.getArraySize() > 1L ? uv.getArrayElement(1L).asDouble() : (double)defY);
                return new Vector2f(sx, sy);
            }
            LuaValueRef sc = LuaCfg.member((LuaValueRef)uv, (String)"scale");
            if (sc != null && !sc.isNull()) {
                if (sc.hasArrayElements()) {
                    float sx = (float)(sc.getArraySize() > 0L ? sc.getArrayElement(0L).asDouble() : (double)defX);
                    float sy = (float)(sc.getArraySize() > 1L ? sc.getArrayElement(1L).asDouble() : (double)defY);
                    return new Vector2f(sx, sy);
                }
                if (sc.isNumber()) {
                    float s = (float)sc.asDouble();
                    return new Vector2f(s, s);
                }
            }
            if (uv.hasMember("sx") || uv.hasMember("sy")) {
                float sx = (float)(uv.hasMember("sx") ? uv.getMember("sx").asDouble() : (double)defX);
                float sy = (float)(uv.hasMember("sy") ? uv.getMember("sy").asDouble() : (double)defY);
                return new Vector2f(sx, sy);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    private static void trySetFloat(Material m, String name, float v) {
        try {
            m.setFloat(name, v);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private static void trySetVector2(Material m, String name, Vector2f v) {
        try {
            m.setVector2(name, v);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public void apply(Spatial s, LuaValueRef cfgOrUv) {
        Vector2f scale;
        if (s == null) {
            throw new IllegalArgumentException("terrain.uv: spatial is null");
        }
        if (cfgOrUv == null || cfgOrUv.isNull()) {
            return;
        }
        LuaValueRef uv = cfgOrUv;
        LuaValueRef nested = LuaCfg.member((LuaValueRef)cfgOrUv, (String)"uv");
        if (nested != null && !nested.isNull()) {
            uv = nested;
        }
        if ((scale = TerrainUV.readUvScale(uv, 1.0f, 1.0f)) == null) {
            return;
        }
        if (s instanceof Geometry) {
            Geometry g = (Geometry)s;
            if (g.getMesh() != null) {
                g.getMesh().scaleTextureCoordinates(scale);
            }
            return;
        }
        if (s instanceof TerrainQuad) {
            TerrainQuad tq = (TerrainQuad)s;
            Material m = tq.getMaterial();
            if (m != null) {
                TerrainUV.trySetFloat(m, "TexScale", scale.x);
                TerrainUV.trySetVector2(m, "UvScale", scale);
                TerrainUV.trySetVector2(m, "uvScale", scale);
                TerrainUV.trySetFloat(m, "Tex1Scale", scale.x);
                TerrainUV.trySetFloat(m, "Tex2Scale", scale.x);
                TerrainUV.trySetFloat(m, "Tex3Scale", scale.x);
                TerrainUV.trySetFloat(m, "Tex4Scale", scale.x);
            }
            TerrainUV.scaleUvRecursively((Spatial)tq, scale);
            tq.setUserData("uvScale", (Object)scale);
            return;
        }
        s.setUserData("uvScale", (Object)scale);
    }
}

