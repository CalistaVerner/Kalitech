/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector2f
 *  com.jme3.math.Vector3f
 *  com.jme3.renderer.Camera
 *  com.jme3.scene.control.Control
 *  com.jme3.terrain.Terrain
 *  com.jme3.terrain.geomipmap.TerrainLodControl
 *  com.jme3.terrain.geomipmap.TerrainQuad
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 *  org.foxesworld.kalitech.engine.script.lua.LuaObject
 */
package org.foxesworld.kalitech.engine.modules.terrain;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.control.Control;
import com.jme3.terrain.Terrain;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.geomipmap.TerrainQuad;
import java.util.HashMap;
import java.util.Map;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

public final class TerrainOps {
    private final Camera camera;

    public TerrainOps(Camera camera) {
        this.camera = camera;
    }

    public void lod(TerrainQuad tq, LuaValueRef cfg) {
        boolean enable;
        boolean bl = enable = cfg == null || cfg.isNull() ? true : LuaCfg.bool((LuaValueRef)cfg, (String)"enabled", (boolean)true);
        if (!enable) {
            TerrainLodControl c = (TerrainLodControl)tq.getControl(TerrainLodControl.class);
            if (c != null) {
                tq.removeControl((Control)c);
            }
            return;
        }
        TerrainLodControl existing = (TerrainLodControl)tq.getControl(TerrainLodControl.class);
        if (existing != null) {
            existing.setCamera(this.camera);
            return;
        }
        tq.addControl((Control)new TerrainLodControl((Terrain)tq, this.camera));
    }

    public void scale(TerrainQuad tq, double xzScale, LuaValueRef cfg) {
        float xz = (float)LuaCfg.clamp((double)xzScale, (double)1.0E-4, (double)1000000.0);
        float y = tq.getLocalScale().y;
        if (cfg != null && !cfg.isNull() && cfg.hasMember("yScale")) {
            y = (float)LuaCfg.clamp((double)LuaCfg.num((LuaValueRef)cfg, (String)"yScale", (double)y), (double)1.0E-4, (double)1000000.0);
        }
        tq.setLocalScale(xz, y, xz);
    }

    public double heightAt(TerrainQuad tq, double x, double z, boolean world) {
        float lz;
        float lx;
        if (world) {
            Vector3f local = tq.worldToLocal(new Vector3f((float)x, 0.0f, (float)z), null);
            lx = local.x;
            lz = local.z;
        } else {
            lx = (float)x;
            lz = (float)z;
        }
        Float h = Float.valueOf(tq.getHeight(new Vector2f(lx, lz)));
        if (h == null) {
            return Double.NaN;
        }
        if (!world) {
            return h.floatValue();
        }
        Vector3f wp = tq.localToWorld(new Vector3f(0.0f, h.floatValue(), 0.0f), null);
        return wp.y;
    }

    public LuaObject normalAt(TerrainQuad tq, double x, double z, boolean world) {
        float lz;
        float lx;
        if (world) {
            Vector3f local = tq.worldToLocal(new Vector3f((float)x, 0.0f, (float)z), null);
            lx = local.x;
            lz = local.z;
        } else {
            lx = (float)x;
            lz = (float)z;
        }
        Vector3f n = tq.getNormal(new Vector2f(lx, lz));
        if (n == null) {
            return LuaObject.fromMap(Map.of("x", Double.NaN, "y", Double.NaN, "z", Double.NaN));
        }
        if (world) {
            n = tq.getWorldRotation().mult(n);
        }
        HashMap<String, Double> out = new HashMap<String, Double>(4, 1.0f);
        out.put("x", Double.valueOf(n.x));
        out.put("y", Double.valueOf(n.y));
        out.put("z", Double.valueOf(n.z));
        return LuaObject.fromMap(out);
    }
}

