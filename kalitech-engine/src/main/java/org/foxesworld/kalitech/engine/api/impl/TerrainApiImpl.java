package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.TerrainApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;

public final class TerrainApiImpl implements TerrainApi {

    private final EngineApiImpl engine;
    private final AssetManager assets;
    private final SurfaceRegistry registry;

    public TerrainApiImpl(EngineApiImpl engine, SurfaceRegistry registry) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.assets = Objects.requireNonNull(engine, "engine").getAssets();
        this.registry = Objects.requireNonNull(registry, "registry");
    }

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

        TerrainQuad tq = new TerrainQuad("terrain", patchSize, size, hm.getHeightMap());
        tq.setLocalScale(xzScale, 1f, xzScale);

        boolean shadows = bool(cfg, "shadows", true);
        tq.setShadowMode(shadows ? RenderQueue.ShadowMode.CastAndReceive : RenderQueue.ShadowMode.Receive);

        // safe default material
        Material defMat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        defMat.setColor("Color", new ColorRGBA(0.25f, 0.7f, 0.3f, 1f));
        tq.setMaterial(defMat);

        // apply transforms
        SurfaceApiImpl.applyTransform(tq, cfg);

        SurfaceApi.SurfaceHandle h = registry.register(tq, "terrain");

        // optional material
        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) {
            engine.surface().setMaterial(h, mh);
        }

        boolean attach = bool(cfg, "attach", true);
        if (attach) registry.attachToRoot(h.id());

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

        SurfaceApi.SurfaceHandle handle = registry.register(g, "quad");

        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(handle, mh);

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

        // rotate XY -> XZ
        g.setLocalRotation(new Quaternion().fromAngles(-(float)(Math.PI * 0.5), 0f, 0f));

        Material defMat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        defMat.setColor("Color", ColorRGBA.White);
        g.setMaterial(defMat);

        SurfaceApiImpl.applyTransform(g, cfg);

        SurfaceApi.SurfaceHandle handle = registry.register(g, "plane");

        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(handle, mh);

        boolean attach = bool(cfg, "attach", true);
        if (attach) registry.attachToRoot(handle.id());

        return handle;
    }

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