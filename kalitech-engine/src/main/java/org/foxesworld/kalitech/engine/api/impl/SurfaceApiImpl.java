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
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.MaterialApi;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.module.EnginePhysicsModule;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.material.MaterialTypes;
import org.foxesworld.kalitech.engine.modules.material.MaterialUtils;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.*;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.member;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.num;

/**
 * Surface API.
 *
 * <p>Contract:
 * <ul>
 *   <li>All scene graph mutations are executed on the JME thread via {@code onJme*} helpers.</li>
 *   <li>Public entity binding is UUID-only (string).</li>
 *   <li>No legacy constructors or ad-hoc binding paths.</li>
 * </ul>
 */
public final class SurfaceApiImpl extends AbstractApiModule implements SurfaceApi {

    private static final String UD_UV_SCALE = "__kt_uvScale";
    private static final String UD_MESH_CLONED = "__kt_meshCloned";

    private SurfaceRegistry registry;
    private AssetManager assets;

    @SuppressWarnings("unused")
    private PhysicsApi physicsApi;
    @SuppressWarnings("unused")
    private MaterialApi materialApi;

    public SurfaceApiImpl() {
        super("surface", "Surface", "3.0.0");
    }

    private static int clampInt(double v, int lo, int hi) {
        int x = (int) v;
        if (x < lo) return lo;
        if (x > hi) return hi;
        return x;
    }


    // ---------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------

