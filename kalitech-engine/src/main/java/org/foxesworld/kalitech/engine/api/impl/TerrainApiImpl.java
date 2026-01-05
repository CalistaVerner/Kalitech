package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.TerrainApi;
import org.foxesworld.kalitech.engine.modules.terrain.*;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

public final class TerrainApiImpl implements TerrainApi {

    private final EngineApiImpl engine;
    private final SurfaceRegistry registry;

    private final TerrainEmitter emitter;
    private final TerrainFactory factory;
    private final TerrainUV uv;
    private final TerrainOps ops;
    private final TerrainEditOps editOps;
    private final TerrainPhysics physics;
    private final TerrainNoise noise;

    public TerrainApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.registry = engine.getSurfaceRegistry();

        this.emitter = new TerrainEmitter(engine.getBus());
        this.factory = new TerrainFactory(engine.getAssets());
        this.uv = new TerrainUV();
        this.ops = new TerrainOps(engine.getApp().getCamera());
        this.editOps = new TerrainEditOps();
        this.physics = new TerrainPhysics(engine, registry);
        this.noise = new TerrainNoise();
    }

    // ---------------------------------------------------------------------
    // CREATION
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle terrain(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrain.terrain(cfg): cfg is null");

        TerrainQuad tq = factory.createTerrainFromHeightmap(cfg);
        SurfaceApiImpl.applyTransform(tq, cfg);

        SurfaceApi.SurfaceHandle h = registry.register(tq, "terrain", engine.surface());

        // material if provided
        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(h, mh);

        // uv if provided
        Value u = member(cfg, "uv");
        if (u != null && !u.isNull()) uv(h, u);

        // attach default true
        if (bool(cfg, "attach", TerrainDefaults.ATTACH_DEFAULT)) registry.attachToRoot(h.id());

        // lod if provided
        Value lod = member(cfg, "lod");
        if (lod != null && !lod.isNull() && bool(lod, "enabled", true)) lod(h, lod);

        emitter.emit("engine.terrain.created", "surfaceId", h.id(), "type", "terrain");
        return h;
    }

    @HostAccess.Export
    public SurfaceApi.SurfaceHandle terrainHeights(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrain.terrainHeights(cfg): cfg is null");

        TerrainQuad tq = factory.createTerrainFromHeights(cfg);
        SurfaceApiImpl.applyTransform(tq, cfg);

        SurfaceApi.SurfaceHandle h = registry.register(tq, "terrain", engine.surface());

        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(h, mh);

        Value u = member(cfg, "uv");
        if (u != null && !u.isNull()) uv(h, u);

        if (bool(cfg, "attach", TerrainDefaults.ATTACH_DEFAULT)) registry.attachToRoot(h.id());

        Value lod = member(cfg, "lod");
        if (lod != null && !lod.isNull() && bool(lod, "enabled", true)) lod(h, lod);

        emitter.emit("engine.terrain.created", "surfaceId", h.id(), "type", "terrainHeights");
        return h;
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle quad(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrain.quad(cfg): cfg is null");

        Geometry g = factory.createQuad(cfg);
        SurfaceApiImpl.applyTransform(g, cfg);

        SurfaceApi.SurfaceHandle h = registry.register(g, "quad", engine.surface());

        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(h, mh);

        Value u = member(cfg, "uv");
        if (u != null && !u.isNull()) uv(h, u);

        if (bool(cfg, "attach", TerrainDefaults.ATTACH_DEFAULT)) registry.attachToRoot(h.id());

        emitter.emit("engine.terrain.created", "surfaceId", h.id(), "type", "quad");
        return h;
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle plane(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrain.plane(cfg): cfg is null");

        Geometry g = factory.createPlane(cfg);
        SurfaceApiImpl.applyTransform(g, cfg);

        SurfaceApi.SurfaceHandle h = registry.register(g, "plane", engine.surface());

        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(h, mh);

        Value u = member(cfg, "uv");
        if (u != null && !u.isNull()) uv(h, u);

        if (bool(cfg, "attach", TerrainDefaults.ATTACH_DEFAULT)) registry.attachToRoot(h.id());

        emitter.emit("engine.terrain.created", "surfaceId", h.id(), "type", "plane");
        return h;
    }

    // ---------------------------------------------------------------------
    // OPS (universal where possible)
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public void material(SurfaceApi.SurfaceHandle handle, Object materialHandleOrCfg) {
        if (handle == null) throw new IllegalArgumentException("terrain.material: handle is required");
        engine.surface().setMaterial(handle, materialHandleOrCfg);
    }

    @HostAccess.Export
    public void uv(SurfaceApi.SurfaceHandle handle, Value cfgOrUv) {
        if (handle == null) throw new IllegalArgumentException("terrain.uv: handle is required");
        Spatial s = requireSurface(handle);
        uv.apply(s, cfgOrUv);
    }

    @HostAccess.Export
    public void lod(SurfaceApi.SurfaceHandle handle, Value cfg) {
        TerrainQuad tq = requireTerrain(handle);
        ops.lod(tq, cfg);
    }

    @HostAccess.Export
    public void scale(SurfaceApi.SurfaceHandle handle, double xzScale, Value cfg) {
        TerrainQuad tq = requireTerrain(handle);
        ops.scale(tq, xzScale, cfg);
    }

    // ---------------------------------------------------------------------
    // TERRAINQUAD (editing/query)
    // ---------------------------------------------------------------------

    /**
     * Replace terrain heightmap at runtime.
     * cfg:
     *  - heights: ArrayLike<number> (Float32Array recommended)
     *  - size?: int (optional validation)
     *  - rebuild?: boolean (default true)
     */
    @HostAccess.Export
    public void setHeightmap(SurfaceApi.SurfaceHandle handle, Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrain.setHeightmap(handle,cfg): cfg is null");
        TerrainQuad tq = requireTerrain(handle);

        Value hv = member(cfg, "heights");
        if (hv == null || hv.isNull()) throw new IllegalArgumentException("terrain.setHeightmap: cfg.heights is required");

        float[] heights = readFloatArray(hv);
        int size = i32(cfg, "size", 0);
        if (size > 0) {
            int need = size * size;
            if (heights.length != need) {
                throw new IllegalArgumentException("terrain.setHeightmap: heights length=" + heights.length + " expected=" + need + " (size=" + size + ")");
            }
        }

        editOps.setHeightmap(tq, heights, bool(cfg, "rebuild", true));
    }

    /** Get a copy of the current heightmap (float[]). */
    @HostAccess.Export
    public float[] heightmap(SurfaceApi.SurfaceHandle handle) {
        TerrainQuad tq = requireTerrain(handle);
        return editOps.heightmapCopy(tq);
    }

    /** Set height at (x,z). world=true uses world x/z, otherwise local. */
    @HostAccess.Export
    public void setHeight(SurfaceApi.SurfaceHandle handle, double x, double z, double height, boolean world) {
        TerrainQuad tq = requireTerrain(handle);
        editOps.setHeight(tq, x, z, height, world);
    }

    /** Add delta to height at (x,z). world=true uses world x/z, otherwise local. */
    @HostAccess.Export
    public void adjustHeight(SurfaceApi.SurfaceHandle handle, double x, double z, double delta, boolean world) {
        TerrainQuad tq = requireTerrain(handle);
        editOps.adjustHeight(tq, x, z, delta, world);
    }

    @HostAccess.Export
    public void setHeight(SurfaceApi.SurfaceHandle handle, double x, double z, double height) {
        setHeight(handle, x, z, height, true);
    }



    @HostAccess.Export
    public void adjustHeight(SurfaceApi.SurfaceHandle handle, double x, double z, double delta) {
        adjustHeight(handle, x, z, delta, true);
    }

    /** Force bounds/state refresh for tools after bulk edits. */
    @HostAccess.Export
    public void rebuild(SurfaceApi.SurfaceHandle handle) {
        TerrainQuad tq = requireTerrain(handle);
        editOps.rebuild(tq);
    }

    /*
    @HostAccess.Export
    public int size(SurfaceApi.SurfaceHandle handle) {
        TerrainQuad tq = requireTerrain(handle);
        return ops.size(tq);
    }

    @HostAccess.Export
    public int patchSize(SurfaceApi.SurfaceHandle handle) {
        TerrainQuad tq = requireTerrain(handle);
        return ops.patchSize(tq);
    } */

    // ---------------------------------------------------------------------
    // PROCEDURAL (noise generation for editor/runtime parity)
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public float[] perlinHeights(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrain.perlinHeights(cfg): cfg is null");
        return noise.perlinHeights(cfg);
    }

    @HostAccess.Export
    public float[] ridgedHeights(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrain.ridgedHeights(cfg): cfg is null");
        return noise.ridgedHeights(cfg);
    }

    @HostAccess.Export
    public double heightAt(SurfaceApi.SurfaceHandle handle, double x, double z, boolean world) {
        TerrainQuad tq = requireTerrain(handle);
        return ops.heightAt(tq, x, z, world);
    }

    @HostAccess.Export
    public double heightAt(SurfaceApi.SurfaceHandle handle, double x, double z) {
        return heightAt(handle, x, z, true);
    }

    @HostAccess.Export
    public Map<String, Double> normalAt(SurfaceApi.SurfaceHandle handle, double x, double z, boolean world) {
        TerrainQuad tq = requireTerrain(handle);
        return ops.normalAt(tq, x, z, world);
    }

    @HostAccess.Export
    public Map<String, Double> normalAt(SurfaceApi.SurfaceHandle handle, double x, double z) {
        return normalAt(handle, x, z, true);
    }

    // ---------------------------------------------------------------------
    // PHYSICS (UNIVERSAL)
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public Object physics(SurfaceApi.SurfaceHandle surface, Value cfg) {
        return physics.bind(surface, cfg);
    }

    // ---------------------------------------------------------------------
    // ATTACH / DETACH
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void attach(SurfaceApi.SurfaceHandle handle, int entityId) {
        engine.surface().attach(handle, entityId);
    }

    @HostAccess.Export
    @Override
    public void detach(SurfaceApi.SurfaceHandle handle) {
        engine.surface().detachFromEntity(handle);
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    private Spatial requireSurface(SurfaceApi.SurfaceHandle handle) {
        Spatial s = registry.get(handle.id());
        if (s == null) throw new IllegalArgumentException("terrain: unknown surface id=" + handle.id());
        return s;
    }

    private TerrainQuad requireTerrain(SurfaceApi.SurfaceHandle handle) {
        Spatial s = requireSurface(handle);
        if (!(s instanceof TerrainQuad tq)) {
            throw new IllegalArgumentException("terrain: surface id=" + handle.id()
                    + " is not TerrainQuad (type=" + s.getClass().getSimpleName() + ")");
        }
        return tq;
    }
}