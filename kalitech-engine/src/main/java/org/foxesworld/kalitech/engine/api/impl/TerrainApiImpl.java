package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.TerrainApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.terrain.*;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

/**
 * Terrain API (script-facing).
 *
 * <p>Contract:
 * <ul>
 *   <li>All scene graph mutations and TerrainQuad edits happen on the JME thread via {@code onJme*} helpers.</li>
 *   <li>Surface/entity binding is UUID-only (delegated to {@link SurfaceApi}).</li>
 *   <li>No legacy constructors and no manual ApiContext wiring.</li>
 * </ul>
 */
public final class TerrainApiImpl extends AbstractApiModule implements TerrainApi {

    private SurfaceRegistry registry;

    private TerrainEmitter emitter;
    private TerrainFactory factory;
    private TerrainUV uv;
    private TerrainOps ops;
    private TerrainEditOps editOps;
    private TerrainPhysics physics;
    private TerrainNoise noise;

    public TerrainApiImpl() {
        super("terrain", "Terrain", "3.0.0");
    }

    private static void requireCfg(Value cfg, String where) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException(where + ": cfg is null");
    }

    private static double heightAtSafe(TerrainQuad tq, double x, double z, boolean world) {
        Vector3f local;
        if (world) {
            local = tq.worldToLocal(new Vector3f((float) x, 0f, (float) z), null);
        } else {
            local = new Vector3f((float) x, 0f, (float) z);
        }

        float hLocal = tq.getHeight(new Vector2f(local.x, local.z));
        if (!Float.isFinite(hLocal)) return Double.NaN;
        if (!world) return (double) hLocal;

        Vector3f wp = tq.localToWorld(new Vector3f(local.x, hLocal, local.z), null);
        return (double) wp.y;
    }

    // ---------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------

    private static void requireHandle(SurfaceApi.SurfaceHandle handle, String where) {
        if (handle == null) throw new IllegalArgumentException(where + ": handle is required");
    }

    private static Vector3f normalAtSafe(TerrainQuad tq, double x, double z, boolean world) {
        Vector3f s = tq.getWorldScale();
        float eps = 0.25f;
        if (s != null) {
            float sx = Math.max(1e-3f, s.x);
            float sz = Math.max(1e-3f, s.z);
            eps = Math.max(0.05f, Math.min(1.0f, 0.25f * Math.max(sx, sz)));
        }

        double hR = heightAtSafe(tq, x + eps, z, world);
        double hL = heightAtSafe(tq, x - eps, z, world);
        double hU = heightAtSafe(tq, x, z + eps, world);
        double hD = heightAtSafe(tq, x, z - eps, world);

        if (!(Double.isFinite(hR) && Double.isFinite(hL) && Double.isFinite(hU) && Double.isFinite(hD))) {
            return new Vector3f(0f, 1f, 0f);
        }

        float dx = (float) (hR - hL);
        float dz = (float) (hU - hD);

        Vector3f n = new Vector3f(-dx, 2f * eps, -dz);
        n.normalizeLocal();

        if (!world) return n;

        Quaternion wr = tq.getWorldRotation();
        wr.mult(n, n);
        n.normalizeLocal();
        return n;
    }

    // ---------------------------------------------------------------------
    // Height/normal sampling (transform-aware)
    // ---------------------------------------------------------------------

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
        super.detach();
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);

        this.registry = Objects.requireNonNull(engine.getSurfaceRegistry(), "engine.surfaceRegistry");

        this.emitter = new TerrainEmitter(engine);
        this.factory = new TerrainFactory(engine.getAssets());
        this.uv = new TerrainUV();
        this.ops = new TerrainOps(engine.getApp().getCamera());
        this.editOps = new TerrainEditOps();
        this.physics = new TerrainPhysics(engine);
        this.noise = new TerrainNoise();
    }

    // ---------------------------------------------------------------------
    // Creation
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle terrain(Value cfg) {
        requireCfg(cfg, "terrain.terrain(cfg)");

        // Parse/create on caller thread (keeps Value away from JME thread).
        final TerrainQuad tq = factory.createTerrainFromHeightmap(cfg);
        return registerTerrainLike(tq, "terrain", cfg);
    }

    @HostAccess.Export
    public SurfaceApi.SurfaceHandle terrainHeights(Value cfg) {
        requireCfg(cfg, "terrain.terrainHeights(cfg)");

        final TerrainQuad tq = factory.createTerrainFromHeights(cfg);
        return registerTerrainLike(tq, "terrainHeights", cfg);
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle quad(Value cfg) {
        requireCfg(cfg, "terrain.quad(cfg)");

        final Geometry g = factory.createQuad(cfg);
        return registerSpatialLike(g, "quad", cfg);
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle plane(Value cfg) {
        requireCfg(cfg, "terrain.plane(cfg)");

        final Geometry g = factory.createPlane(cfg);
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

    private SurfaceApi.SurfaceHandle registerSpatialLike(Spatial s, String type, Value cfg) {
        requireCfg(cfg, "terrain.registerSpatialLike(cfg)");

        final Value matCfg = member(cfg, "material");
        final Value uvCfg = member(cfg, "uv");
        final boolean attach = bool(cfg, "attach", TerrainDefaults.ATTACH_DEFAULT);

        return onJmeSync("terrain.registerSpatialLike", () -> {
            SurfaceApi.SurfaceHandle h = registry.register(s, type, engine.surface());

            if (matCfg != null && !matCfg.isNull()) {
                engine.surface().setMaterial(h, matCfg);
            }

            if (uvCfg != null && !uvCfg.isNull()) {
                Spatial live = registry.get(h.id());
                if (live != null) uv.apply(live, uvCfg);
            }

            if (attach) {
                registry.attachToRoot(h.id());
            }

            return h;
        }, null);
    }

    // ---------------------------------------------------------------------
    // Ops (JME thread)
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public void lod(SurfaceApi.SurfaceHandle handle, Value cfg) {
        requireHandle(handle, "terrain.lod");
        requireCfg(cfg, "terrain.lod(cfg)");

        onJmeSyncVoid("terrain.lod", () -> ops.lod(requireTerrain(handle), cfg));
    }

    @HostAccess.Export
    public void scale(SurfaceApi.SurfaceHandle handle, double xzScale, Value cfg) {
        requireHandle(handle, "terrain.scale");
        onJmeSyncVoid("terrain.scale", () -> ops.scale(requireTerrain(handle), xzScale, cfg));
    }

    // ---------------------------------------------------------------------
    // TerrainQuad editing/query (JME thread)
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public float[] heightmap(SurfaceApi.SurfaceHandle handle) {
        requireHandle(handle, "terrain.heightmap");
        return onJmeSync("terrain.heightmap", () -> editOps.heightmapCopy(requireTerrain(handle)), new float[0]);
    }

    @HostAccess.Export
    public void setHeight(SurfaceApi.SurfaceHandle handle, double x, double z, double height, boolean world) {
        requireHandle(handle, "terrain.setHeight");
        onJmeSyncVoid("terrain.setHeight", () -> editOps.setHeight(requireTerrain(handle), x, z, height, world));
    }

    @HostAccess.Export
    public void adjustHeight(SurfaceApi.SurfaceHandle handle, double x, double z, double delta, boolean world) {
        requireHandle(handle, "terrain.adjustHeight");
        onJmeSyncVoid("terrain.adjustHeight", () -> editOps.adjustHeight(requireTerrain(handle), x, z, delta, world));
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
        requireHandle(handle, "terrain.rebuild");
        onJmeSyncVoid("terrain.rebuild", () -> editOps.rebuild(requireTerrain(handle)));
    }

    @HostAccess.Export
    public void setHeightmap(SurfaceApi.SurfaceHandle handle, Value cfg) {
        requireHandle(handle, "terrain.setHeightmap");
        requireCfg(cfg, "terrain.setHeightmap(handle,cfg)");

        Value hv = member(cfg, "heights");
        if (hv == null || hv.isNull())
            throw new IllegalArgumentException("terrain.setHeightmap: cfg.heights is required");

        float[] heights = readFloatArray(hv);
        int size = i32(cfg, "size", 0);
        if (size > 0) {
            int need = size * size;
            if (heights.length != need) {
                throw new IllegalArgumentException(
                        "terrain.setHeightmap: heights length=" + heights.length + " expected=" + need + " (size=" + size + ")"
                );
            }
        }

        final boolean doRebuild = bool(cfg, "rebuild", true);

        onJmeSyncVoid("terrain.setHeightmap", () -> editOps.setHeightmap(requireTerrain(handle), heights, doRebuild));
    }

    @HostAccess.Export
    public double heightAt(SurfaceApi.SurfaceHandle handle, double x, double z) {
        return heightAt(handle, x, z, true);
    }

    @HostAccess.Export
    public double heightAt(SurfaceApi.SurfaceHandle handle, double x, double z, boolean world) {
        requireHandle(handle, "terrain.heightAt");
        return onJmeSync("terrain.heightAt", () -> heightAtSafe(requireTerrain(handle), x, z, world), Double.NaN);
    }

    @HostAccess.Export
    public ProxyObject normalAt(SurfaceApi.SurfaceHandle handle, double x, double z) {
        return normalAt(handle, x, z, true);
    }

    @HostAccess.Export
    public ProxyObject normalAt(SurfaceApi.SurfaceHandle handle, double x, double z, boolean world) {
        requireHandle(handle, "terrain.normalAt");
        Vector3f n = onJmeSync("terrain.normalAt", () -> normalAtSafe(requireTerrain(handle), x, z, world), new Vector3f(0f, 1f, 0f));
        return ProxyObject.fromMap(Map.of(
                "x", (double) n.x,
                "y", (double) n.y,
                "z", (double) n.z
        ));
    }

    // ---------------------------------------------------------------------
    // Procedural helpers (caller thread)
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public float[] perlinHeights(Value cfg) {
        requireCfg(cfg, "terrain.perlinHeights(cfg)");
        return noise.perlinHeights(cfg);
    }

    @HostAccess.Export
    public float[] ridgedHeights(Value cfg) {
        requireCfg(cfg, "terrain.ridgedHeights(cfg)");
        return noise.ridgedHeights(cfg);
    }

    // ---------------------------------------------------------------------
    // Material / UV (JME thread)
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public void material(SurfaceApi.SurfaceHandle handle, Object materialHandleOrCfg) {
        requireHandle(handle, "terrain.material");
        engine.surface().setMaterial(handle, materialHandleOrCfg);
    }

    @HostAccess.Export
    public void uv(SurfaceApi.SurfaceHandle handle, Value cfgOrUv) {
        requireHandle(handle, "terrain.uv");
        requireCfg(cfgOrUv, "terrain.uv(cfg)");

        onJmeSyncVoid("terrain.uv", () -> uv.apply(requireSurface(handle), cfgOrUv));
    }

    // ---------------------------------------------------------------------
    // Physics bridge
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public Object physics(SurfaceApi.SurfaceHandle surface, Value cfg) {
        requireHandle(surface, "terrain.physics");
        requireCfg(cfg, "terrain.physics(cfg)");
        return physics.bind(surface, cfg);
    }

    // ---------------------------------------------------------------------
    // Entity binding (UUID-only, delegated)
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void attachEntity(SurfaceApi.SurfaceHandle handle, Object entityUuid) {
        requireHandle(handle, "terrain.attachEntity");
        engine.surface().attachEntity(handle, entityUuid);
        emitter.emit("engine.terrain.attached", "surfaceId", handle.id(), "uuid", String.valueOf(entityUuid));
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

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private Spatial requireSurface(SurfaceApi.SurfaceHandle handle) {
        Spatial s = registry.get(handle.id());
        if (s == null) throw new IllegalArgumentException("terrain: unknown surface id=" + handle.id());
        return s;
    }

    private TerrainQuad requireTerrain(SurfaceApi.SurfaceHandle handle) {
        Spatial s = requireSurface(handle);
        if (s instanceof TerrainQuad tq) return tq;

        String type = s.getClass().getSimpleName();
        throw new IllegalArgumentException("terrain: surface id=" + handle.id() + " is not TerrainQuad (type=" + type + ")");
    }
}
