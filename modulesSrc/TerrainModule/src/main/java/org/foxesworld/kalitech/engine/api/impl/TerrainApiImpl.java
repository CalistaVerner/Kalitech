/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector2f
 *  com.jme3.math.Vector3f
 *  com.jme3.scene.Geometry
 *  com.jme3.scene.Spatial
 *  com.jme3.terrain.geomipmap.TerrainQuad
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi$SurfaceHandle
 *  org.foxesworld.kalitech.engine.api.module.AbstractApiModule
 *  org.foxesworld.kalitech.engine.api.module.ApiContext
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 *  org.foxesworld.kalitech.engine.script.lua.LuaObject
 */
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import java.util.Map;
import java.util.Objects;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.TerrainApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.terrain.TerrainEditOps;
import org.foxesworld.kalitech.engine.modules.terrain.TerrainEmitter;
import org.foxesworld.kalitech.engine.modules.terrain.TerrainFactory;
import org.foxesworld.kalitech.engine.modules.terrain.TerrainNoise;
import org.foxesworld.kalitech.engine.modules.terrain.TerrainOps;
import org.foxesworld.kalitech.engine.modules.terrain.TerrainPhysics;
import org.foxesworld.kalitech.engine.modules.terrain.TerrainUV;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

public final class TerrainApiImpl
extends AbstractApiModule
implements TerrainApi {
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

    private static void requireCfg(LuaValueRef cfg, String where) {
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException(where + ": cfg is null");
        }
    }

    private static double heightAtSafe(TerrainQuad tq, double x, double z, boolean world) {
        Vector3f local = world ? tq.worldToLocal(new Vector3f((float)x, 0.0f, (float)z), null) : new Vector3f((float)x, 0.0f, (float)z);
        float hLocal = tq.getHeight(new Vector2f(local.x, local.z));
        if (!Float.isFinite(hLocal)) {
            return Double.NaN;
        }
        if (!world) {
            return hLocal;
        }
        Vector3f wp = tq.localToWorld(new Vector3f(local.x, hLocal, local.z), null);
        return wp.y;
    }

    private static void requireHandle(SurfaceApi.SurfaceHandle handle, String where) {
        if (handle == null) {
            throw new IllegalArgumentException(where + ": handle is required");
        }
    }

    private static Vector3f normalAtSafe(TerrainQuad tq, double x, double z, boolean world) {
        Vector3f s = tq.getWorldScale();
        float eps = 0.25f;
        if (s != null) {
            float sx = Math.max(0.001f, s.x);
            float sz = Math.max(0.001f, s.z);
            eps = Math.max(0.05f, Math.min(1.0f, 0.25f * Math.max(sx, sz)));
        }
        double hR = TerrainApiImpl.heightAtSafe(tq, x + (double)eps, z, world);
        double hL = TerrainApiImpl.heightAtSafe(tq, x - (double)eps, z, world);
        double hU = TerrainApiImpl.heightAtSafe(tq, x, z + (double)eps, world);
        double hD = TerrainApiImpl.heightAtSafe(tq, x, z - (double)eps, world);
        if (!(Double.isFinite(hR) && Double.isFinite(hL) && Double.isFinite(hU) && Double.isFinite(hD))) {
            return new Vector3f(0.0f, 1.0f, 0.0f);
        }
        float dx = (float)(hR - hL);
        float dz = (float)(hU - hD);
        Vector3f n = new Vector3f(-dx, 2.0f * eps, -dz);
        n.normalizeLocal();
        if (!world) {
            return n;
        }
        Quaternion wr = tq.getWorldRotation();
        wr.mult(n, n);
        n.normalizeLocal();
        return n;
    }

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

    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.registry = Objects.requireNonNull(this.engine.getSurfaceRegistry(), "engine.surfaceRegistry");
        this.emitter = new TerrainEmitter(this.engine);
        this.factory = new TerrainFactory(this.engine.getAssets());
        this.uv = new TerrainUV();
        this.ops = new TerrainOps(this.engine.getApp().getCamera());
        this.editOps = new TerrainEditOps();
        this.physics = new TerrainPhysics(this.engine);
        this.noise = new TerrainNoise();
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public SurfaceApi.SurfaceHandle terrain(LuaValueRef cfg) {
        TerrainApiImpl.requireCfg(cfg, "terrain.terrain(cfg)");
        TerrainQuad tq = this.factory.createTerrainFromHeightmap(cfg);
        return this.registerTerrainLike(tq, "terrain", cfg);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public SurfaceApi.SurfaceHandle terrainHeights(LuaValueRef cfg) {
        TerrainApiImpl.requireCfg(cfg, "terrain.terrainHeights(cfg)");
        TerrainQuad tq = this.factory.createTerrainFromHeights(cfg);
        return this.registerTerrainLike(tq, "terrainHeights", cfg);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public SurfaceApi.SurfaceHandle quad(LuaValueRef cfg) {
        TerrainApiImpl.requireCfg(cfg, "terrain.quad(cfg)");
        Geometry g = this.factory.createQuad(cfg);
        return this.registerSpatialLike((Spatial)g, "quad", cfg);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public SurfaceApi.SurfaceHandle plane(LuaValueRef cfg) {
        TerrainApiImpl.requireCfg(cfg, "terrain.plane(cfg)");
        Geometry g = this.factory.createPlane(cfg);
        return this.registerSpatialLike((Spatial)g, "plane", cfg);
    }

    private SurfaceApi.SurfaceHandle registerTerrainLike(TerrainQuad tq, String type, LuaValueRef cfg) {
        SurfaceApi.SurfaceHandle h = this.registerSpatialLike((Spatial)tq, type, cfg);
        LuaValueRef lod = LuaCfg.member((LuaValueRef)cfg, (String)"lod");
        if (lod != null && !lod.isNull() && LuaCfg.bool((LuaValueRef)lod, (String)"enabled", (boolean)true)) {
            this.lod(h, lod);
        }
        this.emitter.emit("engine.terrain.created", "surfaceId", h.id(), "type", type);
        return h;
    }

    private SurfaceApi.SurfaceHandle registerSpatialLike(Spatial s, String type, LuaValueRef cfg) {
        TerrainApiImpl.requireCfg(cfg, "terrain.registerSpatialLike(cfg)");
        LuaValueRef matCfg = LuaCfg.member((LuaValueRef)cfg, (String)"material");
        LuaValueRef uvCfg = LuaCfg.member((LuaValueRef)cfg, (String)"uv");
        boolean attach = LuaCfg.bool((LuaValueRef)cfg, (String)"attach", (boolean)true);
        return (SurfaceApi.SurfaceHandle)this.onJmeSync("terrain.registerSpatialLike", () -> {
            Spatial live;
            SurfaceApi.SurfaceHandle h = this.registry.register(s, type, this.engine.surface());
            if (matCfg != null && !matCfg.isNull()) {
                this.engine.surface().setMaterial(h, (Object)matCfg);
            }
            if (uvCfg != null && !uvCfg.isNull() && (live = this.registry.get(h.id())) != null) {
                this.uv.apply(live, uvCfg);
            }
            if (attach) {
                this.registry.attachToRoot(h.id());
            }
            return h;
        }, null);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void lod(SurfaceApi.SurfaceHandle handle, LuaValueRef cfg) {
        TerrainApiImpl.requireHandle(handle, "terrain.lod");
        TerrainApiImpl.requireCfg(cfg, "terrain.lod(cfg)");
        this.onJmeSyncVoid("terrain.lod", () -> this.ops.lod(this.requireTerrain(handle), cfg));
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void scale(SurfaceApi.SurfaceHandle handle, double xzScale, LuaValueRef cfg) {
        TerrainApiImpl.requireHandle(handle, "terrain.scale");
        this.onJmeSyncVoid("terrain.scale", () -> this.ops.scale(this.requireTerrain(handle), xzScale, cfg));
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public float[] heightmap(SurfaceApi.SurfaceHandle handle) {
        TerrainApiImpl.requireHandle(handle, "terrain.heightmap");
        return (float[])this.onJmeSync("terrain.heightmap", () -> this.editOps.heightmapCopy(this.requireTerrain(handle)), new float[0]);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setHeight(SurfaceApi.SurfaceHandle handle, double x, double z, double height, boolean world) {
        TerrainApiImpl.requireHandle(handle, "terrain.setHeight");
        this.onJmeSyncVoid("terrain.setHeight", () -> this.editOps.setHeight(this.requireTerrain(handle), x, z, height, world));
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void adjustHeight(SurfaceApi.SurfaceHandle handle, double x, double z, double delta, boolean world) {
        TerrainApiImpl.requireHandle(handle, "terrain.adjustHeight");
        this.onJmeSyncVoid("terrain.adjustHeight", () -> this.editOps.adjustHeight(this.requireTerrain(handle), x, z, delta, world));
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setHeight(SurfaceApi.SurfaceHandle handle, double x, double z, double height) {
        this.setHeight(handle, x, z, height, true);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void adjustHeight(SurfaceApi.SurfaceHandle handle, double x, double z, double delta) {
        this.adjustHeight(handle, x, z, delta, true);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void rebuild(SurfaceApi.SurfaceHandle handle) {
        TerrainApiImpl.requireHandle(handle, "terrain.rebuild");
        this.onJmeSyncVoid("terrain.rebuild", () -> this.editOps.rebuild(this.requireTerrain(handle)));
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setHeightmap(SurfaceApi.SurfaceHandle handle, LuaValueRef cfg) {
        int need;
        TerrainApiImpl.requireHandle(handle, "terrain.setHeightmap");
        TerrainApiImpl.requireCfg(cfg, "terrain.setHeightmap(handle,cfg)");
        LuaValueRef hv = LuaCfg.member((LuaValueRef)cfg, (String)"heights");
        if (hv == null || hv.isNull()) {
            throw new IllegalArgumentException("terrain.setHeightmap: cfg.heights is required");
        }
        float[] heights = LuaCfg.readFloatArray((LuaValueRef)hv);
        int size = LuaCfg.i32((LuaValueRef)cfg, (String)"size", (int)0);
        if (size > 0 && heights.length != (need = size * size)) {
            throw new IllegalArgumentException("terrain.setHeightmap: heights length=" + heights.length + " expected=" + need + " (size=" + size + ")");
        }
        boolean doRebuild = LuaCfg.bool((LuaValueRef)cfg, (String)"rebuild", (boolean)true);
        this.onJmeSyncVoid("terrain.setHeightmap", () -> this.editOps.setHeightmap(this.requireTerrain(handle), heights, doRebuild));
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public double heightAt(SurfaceApi.SurfaceHandle handle, double x, double z) {
        return this.heightAt(handle, x, z, true);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public double heightAt(SurfaceApi.SurfaceHandle handle, double x, double z, boolean world) {
        TerrainApiImpl.requireHandle(handle, "terrain.heightAt");
        return (Double)this.onJmeSync("terrain.heightAt", () -> TerrainApiImpl.heightAtSafe(this.requireTerrain(handle), x, z, world), Double.NaN);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public LuaObject normalAt(SurfaceApi.SurfaceHandle handle, double x, double z) {
        return this.normalAt(handle, x, z, true);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public LuaObject normalAt(SurfaceApi.SurfaceHandle handle, double x, double z, boolean world) {
        TerrainApiImpl.requireHandle(handle, "terrain.normalAt");
        Vector3f n = (Vector3f)this.onJmeSync("terrain.normalAt", () -> TerrainApiImpl.normalAtSafe(this.requireTerrain(handle), x, z, world), new Vector3f(0.0f, 1.0f, 0.0f));
        return LuaObject.fromMap(Map.of("x", Double.valueOf(n.x), "y", Double.valueOf(n.y), "z", Double.valueOf(n.z)));
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public float[] perlinHeights(LuaValueRef cfg) {
        TerrainApiImpl.requireCfg(cfg, "terrain.perlinHeights(cfg)");
        return this.noise.perlinHeights(cfg);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public float[] ridgedHeights(LuaValueRef cfg) {
        TerrainApiImpl.requireCfg(cfg, "terrain.ridgedHeights(cfg)");
        return this.noise.ridgedHeights(cfg);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void material(SurfaceApi.SurfaceHandle handle, Object materialHandleOrCfg) {
        TerrainApiImpl.requireHandle(handle, "terrain.material");
        this.engine.surface().setMaterial(handle, materialHandleOrCfg);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void uv(SurfaceApi.SurfaceHandle handle, LuaValueRef cfgOrUv) {
        TerrainApiImpl.requireHandle(handle, "terrain.uv");
        TerrainApiImpl.requireCfg(cfgOrUv, "terrain.uv(cfg)");
        this.onJmeSyncVoid("terrain.uv", () -> this.uv.apply(this.requireSurface(handle), cfgOrUv));
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public Object physics(SurfaceApi.SurfaceHandle surface, LuaValueRef cfg) {
        TerrainApiImpl.requireHandle(surface, "terrain.physics");
        TerrainApiImpl.requireCfg(cfg, "terrain.physics(cfg)");
        return this.physics.bind(surface, cfg);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void attachEntity(SurfaceApi.SurfaceHandle handle, Object entityUuid) {
        TerrainApiImpl.requireHandle(handle, "terrain.attachEntity");
        this.engine.surface().attachEntity(handle, entityUuid);
        this.emitter.emit("engine.terrain.attached", "surfaceId", handle.id(), "uuid", String.valueOf(entityUuid));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void detachEntity(SurfaceApi.SurfaceHandle handle) {
        TerrainApiImpl.requireHandle(handle, "terrain.detachEntity");
        this.engine.surface().detachFromEntity(handle);
        this.emitter.emit("engine.terrain.detached", "surfaceId", handle.id());
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void detach(SurfaceApi.SurfaceHandle handle) {
        TerrainApiImpl.requireHandle(handle, "terrain.detach");
        this.engine.surface().detachFromEntity(handle);
        this.emitter.emit("engine.terrain.detached", "surfaceId", handle.id());
    }

    private Spatial requireSurface(SurfaceApi.SurfaceHandle handle) {
        Spatial s = this.registry.get(handle.id());
        if (s == null) {
            throw new IllegalArgumentException("terrain: unknown surface id=" + handle.id());
        }
        return s;
    }

    private TerrainQuad requireTerrain(SurfaceApi.SurfaceHandle handle) {
        Spatial s = this.requireSurface(handle);
        if (s instanceof TerrainQuad) {
            TerrainQuad tq = (TerrainQuad)s;
            return tq;
        }
        String type = s.getClass().getSimpleName();
        throw new IllegalArgumentException("terrain: surface id=" + handle.id() + " is not TerrainQuad (type=" + type + ")");
    }
}