    private static boolean bool(Value v, String k, boolean def) {
        Value m = member(v, k);
        if (m == null || m.isNull()) return def;
        if (m.isBoolean()) return m.asBoolean();
        if (m.isNumber()) return m.asDouble() != 0.0;
        if (m.isString()) {
            String s = m.asString().trim().toLowerCase(Locale.ROOT);
            return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s);
        }
        return def;
    }

    private static Vector3f vec3(Value v, float dx, float dy, float dz) {
        if (v == null || v.isNull()) return new Vector3f(dx, dy, dz);

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

        return new Vector3f(dx, dy, dz);
    }

    private static Vector3f vec3Any(Object v, float dx, float dy, float dz) {
        if (v == null) return new Vector3f(dx, dy, dz);
        if (v instanceof Vector3f vv) return vv;
        if (v instanceof Value gv) return vec3(gv, dx, dy, dz);

        if (v instanceof Map<?, ?> m) {
            Object x = m.get("x"), y = m.get("y"), z = m.get("z");
            float fx = (x instanceof Number n) ? n.floatValue() : dx;
            float fy = (y instanceof Number n) ? n.floatValue() : dy;
            float fz = (z instanceof Number n) ? n.floatValue() : dz;
            return new Vector3f(fx, fy, fz);
        }

        return new Vector3f(dx, dy, dz);
    }

    private static String spatialName(Spatial s) {
        if (s == null) return "";
        String n = s.getName();
        return (n == null) ? "" : n;
    }

    private static TileWorld extractTileWorld(Value materialCfg) {
        if (materialCfg == null || materialCfg.isNull()) return null;

        Value params = member(materialCfg, "params");
        if (params == null || params.isNull() || !params.hasMembers()) return null;

        MaterialTypes.TextureDesc td = tryTex(params, "BaseColorMap");
        if (td == null) td = tryTex(params, "ColorMap");

        if (td == null) {
            for (String k : params.getMemberKeys()) {
                MaterialTypes.TextureDesc t = MaterialUtils.parseTextureDesc(params.getMember(k));
                if (t != null && t.tileWorld() != null) {
                    td = t;
                    break;
                }
            }
        }

        if (td == null || td.tileWorld() == null) return null;

        float tx = td.tileWorld().x();
        float tz = td.tileWorld().z();
        if (!(tx > 0f) || !(tz > 0f)) return null;

        return new TileWorld(tx, tz);
    }

    private static String requireUuid(Object ref) {
        if (ref == null) throw new IllegalArgumentException("surface: uuid is required");

        if (ref instanceof String s) {
            String x = s.trim();
            if (x.isEmpty()) throw new IllegalArgumentException("surface: uuid is blank");
            return x;
        }

        if (ref instanceof Value v) {
            if (v.isNull()) throw new IllegalArgumentException("surface: uuid is null");
            if (v.isString()) {
                String x = v.asString().trim();
                if (x.isEmpty()) throw new IllegalArgumentException("surface: uuid is blank");
                return x;
            }
        }

        throw new IllegalArgumentException("surface: entity must be uuid(string)");
    }

    private static void applyUvScaleNonAccumulating(Geometry g, float u, float v) {
        if (!(u > 0f) || !(v > 0f)) return;

        Mesh mesh = g.getMesh();
        if (mesh == null) return;

        Boolean cloned = g.getUserData(UD_MESH_CLONED);
        if (cloned == null || !cloned) {
            Mesh clonedMesh = mesh.clone();
            g.setMesh(clonedMesh);
            mesh = clonedMesh;
            g.setUserData(UD_MESH_CLONED, Boolean.TRUE);
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

    private static void applyTileWorldToGeometryIfAny(Geometry g, TileWorld tw) {
        if (g == null || tw == null) return;

        BoundingVolume bv = g.getWorldBound();
        if (!(bv instanceof BoundingBox bb)) return;

        float worldX = bb.getXExtent() * 2f;
        float worldZ = bb.getZExtent() * 2f;

        if (worldZ < 1e-4f) worldZ = bb.getYExtent() * 2f;
        if (!(worldX > 1e-4f) || !(worldZ > 1e-4f)) return;

        float u = worldX / tw.tileX;
        float v = worldZ / tw.tileZ;

        applyUvScaleNonAccumulating(g, u, v);
    }

    private static void applyTileWorldRecursive(Spatial root, TileWorld tw) {
        if (root == null || tw == null) return;

        ArrayDeque<Spatial> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Spatial s = stack.pop();
            if (s instanceof Geometry g) {
                applyTileWorldToGeometryIfAny(g, tw);
                continue;
            }
            if (s instanceof Node n) {
                for (Spatial child : n.getChildren()) if (child != null) stack.push(child);
            }
        }
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
                for (Spatial child : n.getChildren()) if (child != null) stack.push(child);
            }
        }
    }

    private static MaterialTypes.TextureDesc tryTex(Value params, String name) {
        if (params == null || params.isNull() || !params.hasMember(name)) return null;
        MaterialTypes.TextureDesc td = MaterialUtils.parseTextureDesc(params.getMember(name));
        return (td != null && td.tileWorld() != null) ? td : null;
    }

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

    // ---------------------------------------------------------------------
    // Material application
    // ---------------------------------------------------------------------

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

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);

        this.registry = Objects.requireNonNull(engine.getSurfaceRegistry(), "engine.surfaceRegistry");
        this.registry.attach(ctx);

        this.assets = ctx.assets;
        this.physicsApi = engine.physics();
        this.materialApi = engine.material();
    }

    @Override
    public void detach() {
        this.registry = null;
        this.assets = null;
        this.physicsApi = null;
        this.materialApi = null;
        super.detach();
    }

    private ScriptEventBus bus() {
        return engine != null ? engine.getBus() : null;
    }

    private void emit(String topic, Object... kv) {
        ScriptEventBus b = bus();
        if (b == null) return;

        HashMap<String, Object> m = new HashMap<>();
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                Object k = kv[i];
                if (k == null) continue;
                m.put(String.valueOf(k), kv[i + 1]);
            }
        }

        try {
            b.emit(topic, m);
        } catch (RuntimeException ignored) {
        }
    }

    private void requireHandle(SurfaceHandle h) {
        if (h == null) throw new IllegalArgumentException("surface: handle is null");
        SurfaceRegistry r = registry;
        if (r == null) throw new IllegalStateException("surface: registry is null");
        if (!r.exists(h.id())) throw new IllegalStateException("surface: unknown handle id=" + h.id());
    }

    // ---------------------------------------------------------------------
    // Exports: entity binding
    // ---------------------------------------------------------------------

    private Spatial requireSpatial(SurfaceHandle h) {
        requireHandle(h);
        Spatial s = registry.get(h.id());
        if (s == null) throw new IllegalStateException("surface: missing spatial for id=" + h.id());
        return s;
    }

    private void requireExistingEntity(String uuid) {
        if (!engine.getEcs().exists(uuid)) {
            throw new IllegalArgumentException("surface: unknown entity uuid=" + uuid);
        }
    }

    private Material unwrapMaterial(Object materialHandle) {
        if (materialHandle == null) return null;

        if (materialHandle instanceof Value v) {
            if (v.isHostObject()) {
                Object host = v.asHostObject();
                if (host instanceof MaterialApiImpl.MaterialHandle mh) return mh.__material();
            }
            if (v.isNumber() && v.fitsInInt()) {
                return resolveMaterialById(v.asInt());
            }
            return null;
        }

        if (materialHandle instanceof MaterialApiImpl.MaterialHandle mh) {
            return mh.__material();
        }

        if (materialHandle instanceof Number n) {
            return resolveMaterialById(n.intValue());
        }

        return null;
    }

    private Material resolveMaterialById(int id) {
        try {
            if (engine.material() instanceof MaterialApiImpl impl) {
                MaterialApiImpl.MaterialHandle h = impl.getById(id);
                return h != null ? h.__material() : null;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Exports: scene graph ops
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public String attachedEntityUuid(SurfaceHandle target) {
        requireHandle(target);
        return registry.attachedEntityUuid(target.id());
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void attachEntity(SurfaceHandle target, Object entityUuid) {
        requireHandle(target);
        final String uuid = requireUuid(entityUuid);
        requireExistingEntity(uuid);

        onJmeSyncVoid("surface.attachEntity", () -> {
            registry.attach(target.id(), uuid);
            engine.getEcs().putComponentByName(uuid, "Surface", new SurfaceComponent(target.id(), target.kind()));
            emit("engine.surface.attachEntity", "surfaceId", target.id(), "uuid", uuid, "kind", target.kind());
        });
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void detachFromEntity(SurfaceHandle target) {
        requireHandle(target);
        onJmeSyncVoid("surface.detachFromEntity", () -> {
            String uuid = registry.detachSurface(target.id());
            if (uuid == null || uuid.isBlank()) return;

            engine.getEcs().removeComponentByName(uuid, "Surface");
            emit("engine.surface.detachFromEntity", "surfaceId", target.id(), "uuid", uuid, "kind", target.kind());
        });
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void attachToRoot(SurfaceHandle target) {
        requireHandle(target);
        onJmeSyncVoid("surface.attachToRoot", () -> registry.attachToRoot(target.id()));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void detach(SurfaceHandle target) {
        requireHandle(target);
        onJmeSyncVoid("surface.detach", () -> registry.detachFromParent(target.id()));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public boolean exists(SurfaceHandle target) {
        return target != null && registry.exists(target.id());
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setName(SurfaceHandle target, String name) {
        Objects.requireNonNull(target, "target");
        final String n = name;
        onJmeSyncVoid("surface.setName", () -> {
            Spatial s = requireSpatial(target);
            if (n != null) s.setName(n);
        });
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setPos(SurfaceHandle target, Object pos) {
        Objects.requireNonNull(target, "target");
        final Vector3f p = vec3Any(pos, 0f, 0f, 0f);
        onJmeSyncVoid("surface.setPos", () -> requireSpatial(target).setLocalTranslation(p));
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setRot(SurfaceHandle target, Object rotDeg) {
        Objects.requireNonNull(target, "target");
        final Vector3f deg = vec3Any(rotDeg, 0f, 0f, 0f);

        onJmeSyncVoid("surface.setRot", () -> {
            Spatial s = requireSpatial(target);
            float rx = deg.x * (float) (Math.PI / 180.0);
            float ry = deg.y * (float) (Math.PI / 180.0);
            float rz = deg.z * (float) (Math.PI / 180.0);
            s.setLocalRotation(new Quaternion().fromAngles(rx, ry, rz));
        });
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setScale(SurfaceHandle target, Object scale) {
        Objects.requireNonNull(target, "target");

        final boolean isScalar =
                (scale instanceof Number)
                        || (scale instanceof Value v && !v.isNull() && v.isNumber());

        final float scalar = isScalar
                ? (scale instanceof Number n ? n.floatValue() : (float) ((Value) scale).asDouble())
                : 0f;

        final Vector3f v3 = (!isScalar) ? vec3Any(scale, 1f, 1f, 1f) : null;

        onJmeSyncVoid("surface.setScale", () -> {
            Spatial s = requireSpatial(target);
            if (isScalar) s.setLocalScale(scalar);
            else s.setLocalScale(v3);
        });
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setCull(SurfaceHandle target, String hint) {
        Objects.requireNonNull(target, "target");
        final Spatial.CullHint ch = parseCullHint(hint);
        onJmeSyncVoid("surface.setCull", () -> requireSpatial(target).setCullHint(ch));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setVisible(SurfaceHandle target, boolean visible) {
        Objects.requireNonNull(target, "target");
        final Spatial.CullHint ch = visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always;
        onJmeSyncVoid("surface.setVisible", () -> requireSpatial(target).setCullHint(ch));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setShadowMode(SurfaceHandle target, String mode) {
        Objects.requireNonNull(target, "target");
        final RenderQueue.ShadowMode sm = parseShadowMode(mode);
        onJmeSyncVoid("surface.setShadowMode", () -> requireSpatial(target).setShadowMode(sm));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setTransform(SurfaceHandle target, Value cfg) {
        Objects.requireNonNull(target, "target");

        final TransformCfg t = TransformCfg.parse(cfg);
        onJmeSyncVoid("surface.setTransform", () -> t.apply(requireSpatial(target)));
    }

    // ---------------------------------------------------------------------
    // Exports: bounds + picking
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void applyMaterialToChildren(SurfaceHandle target, Object materialHandle) {
        Objects.requireNonNull(target, "target");

        final Material mat = unwrapMaterial(materialHandle);
        if (mat == null)
            throw new IllegalArgumentException("surface.applyMaterialToChildren: materialHandle is invalid");

        onJmeSyncVoid("surface.applyMaterialToChildren", () -> applyMaterialRecursive(requireSpatial(target), mat));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setMaterial(SurfaceHandle target, Object materialHandleOrCfg) {
        Objects.requireNonNull(target, "target");

        final Material matDirect = unwrapMaterial(materialHandleOrCfg);
        final MaterialApiImpl.MaterialHandle created;
        final TileWorld tileWorld;

        if (matDirect != null) {
            created = null;
            tileWorld = null;
        } else if (materialHandleOrCfg instanceof Value v && v != null && !v.isNull() && v.hasMembers() && v.hasMember("def")) {
            created = engine.material().create(v); // executed on caller thread (material API handles its own threading)
            tileWorld = extractTileWorld(v);
        } else {
            created = null;
            tileWorld = null;
        }

        final Material mat = (matDirect != null) ? matDirect : (created != null ? created.__material() : null);
        if (mat == null) throw new IllegalArgumentException("surface.setMaterial: materialHandle is invalid");

        onJmeSyncVoid("surface.setMaterial", () -> {
            Spatial s = requireSpatial(target);

            if (s instanceof TerrainQuad tq) {
                tq.setMaterial(mat);
                emit("engine.surface.material.set", "surfaceId", target.id(), "kind", target.kind(), "type", "terrain");
                return;
            }

            if (s instanceof Geometry g) {
                g.setMaterial(mat);
                applyTileWorldToGeometryIfAny(g, tileWorld);
                emit("engine.surface.material.set", "surfaceId", target.id(), "kind", target.kind(), "type", "geometry");
                return;
            }

            if (s instanceof Node n) {
                applyMaterialRecursive(n, mat);
                if (tileWorld != null) {
                    applyTileWorldRecursive(n, tileWorld);
                }
                emit("engine.surface.material.set", "surfaceId", target.id(), "kind", target.kind(), "type", "node");
                return;
            }

            throw new IllegalStateException("surface.setMaterial: unsupported Spatial type=" + s.getClass().getName());
        });
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public WorldBounds getWorldBounds(SurfaceHandle target) {
        Objects.requireNonNull(target, "target");
        return onJmeSync("surface.getWorldBounds", () -> {
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
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Hit[] raycast(SurfaceHandle target, Value cfg) {
        Objects.requireNonNull(target, "target");
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("surface.raycast: cfg is null");

        final RaycastCfg c = RaycastCfg.parse(cfg, new Vector3f(0f, 0f, 0f), new Vector3f(0f, -1f, 0f), 10_000f, 16, true);

        return onJmeSync("surface.raycast", () -> {
            Spatial s = requireSpatial(target);
            Ray ray = new Ray(c.origin, c.dir);
            ray.setLimit(c.max);
            return collide(s, ray, c.onlyClosest, c.limit);
        }, new Hit[0]);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Hit[] pickUnderCursorCfg(SurfaceHandle target, Value cfg) {
        Objects.requireNonNull(target, "target");

        final double cx = (cfg != null && !cfg.isNull()) ? num(cfg, "x", Double.NaN) : Double.NaN;
        final double cy = (cfg != null && !cfg.isNull()) ? num(cfg, "y", Double.NaN) : Double.NaN;
        final float max = (cfg != null && !cfg.isNull()) ? (float) num(cfg, "max", 10_000.0) : 10_000.0f;
        final int limit = (cfg != null && !cfg.isNull()) ? clampInt(num(cfg, "limit", 16.0), 1, 256) : 16;
        final boolean onlyClosest = (cfg != null && !cfg.isNull()) ? bool(cfg, "onlyClosest", true) : true;

        return onJmeSync("surface.pickUnderCursorCfg", () -> {
            Spatial s = requireSpatial(target);
            Camera cam = engine.getApp().getCamera();
            if (cam == null) return new Hit[0];

            float x = Double.isFinite(cx) ? (float) cx : cam.getWidth() * 0.5f;
            float y = Double.isFinite(cy) ? (float) cy : cam.getHeight() * 0.5f;

            Vector3f origin = cam.getWorldCoordinates(new Vector2f(x, y), 0f);
            Vector3f far = cam.getWorldCoordinates(new Vector2f(x, y), 1f);
            Vector3f dir = far.subtract(origin);
            if (dir.lengthSquared() < 1e-8f) return new Hit[0];
            dir.normalizeLocal();

            Ray ray = new Ray(origin, dir);
            ray.setLimit(max);
            return collide(s, ray, onlyClosest, limit);
        }, new Hit[0]);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Hit[] pickUnderCursor() {
        return pickUnderCursorCfg((Value) null);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Hit[] pickUnderCursor(SurfaceHandle target) {
        return pickUnderCursorCfg(target, null);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Hit[] pickUnderCursorCfg(Value cfg) {
        final double cx = (cfg != null && !cfg.isNull()) ? num(cfg, "x", Double.NaN) : Double.NaN;
        final double cy = (cfg != null && !cfg.isNull()) ? num(cfg, "y", Double.NaN) : Double.NaN;
        final float max = (cfg != null && !cfg.isNull()) ? (float) num(cfg, "max", 10_000.0) : 10_000.0f;
        final int limit = (cfg != null && !cfg.isNull()) ? clampInt(num(cfg, "limit", 16.0), 1, 256) : 16;
        final boolean onlyClosest = (cfg != null && !cfg.isNull()) ? bool(cfg, "onlyClosest", true) : true;

        return onJmeSync("surface.pickUnderCursorCfg(world)", () -> {
            Camera cam = engine.getApp().getCamera();
            if (cam == null) return new Hit[0];

            Spatial root = engine.getApp().getRootNode();
            if (root == null) return new Hit[0];

            float x = Double.isFinite(cx) ? (float) cx : cam.getWidth() * 0.5f;
            float y = Double.isFinite(cy) ? (float) cy : cam.getHeight() * 0.5f;

            Vector3f origin = cam.getWorldCoordinates(new Vector2f(x, y), 0f);
            Vector3f far = cam.getWorldCoordinates(new Vector2f(x, y), 1f);
            Vector3f dir = far.subtract(origin);
            if (dir.lengthSquared() < 1e-8f) return new Hit[0];
            dir.normalizeLocal();

            Ray ray = new Ray(origin, dir);
            ray.setLimit(max);
            return collide(root, ray, onlyClosest, limit);
        }, new Hit[0]);
    }

    // ---------------------------------------------------------------------
    // Exports: physics bridge
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int attachedBody(int surfaceId) {
        if (surfaceId <= 0) return 0;
        if (!registry.exists(surfaceId)) return 0;

        PhysicsApi p = this.physicsApi;
        if (p == null) return 0;

        try {
            return p.bodyOfSurface(surfaceId);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    // ---------------------------------------------------------------------
    // Exports: destroy
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void destroy(SurfaceHandle target) {
        requireHandle(target);

        onJmeSyncVoid("surface.destroy", () -> {
            PhysicsApi p = this.physicsApi;
            if (p instanceof EnginePhysicsModule impl) impl.cleanupSurface(target.id());

            registry.detachFromParent(target.id());

            String uuid = registry.detachSurface(target.id());
            if (uuid != null && !uuid.isBlank()) {
                engine.getEcs().removeComponentByName(uuid, "Surface");
            }

            registry.destroy(target.id());

            emit("engine.surface.destroyed", "surfaceId", target.id(), "uuid", uuid, "kind", target.kind());
        });
    }

    // ---------------------------------------------------------------------
    // Parsing: cull/shadow/transform
    // ---------------------------------------------------------------------

    private static final class TileWorld {
        final float tileX;
        final float tileZ;

        TileWorld(float tileX, float tileZ) {
            this.tileX = tileX;
            this.tileZ = tileZ;
        }
    }

    private static final class RaycastCfg {
        final Vector3f origin;
        final Vector3f dir;
        final float max;
        final int limit;
        final boolean onlyClosest;

        RaycastCfg(Vector3f origin, Vector3f dir, float max, int limit, boolean onlyClosest) {
            this.origin = origin;
            this.dir = dir;
            this.max = max;
            this.limit = limit;
            this.onlyClosest = onlyClosest;
        }

        static RaycastCfg parse(Value cfg, Vector3f originDef, Vector3f dirDef, float maxDef, int limitDef, boolean onlyClosestDef) {
            if (cfg == null || cfg.isNull()) {
                return new RaycastCfg(originDef, dirDef, maxDef, limitDef, onlyClosestDef);
            }

            Vector3f o = vec3(member(cfg, "origin"), originDef.x, originDef.y, originDef.z);
            Vector3f d = vec3(member(cfg, "dir"), dirDef.x, dirDef.y, dirDef.z);
            float max = (float) num(cfg, "max", maxDef);
            int limit = clampInt(num(cfg, "limit", limitDef), 1, 256);
            boolean onlyClosest = bool(cfg, "onlyClosest", onlyClosestDef);

            if (d.lengthSquared() < 1e-8f) d.set(dirDef);
            d.normalizeLocal();

            return new RaycastCfg(o, d, max, limit, onlyClosest);
        }
    }

    private static final class TransformCfg {
        final Vector3f pos;
        final Vector3f scale;
        final Vector3f rotDeg;
        final RenderQueue.ShadowMode shadowMode;
        final Spatial.CullHint cullHint;
        final RenderQueue.Bucket bucket;

        TransformCfg(Vector3f pos,
                     Vector3f scale,
                     Vector3f rotDeg,
                     RenderQueue.ShadowMode shadowMode,
                     Spatial.CullHint cullHint,
                     RenderQueue.Bucket bucket) {
            this.pos = pos;
            this.scale = scale;
            this.rotDeg = rotDeg;
            this.shadowMode = shadowMode;
            this.cullHint = cullHint;
            this.bucket = bucket;
        }

        static TransformCfg parse(Value cfg) {
            if (cfg == null || cfg.isNull()) {
                return new TransformCfg(null, null, null, null, null, null);
            }

            Value pos = member(cfg, "pos");
            Value sc = member(cfg, "scale");
            Value rot = member(cfg, "rot");
            Value shadow = member(cfg, "shadow");
            Value cull = member(cfg, "cull");
            Value bucket = member(cfg, "bucket");

            Vector3f p = (pos != null && !pos.isNull()) ? vec3(pos, 0f, 0f, 0f) : null;

            Vector3f s;
            if (sc != null && !sc.isNull()) {
                if (sc.isNumber()) {
                    float k = (float) sc.asDouble();
                    s = new Vector3f(k, k, k);
                } else {
                    s = vec3(sc, 1f, 1f, 1f);
                }
            } else {
                s = null;
            }

            Vector3f r = (rot != null && !rot.isNull()) ? vec3(rot, 0f, 0f, 0f) : null;

            RenderQueue.ShadowMode sm = (shadow != null && !shadow.isNull()) ? parseShadowMode(shadow.asString()) : null;
            Spatial.CullHint ch = (cull != null && !cull.isNull()) ? parseCullHint(cull.asString()) : null;

            RenderQueue.Bucket b = null;
            if (bucket != null && !bucket.isNull()) {
                String raw = bucket.asString();
                if (raw != null) {
                    String m = raw.trim().toLowerCase(Locale.ROOT);
                    b = switch (m) {
                        case "sky" -> RenderQueue.Bucket.Sky;
                        case "gui" -> RenderQueue.Bucket.Gui;
                        case "opaque" -> RenderQueue.Bucket.Opaque;
                        case "transparent" -> RenderQueue.Bucket.Transparent;
                        case "translucent" -> RenderQueue.Bucket.Translucent;
                        default -> null;
                    };
                }
            }

            return new TransformCfg(p, s, r, sm, ch, b);
        }

        void apply(Spatial s) {
            if (s == null) return;

            if (pos != null) s.setLocalTranslation(pos);
            if (scale != null) s.setLocalScale(scale);

            if (rotDeg != null) {
                float rx = rotDeg.x * (float) (Math.PI / 180.0);
                float ry = rotDeg.y * (float) (Math.PI / 180.0);
                float rz = rotDeg.z * (float) (Math.PI / 180.0);
                s.setLocalRotation(new Quaternion().fromAngles(rx, ry, rz));
            }

            if (shadowMode != null) s.setShadowMode(shadowMode);
            if (cullHint != null) s.setCullHint(cullHint);
            if (bucket != null) s.setQueueBucket(bucket);
        }
    }

    // ---------------------------------------------------------------------
    // Types
    // ---------------------------------------------------------------------

    public static final class SurfaceComponent {
        @HostAccess.Export
        public final int surfaceId;
        @HostAccess.Export
        public final String kind;

        public SurfaceComponent(int surfaceId, String kind) {
            this.surfaceId = surfaceId;
            this.kind = kind;
        }
    }
}
