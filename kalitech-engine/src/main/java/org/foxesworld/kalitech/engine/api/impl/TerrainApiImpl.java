// FILE: org/foxesworld/kalitech/engine/api/impl/TerrainApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.TerrainApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Terrain/Plane/Quad API.
 *
 * IMPORTANT DESIGN:
 *  - Terrain-specific ops (LOD, heightAt, normalAt, scale) require TerrainQuad.
 *  - Physics binding MUST be universal for ANY surface id (TerrainQuad or Geometry),
 *    because scripts frequently create flat ground as plane/quad.
 *
 * Universal physics implementation delegates to PhysicsApiImpl.body(...), which:
 *  - reuses existing body per surface (no duplicates)
 *  - chooses a default shape if collider not provided
 */
public final class TerrainApiImpl implements TerrainApi {

    private final EngineApiImpl engine;
    private final AssetManager assets;
    private final SurfaceRegistry registry;

    public TerrainApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.assets = Objects.requireNonNull(engine, "engine").getAssets();
        this.registry = engine.getSurfaceRegistry();
    }

    // ---------------------------------------------------------------------
    // CREATION
    // ---------------------------------------------------------------------

    /**
     * Heightmap terrain from image.
     * cfg:
     *  - heightmap: string (asset path) REQUIRED
     *  - patchSize: int (17..257) default 65
     *  - size: int (33..8193) default 513
     *  - heightScale: number default 2.0
     *  - xzScale: number default 2.0
     *  - shadows: bool default true
     *  - material: material-handle or material-cfg
     *  - attach: bool default true
     *  - lod: {enabled:true}
     *  - transform fields: pos/rot/scale (SurfaceApiImpl.applyTransform)
     */
    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle terrain(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrain.terrain(cfg): cfg is null");

        String heightmap = str(cfg, "heightmap", null);
        if (heightmap == null || heightmap.isBlank()) {
            throw new IllegalArgumentException("terrain.terrain: heightmap is required");
        }

        int patchSize = clampInt(num(cfg, "patchSize", 65), 17, 257);
        int size = clampInt(num(cfg, "size", 513), 33, 8193);
        float heightScale = (float) num(cfg, "heightScale", 2.0);
        float xzScale = (float) num(cfg, "xzScale", 2.0);

        Texture tex = assets.loadTexture(heightmap);
        AbstractHeightMap hm = new ImageBasedHeightMap(tex.getImage(), heightScale);
        hm.load();

        TerrainQuad tq = new TerrainQuad(str(cfg, "name", "terrain"), patchSize, size, hm.getHeightMap());
        tq.setLocalScale(xzScale, 1f, xzScale);

        boolean shadows = bool(cfg, "shadows", true);
        tq.setShadowMode(shadows ? RenderQueue.ShadowMode.CastAndReceive : RenderQueue.ShadowMode.Receive);

        Material defMat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        defMat.setColor("Color", new ColorRGBA(0.25f, 0.7f, 0.3f, 1f));
        tq.setMaterial(defMat);

        SurfaceApiImpl.applyTransform(tq, cfg);

        SurfaceApi.SurfaceHandle h = registry.register(tq, "terrain", engine.surface());

        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(h, mh);

        boolean attach = bool(cfg, "attach", true);
        if (attach) registry.attachToRoot(h.id());

        Value uv = member(cfg, "uv");
        if (uv != null && !uv.isNull()) uv(h, uv);

        Value lod = member(cfg, "lod");
        if (lod != null && !lod.isNull()) {
            boolean enable = bool(lod, "enabled", true);
            if (enable) enableLod(h);
        }

        return h;
    }

    /**
     * Procedural/Custom terrain from float heights array.
     * cfg:
     *  - heights: array length == size*size REQUIRED
     *  - size: int REQUIRED
     *  - patchSize: int default 65
     *  - xzScale: number default 2.0
     *  - yScale: number default 1.0
     *  - name, shadows, material, attach, lod, transform...
     */
    @HostAccess.Export
    public SurfaceApi.SurfaceHandle terrainHeights(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrain.terrainHeights(cfg): cfg is null");

        Value heightsV = member(cfg, "heights");
        if (heightsV == null || heightsV.isNull() || !heightsV.hasArrayElements()) {
            throw new IllegalArgumentException("terrain.terrainHeights: cfg.heights array is required");
        }

        int size = clampInt(num(cfg, "size", 0), 33, 8193);
        if (size <= 0) throw new IllegalArgumentException("terrain.terrainHeights: cfg.size is required");

        long n = heightsV.getArraySize();
        long expected = (long) size * (long) size;
        if (n != expected) {
            throw new IllegalArgumentException("terrain.terrainHeights: heights length must be size*size (" + expected + "), got " + n);
        }

        int patchSize = clampInt(num(cfg, "patchSize", 65), 17, 257);
        float xzScale = (float) num(cfg, "xzScale", 2.0);
        float yScale = (float) num(cfg, "yScale", 1.0);

        float[] heights = new float[(int) expected];
        for (int i = 0; i < expected; i++) {
            Value el = heightsV.getArrayElement(i);
            heights[i] = (el != null && !el.isNull() && el.isNumber()) ? (float) el.asDouble() : 0f;
        }

        TerrainQuad tq = new TerrainQuad(str(cfg, "name", "terrain"), patchSize, size, heights);
        tq.setLocalScale(xzScale, yScale, xzScale);

        boolean shadows = bool(cfg, "shadows", true);
        tq.setShadowMode(shadows ? RenderQueue.ShadowMode.CastAndReceive : RenderQueue.ShadowMode.Receive);

        Material defMat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        defMat.setColor("Color", new ColorRGBA(0.25f, 0.7f, 0.3f, 1f));
        tq.setMaterial(defMat);

        SurfaceApiImpl.applyTransform(tq, cfg);

        SurfaceApi.SurfaceHandle h = registry.register(tq, "terrain", engine.surface());

        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(h, mh);

        boolean attach = bool(cfg, "attach", true);
        if (attach) registry.attachToRoot(h.id());

        Value lod = member(cfg, "lod");
        if (lod != null && !lod.isNull()) {
            boolean enable = bool(lod, "enabled", true);
            if (enable) enableLod(h);
        }

        Value uv = member(cfg, "uv");
        if (uv != null && !uv.isNull()) uv(h, uv);

        return h;
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle quad(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrain.quad(cfg): cfg is null");

        String name = str(cfg, "name", "quad");
        float w = (float) clamp(num(cfg, "w", 1.0), 0.0001, 1_000_000);
        float h = (float) clamp(num(cfg, "h", 1.0), 0.0001, 1_000_000);

        Geometry g = new Geometry(name, new Quad(w, h));
        g.setShadowMode(RenderQueue.ShadowMode.Receive);

        Material defMat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        defMat.setColor("Color", ColorRGBA.White);
        g.setMaterial(defMat);

        SurfaceApiImpl.applyTransform(g, cfg);

        SurfaceApi.SurfaceHandle handle = registry.register(g, "quad", engine.surface());

        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(handle, mh);

        Value uv = member(cfg, "uv");
        if (uv != null && !uv.isNull()) uv(handle, uv);

        boolean attach = bool(cfg, "attach", true);
        if (attach) registry.attachToRoot(handle.id());

        return handle;
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle plane(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrain.plane(cfg): cfg is null");

        String name = str(cfg, "name", "plane");
        float w = (float) clamp(num(cfg, "w", 1.0), 0.0001, 1_000_000);
        float h = (float) clamp(num(cfg, "h", 1.0), 0.0001, 1_000_000);

        Geometry g = new Geometry(name, new Quad(w, h));
        g.setShadowMode(RenderQueue.ShadowMode.Receive);

        g.setLocalRotation(new Quaternion().fromAngles(-(float) (Math.PI * 0.5), 0f, 0f));

        Material defMat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        defMat.setColor("Color", ColorRGBA.White);
        g.setMaterial(defMat);

        SurfaceApiImpl.applyTransform(g, cfg);

        SurfaceApi.SurfaceHandle handle = registry.register(g, "plane", engine.surface());

        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(handle, mh);

        Value uv = member(cfg, "uv");
        if (uv != null && !uv.isNull()) uv(handle, uv);

        boolean attach = bool(cfg, "attach", true);
        if (attach) registry.attachToRoot(handle.id());

        return handle;
    }

    // ---------------------------------------------------------------------
    // TERRAIN OPS (material, lod, height queries, scale)
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public void material(SurfaceApi.SurfaceHandle handle, Object materialHandleOrCfg) {
        if (handle == null) throw new IllegalArgumentException("terrain.material: handle is required");
        engine.surface().setMaterial(handle, materialHandleOrCfg);
    }

    @HostAccess.Export
    public void lod(SurfaceApi.SurfaceHandle handle, Value cfg) {
        if (handle == null) throw new IllegalArgumentException("terrain.lod: handle is required");
        boolean enable = (cfg == null || cfg.isNull()) ? true : bool(cfg, "enabled", true);
        if (!enable) {
            disableLod(handle);
            return;
        }
        enableLod(handle);
    }

    private void enableLod(SurfaceApi.SurfaceHandle handle) {
        TerrainQuad tq = requireTerrain(handle);
        Camera cam = engine.getApp().getCamera();

        TerrainLodControl existing = tq.getControl(TerrainLodControl.class);
        if (existing != null) {
            existing.setCamera(cam);
            return;
        }

        TerrainLodControl lod = new TerrainLodControl(tq, cam);
        tq.addControl(lod);
    }

    private void disableLod(SurfaceApi.SurfaceHandle handle) {
        TerrainQuad tq = requireTerrain(handle);
        TerrainLodControl lod = tq.getControl(TerrainLodControl.class);
        if (lod != null) tq.removeControl(lod);
    }

    @HostAccess.Export
    public void scale(SurfaceApi.SurfaceHandle handle, double xzScale, Value cfg) {
        TerrainQuad tq = requireTerrain(handle);

        float xz = (float) clamp(xzScale, 0.0001, 1_000_000);
        float y = tq.getLocalScale().y;

        if (cfg != null && !cfg.isNull()) {
            if (cfg.hasMember("yScale")) y = (float) clamp(num(cfg, "yScale", y), 0.0001, 1_000_000);
        }

        tq.setLocalScale(xz, y, xz);
    }

    @HostAccess.Export
    public double heightAt(SurfaceApi.SurfaceHandle handle, double x, double z, boolean world) {
        TerrainQuad tq = requireTerrain(handle);

        float lx, lz;
        if (world) {
            Vector3f local = tq.worldToLocal(new Vector3f((float) x, 0f, (float) z), null);
            lx = local.x;
            lz = local.z;
        } else {
            lx = (float) x;
            lz = (float) z;
        }

        Float h = tq.getHeight(new Vector2f(lx, lz));
        if (h == null) return Double.NaN;

        if (!world) return h;

        Vector3f wp = tq.localToWorld(new Vector3f(0f, h, 0f), null);
        return wp.y;
    }

    @HostAccess.Export
    public double heightAt(SurfaceApi.SurfaceHandle handle, double x, double z) {
        return heightAt(handle, x, z, true);
    }

    @HostAccess.Export
    public Map<String, Double> normalAt(SurfaceApi.SurfaceHandle handle, double x, double z, boolean world) {
        TerrainQuad tq = requireTerrain(handle);

        float lx, lz;
        if (world) {
            Vector3f local = tq.worldToLocal(new Vector3f((float) x, 0f, (float) z), null);
            lx = local.x;
            lz = local.z;
        } else {
            lx = (float) x;
            lz = (float) z;
        }

        Vector3f n = tq.getNormal(new Vector2f(lx, lz));
        if (n == null) return Map.of("x", Double.NaN, "y", Double.NaN, "z", Double.NaN);

        if (world) n = tq.getWorldRotation().mult(n);

        return Map.of("x", (double) n.x, "y", (double) n.y, "z", (double) n.z);
    }

    @HostAccess.Export
    public Map<String, Double> normalAt(SurfaceApi.SurfaceHandle handle, double x, double z) {
        return normalAt(handle, x, z, true);
    }

    // ---------------------------------------------------------------------
    // PHYSICS (UNIVERSAL)
    // ---------------------------------------------------------------------

    /**
     * Universal physics bind for ANY surface created by TerrainApi:
     *  - TerrainQuad (terrain/terrainHeights)
     *  - Geometry (plane/quad)
     *
     * Defaults:
     *  - mass: 0 (static)
     *  - kinematic: true
     *  - collider: {type:"mesh"} (valid for static/kinematic)
     *
     * You can override by passing cfg fields (including collider).
     *
     * Notes:
     *  - Internally delegates to engine.physics().body(map)
     *  - PhysicsApiImpl.body() deduplicates by surfaceId (returns existing body)
     */
    @HostAccess.Export
    public Object physics(SurfaceApi.SurfaceHandle surface, Value cfg) {
        if (surface == null) throw new IllegalArgumentException("terrain.physics: surface handle is required");

        // validate surface exists early with clear error
        requireSurface(surface);

        HashMap<String, Object> m = new HashMap<>();
        m.put("surface", surface.id());
        m.put("mass", 0.0);
        m.put("kinematic", true);

        HashMap<String, Object> col = new HashMap<>();
        col.put("type", "mesh");
        m.put("collider", col);

        if (cfg != null && !cfg.isNull() && cfg.hasMembers()) {
            for (String k : cfg.getMemberKeys()) {
                m.put(k, cfg.getMember(k));
            }
        }

        return engine.physics().body(m);
    }

    @HostAccess.Export
    public void uv(SurfaceApi.SurfaceHandle handle, Value cfg) {
        if (handle == null) throw new IllegalArgumentException("terrain.uv: handle is required");
        if (cfg == null || cfg.isNull()) return;

        Spatial s = requireSurface(handle);

        // cfg.uv может быть объектом {scale:[sx,sy]} или напрямую {scale:...}
        Value uv = cfg;
        if (cfg.hasMember("uv")) uv = cfg.getMember("uv");
        if (uv == null || uv.isNull()) return;

        Vector2f scale = readUvScale(uv, 1f, 1f);
        if (scale == null) return;

        // --- 1) GEOMETRY: реально скейлим texCoord на меше
        if (s instanceof Geometry g) {
            if (g.getMesh() != null) {
                g.getMesh().scaleTextureCoordinates(scale);
            }
            return;
        }

        // --- 2) TERRAINQUAD: обычно делается через материал параметры (тайлинг), не через mesh
        if (s instanceof TerrainQuad tq) {
            Material m = tq.getMaterial();
            if (m != null) {
                // Пытаемся выставить наиболее типичные параметры (без падения)
                trySetFloat(m, "TexScale", scale.x);          // custom/unified
                trySetVector2(m, "UvScale", scale);           // custom/unified
                trySetVector2(m, "uvScale", scale);           // custom/unified

                // jME TerrainLighting.j3md часто использует Tex1Scale/Tex2Scale/... (float)
                trySetFloat(m, "Tex1Scale", scale.x);
                trySetFloat(m, "Tex2Scale", scale.x);
                trySetFloat(m, "Tex3Scale", scale.x);
                trySetFloat(m, "Tex4Scale", scale.x);
            }

            // На всякий — сохраняем в userData, чтобы твой MaterialApi мог подхватить
            tq.setUserData("uvScale", scale);
            return;
        }

        // Если появятся другие типы — просто сохраняем userData
        s.setUserData("uvScale", scale);
    }

    private static Vector2f readUvScale(Value uv, float defX, float defY) {
        try {
            // uv: [sx,sy]
            if (uv.hasArrayElements()) {
                float sx = (float) (uv.getArraySize() > 0 ? uv.getArrayElement(0).asDouble() : defX);
                float sy = (float) (uv.getArraySize() > 1 ? uv.getArrayElement(1).asDouble() : defY);
                return new Vector2f(sx, sy);
            }
            // uv: {scale:[sx,sy]} или {scale:s}
            if (uv.hasMember("scale")) {
                Value sc = uv.getMember("scale");
                if (sc != null && !sc.isNull()) {
                    if (sc.hasArrayElements()) {
                        float sx = (float) (sc.getArraySize() > 0 ? sc.getArrayElement(0).asDouble() : defX);
                        float sy = (float) (sc.getArraySize() > 1 ? sc.getArrayElement(1).asDouble() : defY);
                        return new Vector2f(sx, sy);
                    }
                    if (sc.isNumber()) {
                        float s = (float) sc.asDouble();
                        return new Vector2f(s, s);
                    }
                }
            }
            // uv: {sx,sy}
            if (uv.hasMember("sx") || uv.hasMember("sy")) {
                float sx = (float) (uv.hasMember("sx") ? uv.getMember("sx").asDouble() : defX);
                float sy = (float) (uv.hasMember("sy") ? uv.getMember("sy").asDouble() : defY);
                return new Vector2f(sx, sy);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void trySetFloat(Material m, String name, float v) {
        try { m.setFloat(name, v); } catch (Throwable ignored) {}
    }
    private static void trySetVector2(Material m, String name, Vector2f v) {
        try { m.setVector2(name, v); } catch (Throwable ignored) {}
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
        if (handle == null) throw new IllegalArgumentException("terrain: handle is required");
        Spatial s = registry.get(handle.id());
        if (s == null) throw new IllegalArgumentException("terrain: unknown surface id=" + handle.id());
        return s;
    }

    private TerrainQuad requireTerrain(SurfaceApi.SurfaceHandle handle) {
        Spatial s = requireSurface(handle);
        if (!(s instanceof TerrainQuad tq)) {
            throw new IllegalArgumentException("terrain: surface id=" + handle.id() + " is not TerrainQuad (type=" + s.getClass().getSimpleName() + ")");
        }
        return tq;
    }

    // ---- helpers ----
    private static Value member(Value v, String k) {
        return (v != null && v.hasMember(k)) ? v.getMember(k) : null;
    }

    private static String str(Value v, String k, String def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asString();
        } catch (Throwable t) {
            return def;
        }
    }

    private static boolean bool(Value v, String k, boolean def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asBoolean();
        } catch (Throwable t) {
            return def;
        }
    }

    private static double num(Value v, String k, double def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asDouble();
        } catch (Throwable t) {
            return def;
        }
    }

    private static int clampInt(double v, int a, int b) {
        int x = (int) Math.round(v);
        return Math.max(a, Math.min(b, x));
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }
}