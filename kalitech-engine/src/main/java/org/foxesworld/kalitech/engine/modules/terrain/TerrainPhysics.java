package org.foxesworld.kalitech.engine.modules.terrain;

import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.member;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.str;


public final class TerrainPhysics {

    private final EngineApiImpl engine;
    private final SurfaceRegistry registry;

    public TerrainPhysics(EngineApiImpl engine, SurfaceRegistry registry) {
        this.engine = engine;
        this.registry = registry;
    }

    public Object bind(SurfaceApi.SurfaceHandle surface, Value cfg) {
        if (surface == null) throw new IllegalArgumentException("terrain.physics: surface is required");

        Spatial s = registry.get(surface.id());
        if (s == null) throw new IllegalArgumentException("terrain.physics: unknown surfaceId=" + surface.id());

        boolean isTerrain = (s instanceof TerrainQuad);

        // base defaults
        Map<String, Object> out = new HashMap<>();
        out.put("surface", surface.id());
        out.put("mass", 0.0);
        out.put("kinematic", false); // статик лучше как НЕ-кинематик

        Map<String, Object> col = new HashMap<>();
        col.put("type", isTerrain ? "dynamicMesh" : "mesh");
        out.put("collider", col);

        // merge cfg overrides
        if (cfg != null && !cfg.isNull() && cfg.hasMembers()) {
            for (String k : cfg.getMemberKeys()) {
                if ("collider".equals(k)) {
                    Value c = cfg.getMember(k);
                    if (c != null && !c.isNull()) {
                        Value t = member(c, "type");
                        String type = (t != null && !t.isNull()) ? str(c, "type", "") : "";

                        //  форсим dynamicMesh для TerrainQuad
                        if (isTerrain && "mesh".equalsIgnoreCase(type)) {
                            Map<String, Object> c2 = new HashMap<>();
                            for (String ck : c.getMemberKeys()) c2.put(ck, c.getMember(ck));
                            c2.put("type", "dynamicMesh");
                            out.put("collider", c2);
                        } else {
                            out.put("collider", c);
                        }
                    }
                } else {
                    out.put(k, cfg.getMember(k));
                }
            }
        }

        return engine.physics().body(out);
    }
}