// FILE: org/foxesworld/kalitech/engine/api/impl/TerrainApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.math.Vector3f;
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

    private static void requireCfg(Value cfg, String where) {
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException(where + ": cfg is null");
        }
    }

    private static void requireHandle(SurfaceApi.SurfaceHandle handle, String where) {
        if (handle == null) throw new IllegalArgumentException(where + ": handle is required");
    }

    /**
     * Correct world/local height:
     * - If world=false: x,z are local; returns local height.
     * - If world=true: x,z are world; returns world Y (full transform-aware).
     */
    private static double heightAtSafe(TerrainQuad tq, double x, double z, boolean world) {
        Vector3f localXZ;
        if (world) {
            // Convert world (x,?,z) into terrain local space for sampling.
            localXZ = tq.worldToLocal(new Vector3f((float) x, 0f, (float) z), null);
        } else {
            localXZ = new Vector3f((float) x, 0f, (float) z);
        }

        // TerrainQuad sampling is in local x/z.
        // getHeight can return NaN outside the terrain; keep as NaN.
        float hLocal = tq.getHeight(new com.jme3.math.Vector2f(localXZ.x, localXZ.z));
        if (!Float.isFinite(hLocal)) return Double.NaN;

        if (!world) return (double) hLocal;

        // Convert sampled local point back to world to get final Y.
        Vector3f wp = tq.localToWorld(new Vector3f(localXZ.x, hLocal, localXZ.z), null);
        return (double) wp.y;
    }

    /**
     * Finite-difference normal in local space, then transform to world if needed.
     */
    private static Vector3f normalAtSafe(TerrainQuad tq, double x, double z, boolean world) {
        // Choose epsilon based on terrain scale in X/Z to stay stable.
        Vector3f s = tq.getWorldScale();
        float eps = 0.25f;
        if (s != null) {
            float sx = Math.max(1e-3f, s.x);
            float sz = Math.max(1e-3f, s.z);
            eps = Math.max(0.05f, Math.min(1.0f, 0.25f * Math.max(sx, sz)));
        }

        // We compute heights in the same coordinate mode the caller requested.
        double hR = heightAtSafe(tq, x + eps, z, world);
        double hL = heightAtSafe(tq, x - eps, z, world);
        double hU = heightAtSafe(tq, x, z + eps, world);
        double hD = heightAtSafe(tq, x, z - eps, world);

        if (!(Double.isFinite(hR) && Double.isFinite(hL) && Double.isFinite(hU) && Double.isFinite(hD))) {
            return new Vector3f(0, 1, 0);
        }

        // Build normal from slopes.
        // If world=true these heights are world-y; if world=false they are local-y.
        float dx = (float) (hR - hL);
        float dz = (float) (hU - hD);

        // "Up" component relative to eps. Bigger up makes smoother normals.
        Vector3f n = new Vector3f(-dx, 2f * eps, -dz);
        n.normalizeLocal();

        // If world=false we’re done (local normal).
        if (!world) return n;

        // For world normals, ensure direction respects terrain rotation.
        // n is already in world-space slope basis because h* are world-y, but
        // rotation still matters if terrain is rotated (rare). Apply rotation only.
        tq.getWorldRotation().mult(n, n);
        n.normalizeLocal();
        return n;
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle terrain(Value cfg) {
        requireCfg(cfg, "terrain.terrain(cfg)");
        TerrainQuad tq = factory.createTerrainFromHeightmap(cfg);
        return registerTerrainLike(tq, "terrain", cfg);
    }

    @HostAccess.Export
    public SurfaceApi.SurfaceHandle terrainHeights(Value cfg) {
        requireCfg(cfg, "terrain.terrainHeights(cfg)");
        TerrainQuad tq = factory.createTerrainFromHeights(cfg);
        return registerTerrainLike(tq, "terrainHeights", cfg);
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle quad(Value cfg) {
        requireCfg(cfg, "terrain.quad(cfg)");
        Geometry g = factory.createQuad(cfg);
        return registerSpatialLike(g, "quad", cfg);
    }

    // ---------------------------------------------------------------------
    // OPS
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle plane(Value cfg) {
        requireCfg(cfg, "terrain.plane(cfg)");
        Geometry g = factory.createPlane(cfg);
        return registerSpatialLike(g, "plane", cfg);
    }

    private SurfaceApi.SurfaceHandle registerTerrainLike(TerrainQuad tq, String type, Value cfg) {
        SurfaceApi.SurfaceHandle h = registerSpatialLike(tq, type, cfg);

        Value lod = member(cfg, "lod");
        if (lod != null && !lod.isNull() && bool(lod, "enabled", true)) {
            lod(h, lod);
        }

        emitter.emit("engine.terrain.created", "surfaceId", h.id(), "type", type);
        return h;
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

    private SurfaceApi.SurfaceHandle registerSpatialLike(Spatial s, String type, Value cfg) {
        SurfaceApiImpl.applyTransform(s, cfg);

        SurfaceApi.SurfaceHandle h = registry.register(s, type, engine.surface());

        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(h, mh);

        Value u = member(cfg, "uv");
        if (u != null && !u.isNull()) uv(h, u);

        if (bool(cfg, "attach", TerrainDefaults.ATTACH_DEFAULT)) {
            registry.attachToRoot(h.id());
        }

        return h;
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
    public void material(SurfaceApi.SurfaceHandle handle, Object materialHandleOrCfg) {
        requireHandle(handle, "terrain.material");
        engine.surface().setMaterial(handle, materialHandleOrCfg);
    }

    @HostAccess.Export
    public void uv(SurfaceApi.SurfaceHandle handle, Value cfgOrUv) {
        requireHandle(handle, "terrain.uv");
        Spatial s = requireSurface(handle);
        uv.apply(s, cfgOrUv);
    }

    @HostAccess.Export
    public void setHeightmap(SurfaceApi.SurfaceHandle handle, Value cfg) {
        requireHandle(handle, "terrain.setHeightmap");
        requireCfg(cfg, "terrain.setHeightmap(handle,cfg)");

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
    public double heightAt(SurfaceApi.SurfaceHandle handle, double x, double z) {
        return heightAt(handle, x, z, true);
    }

    @HostAccess.Export
    public float[] perlinHeights(Value cfg) {
        requireCfg(cfg, "terrain.perlinHeights(cfg)");
        return noise.perlinHeights(cfg);
    }

    @HostAccess.Export
    public ProxyObject normalAt(SurfaceApi.SurfaceHandle handle, double x, double z) {
        return normalAt(handle, x, z, true);
    }

    // ---------------------------------------------------------------------
    // PHYSICS
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public float[] ridgedHeights(Value cfg) {
        requireCfg(cfg, "terrain.ridgedHeights(cfg)");
        return noise.ridgedHeights(cfg);
    }

    // ---------------------------------------------------------------------
    // ATTACH / DETACH (UUID-only)
    // ---------------------------------------------------------------------

    /**
     * IMPORTANT:
     * We compute world/local height robustly here to avoid wrong world conversions
     * (your camera logs show terrY ~ -790 while the world is ~ -10).
     */
    @HostAccess.Export
    public double heightAt(SurfaceApi.SurfaceHandle handle, double x, double z, boolean world) {
        TerrainQuad tq = requireTerrain(handle);
        return heightAtSafe(tq, x, z, world);
    }

    /**
     * Stable normal via finite differences around the query point.
     * Works for any TerrainQuad transform and avoids ops.normalAt inconsistencies.
     */
    @HostAccess.Export
    public ProxyObject normalAt(SurfaceApi.SurfaceHandle handle, double x, double z, boolean world) {
        TerrainQuad tq = requireTerrain(handle);
        Vector3f n = normalAtSafe(tq, x, z, world);
        return ProxyObject.fromMap(java.util.Map.of(
                "x", (double) n.x,
                "y", (double) n.y,
                "z", (double) n.z
        ));
    }

    @HostAccess.Export
    public Object physics(SurfaceApi.SurfaceHandle surface, Value cfg) {
        requireHandle(surface, "terrain.physics");
        return physics.bind(surface, cfg);
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void attachEntity(SurfaceApi.SurfaceHandle handle, Object entityUuid) {
        requireHandle(handle, "terrain.attachEntity");
        engine.surface().attachEntity(handle, entityUuid);
        emitter.emit("engine.terrain.attached", "surfaceId", handle.id(), "uuid", String.valueOf(entityUuid));
    }

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

    @HostAccess.Export
    @Override
    public void detachEntity(SurfaceApi.SurfaceHandle handle) {
        requireHandle(handle, "terrain.detachEntity");
        engine.surface().detachFromEntity(handle);
        emitter.emit("engine.terrain.detached", "surfaceId", handle.id());
    }

    @HostAccess.Export
    @Override
    public void detach(SurfaceApi.SurfaceHandle handle) {
        requireHandle(handle, "terrain.detach");
        engine.surface().detachFromEntity(handle);
        emitter.emit("engine.terrain.detached", "surfaceId", handle.id());
    }
}
