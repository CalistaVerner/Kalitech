package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetManager;
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
import com.jme3.scene.*;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.MaterialApi;
import org.foxesworld.kalitech.engine.api.interfaces.MeshApi;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.modules.material.MaterialUtils;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.member;

public final class SurfaceApiImpl implements SurfaceApi {

    private static final Logger log = LogManager.getLogger(SurfaceApiImpl.class);

    /**
     * API contract: JS calls are synchronous.
     * If the call happens from a worker, we must hop to the JME thread and wait.
     */
    private static final long DEFAULT_TIMEOUT_MS = 2_000;

    private final EngineApiImpl engine;
    private final SurfaceRegistry registry;
    private static final String UD_UV_SCALE = "__kt_uvScale";
    private final AssetManager assets;
    @SuppressWarnings("unused")
    private final MeshApi meshApi;
    @SuppressWarnings("unused")
    private final PhysicsApi physicsApi;
    @SuppressWarnings("unused")
    private final MaterialApi materialApi;
    private final org.foxesworld.kalitech.engine.script.events.ScriptEventBus bus;

    public SurfaceApiImpl(EngineApiImpl engine, SurfaceRegistry registry) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.assets = engine.getAssets();
        this.physicsApi = engine.physics();
        this.meshApi = engine.mesh();
        this.materialApi = engine.material();
        this.bus = engine.getBus();
        // ❌ LEGACY REMOVED: registry.bindSurfaceApi(this);
    }

    // ------------------------------------------------------------
    // Threading helpers
    // ------------------------------------------------------------

    private boolean isJmeThread() {
        try {
            return engine.isJmeThread();
        } catch (Throwable ignored) {
            // If EngineApiImpl doesn't provide isJmeThread for some reason,
            // we still behave safely by always enqueueing.
            return false;
        }
    }

    private void onJmeSyncVoid(String where, Runnable r) {
        if (isJmeThread()) {
            r.run();
            return;
        }
        try {
            Future<?> f = engine.getApp().enqueue(() -> {
                r.run();
                return null;
            });
            f.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            log.warn("[surface] {}: JME hop failed/timeout", where, t);
        }
    }

    private <T> T onJmeSync(String where, Callable<T> c, T fallback) {
        if (isJmeThread()) {
            try {
                return c.call();
            } catch (Throwable t) {
                log.warn("[surface] {}: failed", where, t);
                return fallback;
            }
        }
        try {
            Future<T> f = engine.getApp().enqueue(c);
            return f.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            log.warn("[surface] {}: JME hop failed/timeout", where, t);
            return fallback;
        }
    }

    // ------------------------------------------------------------
    // Events
    // ------------------------------------------------------------

    private void emit(String topic, Object... kv) {
        if (bus == null) return;
        try {
            HashMap<String, Object> m = new HashMap<>();
            for (int i = 0; i + 1 < kv.length; i += 2) {
                Object k = kv[i];
                if (k == null) continue;
                m.put(String.valueOf(k), kv[i + 1]);
            }
            bus.emit(topic, m);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------
    // Handle coercion (fixes legacy JS passing raw ids)
    // ------------------------------------------------------------

    private int idOf(Object handleOrId) {
        if (handleOrId == null) return 0;

        if (handleOrId instanceof SurfaceHandle h) return h.id();
        if (handleOrId instanceof Number n) return n.intValue();

        if (handleOrId instanceof Value v) {
            try {
                if (v.isNull()) return 0;
                if (v.isNumber()) return (int) v.asDouble();

                // host object wrapper
                if (v.isHostObject()) {
                    Object host = v.asHostObject();
                    if (host instanceof SurfaceHandle h) return h.id();
                    if (host instanceof Number n) return n.intValue();
                }

                // {id:...}
                if (v.hasMembers() && v.hasMember("id")) {
                    Value id = v.getMember("id");
                    if (id != null && !id.isNull() && id.isNumber()) return (int) id.asDouble();
                }

                // valueOf() pattern
                if (v.canInvokeMember("valueOf")) {
                    Value vo = v.invokeMember("valueOf");
                    if (vo != null && !vo.isNull() && vo.isNumber()) return (int) vo.asDouble();
                }
            } catch (Throwable ignored) {
            }
        }

        // plain JS object exposed as Map-like host object
        try {
            var f = handleOrId.getClass().getField("id");
            Object id = f.get(handleOrId);
            if (id instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {
        }

        return 0;
    }

    private SurfaceHandle handleOf(Object handleOrId) {
        if (handleOrId instanceof SurfaceHandle h) return h;
        int id = idOf(handleOrId);
        if (id <= 0) return null;
        if (!registry.exists(id)) return null;
        return new SurfaceHandle(id, registry.kind(id));
    }

    private Spatial requireSpatial(Object handleOrId) {
        SurfaceHandle h = handleOf(handleOrId);
        if (h == null) throw new IllegalArgumentException("surface: invalid handle/id: " + String.valueOf(handleOrId));
        Spatial s = registry.get(h.id());
        if (s == null) throw new IllegalStateException("surface: missing spatial for id=" + h.id());
        return s;
    }

    private SurfaceHandle requireHandle(Object handleOrId) {
        SurfaceHandle h = handleOf(handleOrId);
        if (h == null) throw new IllegalArgumentException("surface: invalid handle/id: " + String.valueOf(handleOrId));
        if (!registry.exists(h.id())) throw new IllegalStateException("surface: unknown handle id=" + h.id());
        return h;
    }

    // ------------------------------------------------------------
    // SurfaceApi exports
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public SurfaceHandle handle(int id) {
        if (!registry.exists(id)) throw new IllegalArgumentException("surface.handle: unknown id=" + id);
        return new SurfaceHandle(id, registry.kind(id));
    }

    // --- Overloads to accept raw ids from JS (LEGACY compatibility) ---

    @HostAccess.Export
    public void setMaterial(Object target, Object materialHandleOrCfg) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("setMaterial", () -> setMaterial(h, materialHandleOrCfg));
    }

    @HostAccess.Export
    public void applyMaterialToChildren(Object target, Object materialHandle) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("applyMaterialToChildren", () -> applyMaterialToChildren(h, materialHandle));
    }

    @HostAccess.Export
    public void setTransform(Object target, Value cfg) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("setTransform", () -> setTransform(h, cfg));
    }

    @HostAccess.Export
    public void setPos(Object target, Object pos) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("setPos", () -> setPos(h, pos));
    }

    @HostAccess.Export
    public void setRot(Object target, Object rotDeg) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("setRot", () -> setRot(h, rotDeg));
    }

    @HostAccess.Export
    public void setCull(Object target, String hint) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("setCull", () -> setCull(h, hint));
    }

    @HostAccess.Export
    public void setVisible(Object target, boolean visible) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("setVisible", () -> setVisible(h, visible));
    }

    @HostAccess.Export
    public void setScale(Object target, Object scale) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("setScale", () -> setScale(h, scale));
    }

    @HostAccess.Export
    public void setName(Object target, String name) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("setName", () -> setName(h, name));
    }

    @HostAccess.Export
    public void setShadowMode(Object target, String mode) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("setShadowMode", () -> setShadowMode(h, mode));
    }

    @HostAccess.Export
    public void attachToRoot(Object target) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("attachToRoot", () -> attachToRoot(h));
    }

    @HostAccess.Export
    public void detach(Object target) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("detach", () -> detach(h));
    }

    @HostAccess.Export
    public void destroy(Object target) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("destroy", () -> destroy(h));
    }

    @HostAccess.Export
    public boolean exists(Object target) {
        SurfaceHandle h = handleOf(target);
        return h != null && registry.exists(h.id());
    }

    @HostAccess.Export
    public int attachedEntity(Object target) {
        SurfaceHandle h = requireHandle(target);
        return attachedEntity(h);
    }

    @HostAccess.Export
    public void attach(Object target, int entityId) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("attach", () -> attach(h, entityId));
    }

    @HostAccess.Export
    public void detachFromEntity(Object target) {
        SurfaceHandle h = requireHandle(target);
        onJmeSyncVoid("detachFromEntity", () -> detachFromEntity(h));
    }

    @HostAccess.Export
    public WorldBounds getWorldBounds(Object target) {
        SurfaceHandle h = requireHandle(target);
        return onJmeSync("getWorldBounds", () -> getWorldBounds(h), new WorldBounds("none", 0, 0, 0, 0, 0, 0, 0));
    }

    @HostAccess.Export
    public Hit[] raycast(Object target, Value cfg) {
        SurfaceHandle h = requireHandle(target);
        return onJmeSync("raycast", () -> raycast(h, cfg), new Hit[0]);
    }

    @HostAccess.Export
    public Hit[] pickUnderCursor(Object target) {
        SurfaceHandle h = requireHandle(target);
        return onJmeSync("pickUnderCursor", () -> pickUnderCursor(h), new Hit[0]);
    }

    @HostAccess.Export
    public Hit[] pickUnderCursorCfg(Object target, Value cfg) {
        SurfaceHandle h = requireHandle(target);
        return onJmeSync("pickUnderCursorCfg", () -> pickUnderCursorCfg(h, cfg), new Hit[0]);
    }

    // ------------------------------------------------------------
    // Interface methods (kept, but now always JME-thread safe)
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void setMaterial(SurfaceHandle target, Object materialHandleOrCfg) {
        Objects.requireNonNull(target, "target");
        onJmeSyncVoid("setMaterial", () -> {
            Spatial s = requireSpatial(target);

            Material mat = unwrapMaterial(materialHandleOrCfg);
            Value cfg = null;

            if (mat == null && materialHandleOrCfg instanceof Value v && v.hasMembers() && v.hasMember("def")) {
                cfg = v;
                MaterialApiImpl.MaterialHandle mh = engine.material().create(v);
                mat = mh.__material();
            }

            if (mat == null) throw new IllegalArgumentException("surface.setMaterial: materialHandle is invalid");

            if (s instanceof TerrainQuad tq) {
                tq.setMaterial(mat);
                emit("engine.surface.material.set", "surfaceId", target.id(), "kind", registry.kind(target.id()), "type", "terrain");
                return;
            }

            if (s instanceof Geometry g) {
                g.setMaterial(mat);
                if (cfg != null) {
                    try {
                        applyTileWorldToGeometryIfAny(g, cfg);
                    } catch (Throwable ignored) {
                    }
                }
                emit("engine.surface.material.set", "surfaceId", target.id(), "kind", registry.kind(target.id()), "type", "geometry");
                return;
            }

            if (s instanceof Node n) {
                applyMaterialRecursiveWithTileWorld(n, mat, cfg);
                emit("engine.surface.material.set", "surfaceId", target.id(), "kind", registry.kind(target.id()), "type", "node");
                return;
            }

            throw new IllegalStateException("surface.setMaterial: unsupported Spatial type=" + s.getClass().getName());
        });
    }

    private static void applyMaterialRecursiveWithTileWorld(Spatial s, Material mat, Value cfgOrNull) {
        if (s instanceof Geometry g) {
            g.setMaterial(mat);
            if (cfgOrNull != null) {
                try {
                    applyTileWorldToGeometryIfAny(g, cfgOrNull);
                } catch (Throwable ignored) {
                }
            }
            return;
        }
        if (s instanceof TerrainQuad tq) {
            tq.setMaterial(mat);
            return;
        }
        if (s instanceof Node n) {
            for (Spatial child : n.getChildren()) applyMaterialRecursiveWithTileWorld(child, mat, cfgOrNull);
        }
    }

    private static void applyTileWorldToGeometryIfAny(Geometry g, Value materialCfg) {
        if (g == null || materialCfg == null || materialCfg.isNull()) return;

        Value params = member(materialCfg, "params");
        if (params == null || params.isNull() || !params.hasMembers()) return;

        MaterialUtils.TextureDesc td = null;

        td = tryTex(params, "BaseColorMap");
        if (td == null) td = tryTex(params, "ColorMap");

        if (td == null) {
            for (String k : params.getMemberKeys()) {
                td = MaterialUtils.parseTextureDesc(params.getMember(k));
                if (td != null && td.tileWorld() != null) break;
                td = null;
            }
        }

        if (td == null || td.tileWorld() == null) return;

        BoundingVolume bv = g.getWorldBound();
        if (!(bv instanceof BoundingBox bb)) return;

        float worldX = bb.getXExtent() * 2f;
        float worldZ = bb.getZExtent() * 2f;

        if (worldZ < 1e-4f) worldZ = bb.getYExtent() * 2f;
        if (worldX < 1e-4f || worldZ < 1e-4f) return;

        float tileX = td.tileWorld().x();
        float tileZ = td.tileWorld().z();
        if (tileX <= 0f || tileZ <= 0f) return;

        float u = worldX / tileX;
        float v = worldZ / tileZ;

        applyUvScaleNonAccumulating(g, u, v);
    }

    private static MaterialUtils.TextureDesc tryTex(Value params, String name) {
        if (params == null || params.isNull() || !params.hasMember(name)) return null;
        MaterialUtils.TextureDesc td = MaterialUtils.parseTextureDesc(params.getMember(name));
        return (td != null && td.tileWorld() != null) ? td : null;
    }

    @SuppressWarnings("unchecked")
    private static void applyUvScaleNonAccumulating(Geometry g, float u, float v) {
        if (u <= 0f || v <= 0f) return;

        Mesh mesh = g.getMesh();
        if (mesh == null) return;

        // IMPORTANT: meshes are often shared. UV scaling mutates mesh buffers, so clone per-geometry once.
        Boolean cloned = g.getUserData("__kt_meshCloned");
        if (cloned == null || !cloned) {
            try {
                Mesh clonedMesh = mesh.clone();
                g.setMesh(clonedMesh);
                mesh = clonedMesh;
                g.setUserData("__kt_meshCloned", Boolean.TRUE);
            } catch (Throwable ignored) {
                // fallback: mutate shared mesh (legacy behavior)
            }
        }

        VertexBuffer vb = mesh.getBuffer(VertexBuffer.Type.TexCoord);
        if (vb == null) return;

        Vector2f prev = g.getUserData(UD_UV_SCALE);
        if (prev == null) prev = new Vector2f(1f, 1f);

        float ru = u / prev.x;
        float rv = v / prev.y;

        if (Math.abs(ru - 1f) < 1e-6f && Math.abs(rv - 1f) < 1e-6f) return;

        mesh.scaleTextureCoordinates(new Vector2f(ru, rv));
        g.setUserData(UD_UV_SCALE, new Vector2f(u, v));
    }

    @HostAccess.Export
    @Override
    public void applyMaterialToChildren(SurfaceHandle target, Object materialHandle) {
        Objects.requireNonNull(target, "target");
        onJmeSyncVoid("applyMaterialToChildren", () -> {
            Spatial s = requireSpatial(target);

            Material mat = unwrapMaterial(materialHandle);
            if (mat == null) throw new IllegalArgumentException("surface.applyMaterialToChildren: materialHandle is invalid");

            applyMaterialRecursive(s, mat);
        });
    }

    @HostAccess.Export
    @Override
    public void setTransform(SurfaceHandle target, Value cfg) {
        Objects.requireNonNull(target, "target");
        onJmeSyncVoid("setTransform", () -> {
            Spatial s = requireSpatial(target);
            applyTransform(s, cfg);
        });
    }

    @HostAccess.Export
    public void setPos(SurfaceHandle target, Object pos) {
        Objects.requireNonNull(target, "target");
        onJmeSyncVoid("setPos", () -> {
            Spatial s = requireSpatial(target);
            Vector3f p = vec3Any(pos, 0f, 0f, 0f);
            s.setLocalTranslation(p);
        });
    }

    @HostAccess.Export
    public void setRot(SurfaceHandle target, Object rotDeg) {
        Objects.requireNonNull(target, "target");
        onJmeSyncVoid("setRot", () -> {
            Spatial s = requireSpatial(target);
            Vector3f deg = vec3Any(rotDeg, 0f, 0f, 0f);
            float rx = deg.x * (float) (Math.PI / 180.0);
            float ry = deg.y * (float) (Math.PI / 180.0);
            float rz = deg.z * (float) (Math.PI / 180.0);
            s.setLocalRotation(new Quaternion().fromAngles(rx, ry, rz));
        });
    }

    @HostAccess.Export
    @Override
    public void setCull(SurfaceHandle target, String hint) {
        Objects.requireNonNull(target, "target");
        onJmeSyncVoid("setCull", () -> {
            Spatial s = requireSpatial(target);
            s.setCullHint(parseCullHint(hint));
        });
    }

    @HostAccess.Export
    @Override
    public void setVisible(SurfaceHandle target, boolean visible) {
        Objects.requireNonNull(target, "target");
        onJmeSyncVoid("setVisible", () -> {
            Spatial s = requireSpatial(target);
            s.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        });
    }

    @Override
    public Hit[] pickUnderCursorCfg(Value cfg) {
        return new Hit[0];
    }

    @HostAccess.Export
    public void setScale(SurfaceHandle target, Object scale) {
        Objects.requireNonNull(target, "target");
        onJmeSyncVoid("setScale", () -> {
            Spatial s = requireSpatial(target);

            if (scale instanceof Number n) {
                s.setLocalScale(n.floatValue());
                return;
            }
            if (scale instanceof Value v && !v.isNull() && v.isNumber()) {
                s.setLocalScale((float) v.asDouble());
                return;
            }

            Vector3f sc = vec3Any(scale, 1f, 1f, 1f);
            s.setLocalScale(sc);
        });
    }

    @HostAccess.Export
    public void setName(SurfaceHandle target, String name) {
        Objects.requireNonNull(target, "target");
        onJmeSyncVoid("setName", () -> {
            Spatial s = requireSpatial(target);
            if (name == null) return;
            s.setName(name);
        });
    }

    @HostAccess.Export
    @Override
    public void setShadowMode(SurfaceHandle target, String mode) {
        Objects.requireNonNull(target, "target");
        onJmeSyncVoid("setShadowMode", () -> {
            Spatial s = requireSpatial(target);
            s.setShadowMode(parseShadowMode(mode));
        });
    }

    @HostAccess.Export
    @Override
    public void attachToRoot(SurfaceHandle target) {
        requireHandle(target);
        // registry already enqueues attach flush; still keep sync hop for "sync" JS semantics.
        onJmeSyncVoid("attachToRoot", () -> registry.attachToRoot(target.id()));
    }

    @HostAccess.Export
    @Override
    public void detach(SurfaceHandle target) {
        requireHandle(target);
        onJmeSyncVoid("detach", () -> registry.detachFromParent(target.id()));
    }

    @HostAccess.Export
    @Override
    public void destroy(SurfaceHandle target) {
        requireHandle(target);
        onJmeSyncVoid("destroy", () -> registry.destroy(target.id()));
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
        Integer e = registry.attachedEntity(target.id());
        return (e == null) ? 0 : e;
    }

    @HostAccess.Export
    @Override
    public void attach(SurfaceHandle target, int entityId) {
        requireHandle(target);
        onJmeSyncVoid("attach", () -> {
            registry.attach(target.id(), entityId);
            engine.getEcs().components().putByName(entityId, "Surface", new SurfaceComponent(target.id(), target.kind()));
        });
    }

    @HostAccess.Export
    @Override
    public void detachFromEntity(SurfaceHandle target) {
        requireHandle(target);
        onJmeSyncVoid("detachFromEntity", () -> {
            Integer entityId = registry.attachedEntity(target.id());
            registry.detachSurface(target.id());
            if (entityId != null && entityId > 0) engine.getEcs().components().removeByName(entityId, "Surface");
        });
    }

    @HostAccess.Export
    @Override
    public WorldBounds getWorldBounds(SurfaceHandle target) {
        Objects.requireNonNull(target, "target");
        return onJmeSync("getWorldBounds", () -> {
            Spatial s = requireSpatial(target);
            BoundingVolume bv = s.getWorldBound();
            if (bv == null) return new WorldBounds("none", 0, 0, 0, 0, 0, 0, 0);

            if (bv instanceof BoundingBox bb) {
                Vector3f c = bb.getCenter();
                return new WorldBounds("box", c.x, c.y, c.z, bb.getXExtent(), bb.getYExtent(), bb.getZExtent(), 0f);
            }
            if (bv instanceof BoundingSphere bs) {
                Vector3f c = bs.getCenter();
                return new WorldBounds("sphere", c.x, c.y, c.z, 0, 0, 0, bs.getRadius());
            }

            Vector3f c = bv.getCenter();
            return new WorldBounds("other", c.x, c.y, c.z, 0, 0, 0, 0);
        }, new WorldBounds("none", 0, 0, 0, 0, 0, 0, 0));
    }

    @HostAccess.Export
    @Override
    public Hit[] raycast(SurfaceHandle target, Value cfg) {
        Objects.requireNonNull(target, "target");
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("surface.raycast: cfg is null");

        return onJmeSync("raycast", () -> {
            Spatial s = requireSpatial(target);

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
        }, new Hit[0]);
    }

    @HostAccess.Export
    @Override
    public Hit[] pickUnderCursor(SurfaceHandle target) {
        return pickUnderCursorCfg(target, null);
    }

    @HostAccess.Export
    @Override
    public Hit[] pickUnderCursorCfg(SurfaceHandle target, Value cfg) {
        Objects.requireNonNull(target, "target");

        return onJmeSync("pickUnderCursorCfg", () -> {
            Spatial s = requireSpatial(target);

            Camera cam = engine.getApp().getCamera();
            if (cam == null) return new Hit[0];

            // cfg: {x,y} in pixels, default: center
            float x = (cfg != null && !cfg.isNull()) ? (float) num(cfg, "x", cam.getWidth() * 0.5) : cam.getWidth() * 0.5f;
            float y = (cfg != null && !cfg.isNull()) ? (float) num(cfg, "y", cam.getHeight() * 0.5) : cam.getHeight() * 0.5f;

            Vector3f origin = cam.getWorldCoordinates(new Vector2f(x, y), 0f);
            Vector3f far = cam.getWorldCoordinates(new Vector2f(x, y), 1f);
            Vector3f dir = far.subtract(origin);
            if (dir.lengthSquared() < 1e-8f) return new Hit[0];
            dir.normalizeLocal();

            float max = (cfg != null && !cfg.isNull()) ? (float) num(cfg, "max", 10_000.0) : 10_000.0f;
            int limit = (cfg != null && !cfg.isNull()) ? clampInt(num(cfg, "limit", 16.0), 1, 256) : 16;
            boolean onlyClosest = (cfg != null && !cfg.isNull()) ? bool(cfg, "onlyClosest", true) : true;

            Ray ray = new Ray(origin, dir);
            ray.setLimit(max);

            return collide(s, ray, onlyClosest, limit);
        }, new Hit[0]);
    }

    @Override
    public Hit[] pickUnderCursor() {
        return new Hit[0];
    }

    // ------------------------------------------------------------
    // Collisions / picking
    // ------------------------------------------------------------

    private static Hit[] collide(Spatial root, Ray ray, boolean onlyClosest, int limit) {
        if (root == null) return new Hit[0];

        CollisionResults results = new CollisionResults();
        root.collideWith(ray, results);

        if (results.size() <= 0) return new Hit[0];

        if (onlyClosest) {
            CollisionResult cr = results.getClosestCollision();
            if (cr == null) return new Hit[0];

            Vector3f p = cr.getContactPoint();
            Vector3f n = cr.getContactNormal();

            return new Hit[]{
                    new Hit(
                            spatialName(cr.getGeometry()),
                            cr.getDistance(),
                            p.x, p.y, p.z,
                            n.x, n.y, n.z
                    )
            };
        }

        int nHits = Math.min(limit, results.size());
        Hit[] out = new Hit[nHits];

        for (int i = 0; i < nHits; i++) {
            CollisionResult cr = results.getCollision(i);
            Vector3f p = cr.getContactPoint();
            Vector3f n = cr.getContactNormal();

            out[i] = new Hit(
                    spatialName(cr.getGeometry()),
                    cr.getDistance(),
                    p.x, p.y, p.z,
                    n.x, n.y, n.z
            );
        }

        return out;
    }

    private static String spatialName(Spatial s) {
        if (s == null) return "";
        String n = s.getName();
        return (n == null) ? "" : n;
    }

    // ------------------------------------------------------------
    // Helpers / parsing
    // ------------------------------------------------------------

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

    private static void applyMaterialRecursive(Spatial root, Material mat) {
        if (root == null || mat == null) return;

        ArrayDeque<Spatial> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Spatial s = stack.pop();

            if (s instanceof Geometry g) {
                if (g.getMaterial() != mat) g.setMaterial(mat);
                continue;
            }
            if (s instanceof TerrainQuad tq) {
                if (tq.getMaterial() != mat) tq.setMaterial(mat);
                continue;
            }
            if (s instanceof Node n) {
                for (Spatial child : n.getChildren()) {
                    if (child != null) stack.push(child);
                }
            }
        }
    }

    private static Spatial.CullHint parseCullHint(String hint) {
        if (hint == null) return Spatial.CullHint.Inherit;
        String m = hint.trim().toLowerCase(Locale.ROOT);
        return switch (m) {
            case "inherit", "parent" -> Spatial.CullHint.Inherit;
            case "always", "hidden", "hide" -> Spatial.CullHint.Always;
            case "never", "show" -> Spatial.CullHint.Never;
            case "dynamic" -> Spatial.CullHint.Dynamic;
            default -> Spatial.CullHint.Inherit;
        };
    }

    private static RenderQueue.ShadowMode parseShadowMode(String mode) {
        if (mode == null) return RenderQueue.ShadowMode.Inherit;
        String m = mode.trim().toLowerCase(Locale.ROOT);
        return switch (m) {
            case "inherit" -> RenderQueue.ShadowMode.Inherit;
            case "off", "none", "disable" -> RenderQueue.ShadowMode.Off;
            case "cast" -> RenderQueue.ShadowMode.Cast;
            case "receive" -> RenderQueue.ShadowMode.Receive;
            case "castandreceive", "both" -> RenderQueue.ShadowMode.CastAndReceive;
            default -> RenderQueue.ShadowMode.Inherit;
        };
    }

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

        Value shadow = member(cfg, "shadow");
        if (shadow != null && !shadow.isNull()) s.setShadowMode(parseShadowMode(shadow.asString()));

        Value cull = member(cfg, "cull");
        if (cull != null && !cull.isNull()) s.setCullHint(parseCullHint(cull.asString()));

        Value bucket = member(cfg, "bucket");
        if (bucket != null && !bucket.isNull()) {
            String b = bucket.asString();
            if (b != null) {
                String m = b.trim().toLowerCase(Locale.ROOT);
                switch (m) {
                    case "sky" -> s.setQueueBucket(RenderQueue.Bucket.Sky);
                    case "gui" -> s.setQueueBucket(RenderQueue.Bucket.Gui);
                    case "opaque" -> s.setQueueBucket(RenderQueue.Bucket.Opaque);
                    case "transparent" -> s.setQueueBucket(RenderQueue.Bucket.Transparent);
                    case "translucent" -> s.setQueueBucket(RenderQueue.Bucket.Translucent);
                    default -> {
                    }
                }
            }
        }
    }

    private static Vector3f vec3(Value v, float dx, float dy, float dz) {
        if (v == null || v.isNull()) return new Vector3f(dx, dy, dz);
        try {
            if (v.hasMembers()) {
                float x = (float) num(v, "x", dx);
                float y = (float) num(v, "y", dy);
                float z = (float) num(v, "z", dz);
                return new Vector3f(x, y, z);
            }
            if (v.hasArrayElements()) {
                float x = (float) (v.getArraySize() > 0 ? v.getArrayElement(0).asDouble() : dx);
                float y = (float) (v.getArraySize() > 1 ? v.getArrayElement(1).asDouble() : dy);
                float z = (float) (v.getArraySize() > 2 ? v.getArrayElement(2).asDouble() : dz);
                return new Vector3f(x, y, z);
            }
        } catch (Throwable ignored) {
        }
        return new Vector3f(dx, dy, dz);
    }

    private static Vector3f vec3Any(Object v, float dx, float dy, float dz) {
        if (v == null) return new Vector3f(dx, dy, dz);

        if (v instanceof Vector3f vv) return vv;

        if (v instanceof Value gv) {
            return vec3(gv, dx, dy, dz);
        }

        if (v instanceof Map<?, ?> m) {
            try {
                Object x = m.get("x"), y = m.get("y"), z = m.get("z");
                float fx = (x instanceof Number n) ? n.floatValue() : dx;
                float fy = (y instanceof Number n) ? n.floatValue() : dy;
                float fz = (z instanceof Number n) ? n.floatValue() : dz;
                return new Vector3f(fx, fy, fz);
            } catch (Throwable ignored) {
            }
        }

        // Reflective {x,y,z}
        try {
            float x = (float) numReflect(v, "x", dx);
            float y = (float) numReflect(v, "y", dy);
            float z = (float) numReflect(v, "z", dz);
            return new Vector3f(x, y, z);
        } catch (Throwable ignored) {
        }

        return new Vector3f(dx, dy, dz);
    }

    private static double num(Value v, String k, double def) {
        try {
            Value m = member(v, k);
            if (m == null || m.isNull()) return def;
            if (m.isNumber()) return m.asDouble();
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static double numReflect(Object o, String field, double def) {
        try {
            var f = o.getClass().getField(field);
            Object v = f.get(o);
            if (v instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static boolean bool(Value v, String k, boolean def) {
        try {
            Value m = member(v, k);
            if (m == null || m.isNull()) return def;
            if (m.isBoolean()) return m.asBoolean();
            if (m.isNumber()) return m.asDouble() != 0.0;
            if (m.isString()) {
                String s = m.asString().trim().toLowerCase(Locale.ROOT);
                return ("true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s));
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static int clampInt(double v, int lo, int hi) {
        int x = (int) v;
        if (x < lo) return lo;
        if (x > hi) return hi;
        return x;
    }

    public static final class SurfaceComponent {
        @HostAccess.Export public final int surfaceId;
        @HostAccess.Export public final String kind;
        public SurfaceComponent(int surfaceId, String kind) {
            this.surfaceId = surfaceId;
            this.kind = kind;
        }
    }

    // --------------------------
    // Legacy helper kept for internal use
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
}