package org.foxesworld.kalitech.engine.api.impl.terrain;

import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.impl.SurfaceRegistry;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.graalvm.polyglot.Value;

import java.util.HashMap;

public final class TerrainPhysics {

    private final EngineApiImpl engine;
    private final SurfaceRegistry registry;

    public TerrainPhysics(EngineApiImpl engine, SurfaceRegistry registry) {
        this.engine = engine;
        this.registry = registry;
    }

    public Object bind(SurfaceApi.SurfaceHandle surface, Value cfg) {
        if (surface == null) throw new IllegalArgumentException("terrain.physics: surface handle is required");

        // validate exists early
        if (registry.get(surface.id()) == null) {
            throw new IllegalArgumentException("terrain.physics: unknown surface id=" + surface.id());
        }

        HashMap<String, Object> m = new HashMap<>();
        m.put("surface", surface.id());

        // defaults
        m.put("mass", 0.0);
        m.put("kinematic", true);

        HashMap<String, Object> col = new HashMap<>();
        col.put("type", "mesh");
        m.put("collider", col);

        // override by cfg
        if (cfg != null && !cfg.isNull() && cfg.hasMembers()) {
            for (String k : cfg.getMemberKeys()) {
                m.put(k, cfg.getMember(k));
            }
        }

        return engine.physics().body(m);
    }
}