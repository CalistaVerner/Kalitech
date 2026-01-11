// FILE: org/foxesworld/kalitech/engine/api/impl/TerrainApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.TerrainApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.terrain.*;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

public final class TerrainApiImpl extends AbstractApiModule implements TerrainApi {

    private EngineApiImpl engine;
    private SurfaceRegistry registry;

    private TerrainEmitter emitter;
    private TerrainFactory factory;
    private TerrainUV uv;
    private TerrainOps ops;
    private TerrainEditOps editOps;
    private TerrainPhysics physics;
    private TerrainNoise noise;

    public TerrainApiImpl() {
        super("terrain", "Terrain", "3.0.0"); // UUID-only binding
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);

        this.engine = ctx.engine;
        this.registry = engine.getSurfaceRegistry();

        this.emitter = new TerrainEmitter(engine);
        this.factory = new TerrainFactory(engine.getAssets());
        this.uv = new TerrainUV();
        this.ops = new TerrainOps(engine.getApp().getCamera());
        this.editOps = new TerrainEditOps();
        this.physics = new TerrainPhysics(engine);
        this.noise = new TerrainNoise();
    }

    @Override
    public void detach() {
        this.noise = null;
        this.physics = null;
        this.editOps = null;
        this.ops = null;
        this.uv = null;
        this.factory = null;
        this.emitter = null;
        this.registry = null;
        this.engine = null;
        super.detach();
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

        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(h, mh);

        Value u = member(cfg, "uv");
        if (u != null && !u.isNull()) uv(h, u);

        if (bool(cfg, "attach", TerrainDefaults.ATTACH_DEFAULT)) registry.attachToRoot(h.id());

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
    // OPS
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

    @HostAccess.Export
    public float[] heightmap(SurfaceApi.SurfaceHandle handle) {
        TerrainQuad tq = requireTerrain(handle);
        return editOps.heightmapCopy(tq);
    }

    @HostAccess.Export
    public void setHeight(SurfaceApi.SurfaceHandle handle, double x, double z, double height, boolean world) {
        TerrainQuad tq = requireTerrain(handle);
        editOps.setHeight(tq, x, z, height, world);
    }

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

    @HostAccess.Export
    public void rebuild(SurfaceApi.SurfaceHandle handle) {
        TerrainQuad tq = requireTerrain(handle);
        editOps.rebuild(tq);
    }

    // ---------------------------------------------------------------------
    // PROCEDURAL
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
    public ProxyObject normalAt(SurfaceApi.SurfaceHandle handle, double x, double z, boolean world) {
        TerrainQuad tq = requireTerrain(handle);
        return ops.normalAt(tq, x, z, world);
    }

    @HostAccess.Export
    public ProxyObject normalAt(SurfaceApi.SurfaceHandle handle, double x, double z) {
        return normalAt(handle, x, z, true);
    }

    // ---------------------------------------------------------------------
    // PHYSICS
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public Object physics(SurfaceApi.SurfaceHandle surface, Value cfg) {
        return physics.bind(surface, cfg);
    }

    // ---------------------------------------------------------------------
    // ATTACH / DETACH (UUID-only)
    // ---------------------------------------------------------------------

    /**
     * UUID-only attach helper for JS:
     * terrain.attachEntity(handle, uuid)
     * or
     * terrain.attachEntity(handle, someEntity.uuid)
     */
    @HostAccess.Export
    public void attachEntity(SurfaceApi.SurfaceHandle handle, Object entityUuid) {
        if (handle == null) throw new IllegalArgumentException("terrain.attachEntity: handle is required");
        engine.surface().attachEntity(handle, entityUuid);
        // SurfaceApiImpl emits engine.surface.* events; we also emit terrain-scoped signal.
        emitter.emit("engine.terrain.attached", "surfaceId", handle.id(), "uuid", String.valueOf(entityUuid));
    }

    /**
     * UUID-only detach helper for JS.
     */
    @HostAccess.Export
    public void detachEntity(SurfaceApi.SurfaceHandle handle) {
        if (handle == null) throw new IllegalArgumentException("terrain.detachEntity: handle is required");
        // new API name (UUID-only)
        engine.surface().detachFromEntity(handle);
        emitter.emit("engine.terrain.detached", "surfaceId", handle.id());
    }

    /**
     * Interface legacy method: entityId is forbidden.
     * Keep override to compile, but fail loudly.
     */
    @HostAccess.Export
    @Override
    public void attach(SurfaceApi.SurfaceHandle handle, int entityId) {
        throw new IllegalStateException("terrain.attach(handle, entityId) removed (UUID-only). Use terrain.attachEntity(handle, uuid).");
    }

    /**
     * Interface detach stays meaningful (no entityId involved): detach terrain from entity (UUID-only under the hood).
     */
    @HostAccess.Export
    @Override
    public void detach(SurfaceApi.SurfaceHandle handle) {
        if (handle == null) throw new IllegalArgumentException("terrain.detach: handle is required");
        engine.surface().detachFromEntity(handle);
        emitter.emit("engine.terrain.detached", "surfaceId", handle.id());
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