package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.material.Material;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SurfaceApiImpl implements SurfaceApi {

    private final EngineApiImpl engine;
    private final SurfaceRegistry registry;

    public SurfaceApiImpl(EngineApiImpl engine, SurfaceRegistry registry) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @HostAccess.Export
    @Override
    public SurfaceHandle handle(int id) {
        if (!registry.exists(id)) throw new IllegalArgumentException("surface.handle: unknown id=" + id);
        return new SurfaceHandle(id, registry.kind(id));
    }

    @HostAccess.Export
    @Override
    public void setMaterial(SurfaceHandle target, Object materialHandle) {
        Spatial s = requireSpatial(target);

        Material mat = unwrapMaterial(materialHandle);
        if (mat == null) throw new IllegalArgumentException("surface.setMaterial: materialHandle is invalid");

        if (s instanceof TerrainQuad tq) {
            tq.setMaterial(mat);
        } else if (s instanceof Geometry g) {
            g.setMaterial(mat);
        } else {
            throw new IllegalStateException("surface.setMaterial: unsupported Spatial type=" + s.getClass().getName());
        }
    }

    @HostAccess.Export
    @Override
    public void applyMaterialToChildren(SurfaceHandle target, Object materialHandle) {
        Spatial s = requireSpatial(target);

        Material mat = unwrapMaterial(materialHandle);
        if (mat == null) throw new IllegalArgumentException("surface.applyMaterialToChildren: materialHandle is invalid");

        applyMaterialRecursive(s, mat);
    }

    @HostAccess.Export
    @Override
    public void setTransform(SurfaceHandle target, Value cfg) {
        Spatial s = requireSpatial(target);
        applyTransform(s, cfg);
    }

    @HostAccess.Export
    @Override
    public void setShadowMode(SurfaceHandle target, String mode) {
        Spatial s = requireSpatial(target);
        s.setShadowMode(parseShadowMode(mode));
    }

    @HostAccess.Export
    @Override
    public void attachToRoot(SurfaceHandle target) {
        requireHandle(target);
        registry.attachToRoot(target.id());
    }

    @HostAccess.Export
    @Override
    public void detach(SurfaceHandle target) {
        requireHandle(target);
        registry.detachFromParent(target.id());
    }

    @HostAccess.Export
    @Override
    public void destroy(SurfaceHandle target) {
        requireHandle(target);
        registry.destroy(target.id());
    }

    @HostAccess.Export
    @Override
    public boolean exists(SurfaceHandle target) {
        return target != null && registry.exists(target.id());
    }

    @HostAccess.Export
    @Override
    public int attachedEntity(SurfaceHandle target) {
        requireHandle(target);
        return registry.attachedEntity(target.id());
    }

    @HostAccess.Export
    @Override
    public void attach(SurfaceHandle target, int entityId) {
        requireHandle(target);
        registry.attach(target.id(), entityId);
        engine.getEcs().components().putByName(entityId, "Surface", new SurfaceComponent(target.id(), target.kind()));
    }

    @HostAccess.Export
    @Override
    public void detachFromEntity(SurfaceHandle target) {
        requireHandle(target);
        int entityId = registry.attachedEntity(target.id());
        registry.detachSurface(target.id());
        if (entityId > 0) engine.getEcs().components().removeByName(entityId, "Surface");
    }

    @HostAccess.Export
    @Override
    public WorldBounds getWorldBounds(SurfaceHandle target) {
        Spatial s = requireSpatial(target);
        BoundingVolume bv = s.getWorldBound();
        if (bv == null) return new WorldBounds("none", 0,0,0, 0,0,0, 0);

        if (bv instanceof BoundingBox bb) {
            Vector3f c = bb.getCenter();
            return new WorldBounds("box", c.x, c.y, c.z, bb.getXExtent(), bb.getYExtent(), bb.getZExtent(), 0f);
        }
        if (bv instanceof BoundingSphere bs) {
            Vector3f c = bs.getCenter();
            return new WorldBounds("sphere", c.x, c.y, c.z, 0,0,0, bs.getRadius());
        }

        Vector3f c = bv.getCenter();
        return new WorldBounds("other", c.x, c.y, c.z, 0,0,0, 0);
    }

    @HostAccess.Export
    @Override
    public Hit[] raycast(SurfaceHandle target, Value cfg) {
        Spatial s = requireSpatial(target);
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("surface.raycast: cfg is null");

        Vector3f origin = vec3(member(cfg, "origin"), 0f, 0f, 0f);
        Vector3f dir = vec3(member(cfg, "dir"), 0f, -1f, 0f);

        float max = (float) num(cfg, "max", 10_000.0);
        int limit = clampInt(num(cfg, "limit", 16.0), 1, 256);
        boolean onlyClosest = bool(cfg, "onlyClosest", true);

        if (dir.lengthSquared() < 1e-8f) dir.set(0, -1, 0);
        dir.normalizeLocal();

        Ray ray = new Ray(origin, dir);
        ray.setLimit(max);

        return collide(s, ray, onlyClosest, limit);
    }

    // ------------------------------
    // ✅ pickUnderCursor (camera ray)
    // ------------------------------

    @HostAccess.Export
    @Override
    public Hit[] pickUnderCursor(SurfaceHandle target) {
        // default config: onlyClosest=true, max=10_000
        return pickUnderCursorCfg(target, null);
    }

    @HostAccess.Export
    @Override
    public Hit[] pickUnderCursorCfg(SurfaceHandle target, Value cfg) {
        Spatial s = requireSpatial(target);

        Camera cam = engine.getApp().getCamera();
        if (cam == null) return new Hit[0];

        // screen coords: override or use InputApi mouse
        float sx;
        float sy;

        if (cfg != null && !cfg.isNull() && cfg.hasMember("screenX") && cfg.getMember("screenX").isNumber()) {
            sx = (float) cfg.getMember("screenX").asDouble();
        } else {
            sx = (float) engine.input().mouseX();
        }

        if (cfg != null && !cfg.isNull() && cfg.hasMember("screenY") && cfg.getMember("screenY").isNumber()) {
            sy = (float) cfg.getMember("screenY").asDouble();
        } else {
            sy = (float) engine.input().mouseY();
        }

        float max = (float) ((cfg != null && !cfg.isNull()) ? num(cfg, "max", 10_000.0) : 10_000.0);
        int limit = clampInt((cfg != null && !cfg.isNull()) ? num(cfg, "limit", 16.0) : 16.0, 1, 256);
        boolean onlyClosest = (cfg != null && !cfg.isNull()) ? bool(cfg, "onlyClosest", true) : true;

        // JME ray from camera through cursor
        Vector2f screen = new Vector2f(sx, sy);
        Vector3f origin = cam.getWorldCoordinates(screen, 0f);
        Vector3f far = cam.getWorldCoordinates(screen, 1f);
        Vector3f dir = far.subtract(origin).normalizeLocal();

        Ray ray = new Ray(origin, dir);
        ray.setLimit(max);

        return collide(s, ray, onlyClosest, limit);
    }

    private static Hit[] collide(Spatial s, Ray ray, boolean onlyClosest, int limit) {
        CollisionResults results = new CollisionResults();
        s.collideWith(ray, results);

        if (results.size() == 0) return new Hit[0];

        if (onlyClosest) {
            CollisionResult r = results.getClosestCollision();
            return new Hit[]{ toHit(r) };
        }

        List<CollisionResult> list = new ArrayList<>(results.size());
        for (CollisionResult cr : results) list.add(cr);
        list.sort(Comparator.comparingDouble(CollisionResult::getDistance));

        int n = Math.min(limit, list.size());
        Hit[] out = new Hit[n];
        for (int i = 0; i < n; i++) out[i] = toHit(list.get(i));
        return out;
    }

    private static Hit toHit(CollisionResult r) {
        String gname = (r.getGeometry() != null) ? r.getGeometry().getName() : "";
        Vector3f p = r.getContactPoint();
        Vector3f n = r.getContactNormal();
        return new Hit(gname, r.getDistance(), p.x, p.y, p.z, n.x, n.y, n.z);
    }

    // --------------------------
    // Component (host-safe)
    // --------------------------
    public static final class SurfaceComponent {
        @HostAccess.Export public final int surfaceId;
        @HostAccess.Export public final String kind;
        public SurfaceComponent(int surfaceId, String kind) { this.surfaceId = surfaceId; this.kind = kind; }
    }

    // --------------------------
    // Helpers
    // --------------------------
    private Spatial requireSpatial(SurfaceHandle h) {
        requireHandle(h);
        Spatial s = registry.get(h.id());
        if (s == null) throw new IllegalStateException("surface: missing spatial for id=" + h.id());
        return s;
    }

    private void requireHandle(SurfaceHandle h) {
        if (h == null) throw new IllegalArgumentException("surface: handle is null");
        if (!registry.exists(h.id())) throw new IllegalStateException("surface: unknown handle id=" + h.id());
    }

    private Material unwrapMaterial(Object materialHandle) {
        if (materialHandle == null) return null;

        if (materialHandle instanceof Value v) {
            if (v.isHostObject()) {
                Object host = v.asHostObject();
                if (host instanceof MaterialApiImpl.MaterialHandle mh) return mh.__material();
            }
            return null;
        }

        if (materialHandle instanceof MaterialApiImpl.MaterialHandle mh) {
            return mh.__material();
        }

        return null;
    }

    private static void applyMaterialRecursive(Spatial s, Material mat) {
        if (s instanceof Geometry g) { g.setMaterial(mat); return; }
        if (s instanceof TerrainQuad tq) { tq.setMaterial(mat); return; }
        if (s instanceof Node n) {
            for (Spatial child : n.getChildren()) applyMaterialRecursive(child, mat);
        }
    }

    // ---- transform helpers (same style as before) ----
    public static void applyTransform(Spatial s, Value cfg) {
        if (s == null || cfg == null || cfg.isNull()) return;

        Value pos = member(cfg, "pos");
        if (pos != null && !pos.isNull()) s.setLocalTranslation(vec3(pos, 0f, 0f, 0f));

        Value sc = member(cfg, "scale");
        if (sc != null && !sc.isNull()) {
            if (sc.isNumber()) s.setLocalScale((float) sc.asDouble());
            else s.setLocalScale(vec3(sc, 1f, 1f, 1f));
        }

        Value rot = member(cfg, "rot");
        if (rot != null && !rot.isNull()) {
            Vector3f deg = vec3(rot, 0f, 0f, 0f);
            float rx = deg.x * (float) (Math.PI / 180.0);
            float ry = deg.y * (float) (Math.PI / 180.0);
            float rz = deg.z * (float) (Math.PI / 180.0);
            s.setLocalRotation(new Quaternion().fromAngles(rx, ry, rz));
        }
    }

    private static RenderQueue.ShadowMode parseShadowMode(String mode) {
        if (mode == null) return RenderQueue.ShadowMode.Inherit;
        String m = mode.trim().toLowerCase();
        return switch (m) {
            case "off", "none" -> RenderQueue.ShadowMode.Off;
            case "receive" -> RenderQueue.ShadowMode.Receive;
            case "cast" -> RenderQueue.ShadowMode.Cast;
            case "castandreceive", "both" -> RenderQueue.ShadowMode.CastAndReceive;
            default -> RenderQueue.ShadowMode.Inherit;
        };
    }

    // ---- Value helpers ----
    private static Value member(Value v, String k) {
        return (v != null && v.hasMember(k)) ? v.getMember(k) : null;
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

    private static Vector3f vec3(Value v, float dx, float dy, float dz) {
        try {
            if (v == null || v.isNull()) return new Vector3f(dx, dy, dz);

            if (v.hasArrayElements()) {
                float x = (float) v.getArrayElement(0).asDouble();
                float y = (float) v.getArrayElement(1).asDouble();
                float z = (float) v.getArrayElement(2).asDouble();
                return new Vector3f(x, y, z);
            }

            float x = (float) num(v, "x", dx);
            float y = (float) num(v, "y", dy);
            float z = (float) num(v, "z", dz);
            return new Vector3f(x, y, z);
        } catch (Throwable t) {
            return new Vector3f(dx, dy, dz);
        }
    }
}
