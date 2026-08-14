/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.scene.Spatial
 *  com.jme3.terrain.geomipmap.TerrainQuad
 *  org.foxesworld.kalitech.engine.api.EngineApiImpl
 *  org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi$SurfaceHandle
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.terrain;

import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import java.util.HashMap;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class TerrainPhysics {
    private final EngineApiImpl engine;
    private final SurfaceRegistry registry;

    public TerrainPhysics(EngineApiImpl engine) {
        this.engine = engine;
        this.registry = engine.getSurfaceRegistry();
    }

    public Object bind(SurfaceApi.SurfaceHandle surface, LuaValueRef cfg) {
        if (surface == null) {
            throw new IllegalArgumentException("terrain.physics: surface is required");
        }
        Spatial s = this.registry.get(surface.id());
        if (s == null) {
            throw new IllegalArgumentException("terrain.physics: unknown surfaceId=" + surface.id());
        }
        boolean isTerrain = s instanceof TerrainQuad;
        HashMap<String, Object> out = new HashMap<String, Object>();
        out.put("surface", Integer.valueOf(surface.id()));
        out.put("mass", Double.valueOf(0.0));
        out.put("kinematic", Boolean.valueOf(false));
        HashMap<String, String> col = new HashMap<String, String>();
        col.put("type", isTerrain ? "dynamicMesh" : "mesh");
        out.put("collider", col);
        if (cfg != null && !cfg.isNull() && cfg.hasMembers()) {
            for (String k : cfg.getMemberKeys()) {
                if ("collider".equals(k)) {
                    String type;
                    LuaValueRef c = cfg.getMember(k);
                    if (c == null || c.isNull()) continue;
                    LuaValueRef t = LuaCfg.member((LuaValueRef)c, (String)"type");
                    String string = type = t != null && !t.isNull() ? LuaCfg.str((LuaValueRef)c, (String)"type", (String)"") : "";
                    if (isTerrain && "mesh".equalsIgnoreCase(type)) {
                        HashMap<String, Object> c2 = new HashMap<String, Object>();
                        for (String ck : c.getMemberKeys()) {
                            c2.put(ck, c.getMember(ck));
                        }
                        c2.put("type", "dynamicMesh");
                        out.put("collider", c2);
                        continue;
                    }
                    out.put("collider", c);
                    continue;
                }
                out.put(k, cfg.getMember(k));
            }
        }
        return this.engine.physics().body(out);
    }
}

