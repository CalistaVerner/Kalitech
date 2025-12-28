// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.impl.material.MaterialApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.MeshApi;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MeshApiImpl implements MeshApi {

    private static final Logger log = LogManager.getLogger(MeshApiImpl.class);

    private static final AtomicBoolean LOADERS_REGISTERED = new AtomicBoolean(false);

    private final EngineApiImpl engine;
    private final AssetManager assets;
    private final SurfaceRegistry registry;

    private volatile Material cachedDefaultMat;

    public MeshApiImpl(EngineApiImpl engine, AssetManager assets, SurfaceRegistry registry) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.registry = Objects.requireNonNull(registry, "registry");
        ensureModelLoadersRegistered();
    }

    private void ensureModelLoadersRegistered() {
        if (!LOADERS_REGISTERED.compareAndSet(false, true)) return;

        // OBJ
        tryRegisterLoader("com.jme3.scene.plugins.OBJLoader", "obj", "mtl");

        // FBX (jme3-plugins)
        tryRegisterLoader("com.jme3.scene.plugins.fbx.FbxLoader", "fbx");
    }

    @SuppressWarnings("unchecked")
    private void tryRegisterLoader(String loaderClassName, String... extensions) {
        try {
            Class<?> c = Class.forName(loaderClassName);
            if (!AssetLoader.class.isAssignableFrom(c)) {
                log.warn("MeshApi: class {} is not an AssetLoader", loaderClassName);
                return;
            }
            assets.registerLoader((Class<? extends AssetLoader>) c, extensions);
            log.info("MeshApi: registered loader={} extensions={}", loaderClassName, String.join(",", extensions));
        } catch (ClassNotFoundException e) {
            log.warn("MeshApi: loader not on classpath: {} (skip)", loaderClassName);
        } catch (Throwable t) {
            log.warn("MeshApi: failed to register loader: {}", loaderClassName, t);
        }
    }

    // ---------- small helpers ----------

    private static Value member(Value v, String k) {
        return (v != null && !v.isNull() && v.hasMember(k)) ? v.getMember(k) : null;
    }

    private static String str(Value v, String k, String def) {
        Value m = member(v, k);
        if (m == null || m.isNull()) return def;
        try {
            String s = m.asString();
            return (s == null || s.isBlank()) ? def : s;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static double num(Value v, String k, double def) {
        Value m = member(v, k);
        if (m == null || m.isNull() || !m.isNumber()) return def;
        try { return m.asDouble(); } catch (Throwable ignored) { return def; }
    }

    private static boolean bool(Value v, String k, boolean def) {
        Value m = member(v, k);
        if (m == null || m.isNull() || !m.isBoolean()) return def;
        try { return m.asBoolean(); } catch (Throwable ignored) { return def; }
    }

    private static double clamp(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    private static String normType(String type) {
        if (type == null) return "box";
        String t = type.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return "box";
        if ("cube".equals(t)) return "box";
        return t;
    }

    private Material defaultMat() {
        Material m = cachedDefaultMat;
        if (m == null) {
            Material created = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            created.setColor("Color", ColorRGBA.White);
            cachedDefaultMat = created;
            return created;
        }
        return m;
    }

    private void applyTransform(Spatial s, Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        // pos: [x,y,z] or {x,y,z}
        Value pos = member(cfg, "pos");
        if (pos != null && !pos.isNull()) {
            float x = 0, y = 0, z = 0;
            if (pos.hasArrayElements() && pos.getArraySize() >= 3) {
                x = (float) pos.getArrayElement(0).asDouble();
                y = (float) pos.getArrayElement(1).asDouble();
                z = (float) pos.getArrayElement(2).asDouble();
            } else if (pos.hasMembers()) {
                Value mx = member(pos, "x");
                Value my = member(pos, "y");
                Value mz = member(pos, "z");
                if (mx != null && mx.isNumber()) x = (float) mx.asDouble();
                if (my != null && my.isNumber()) y = (float) my.asDouble();
                if (mz != null && mz.isNumber()) z = (float) mz.asDouble();
            }
            s.setLocalTranslation(x, y, z);
        }

        // rot: [x,y,z] in degrees (yaw/pitch/roll style is handled in your existing code logic)
        Value rot = member(cfg, "rot");
        if (rot != null && !rot.isNull()) {
            float rx = 0, ry = 0, rz = 0;
            if (rot.hasArrayElements() && rot.getArraySize() >= 3) {
                rx = (float) rot.getArrayElement(0).asDouble();
                ry = (float) rot.getArrayElement(1).asDouble();
                rz = (float) rot.getArrayElement(2).asDouble();
            } else if (rot.hasMembers()) {
                Value mx = member(rot, "x");
                Value my = member(rot, "y");
                Value mz = member(rot, "z");
                if (mx != null && mx.isNumber()) rx = (float) mx.asDouble();
                if (my != null && my.isNumber()) ry = (float) my.asDouble();
                if (mz != null && mz.isNumber()) rz = (float) mz.asDouble();
            }
            s.setLocalRotation(new com.jme3.math.Quaternion().fromAngles(
                    (float) Math.toRadians(rx),
                    (float) Math.toRadians(ry),
                    (float) Math.toRadians(rz)
            ));
        }

        // scale: number | [x,y,z] | {x,y,z}
        Value sc = member(cfg, "scale");
        if (sc != null && !sc.isNull()) {
            if (sc.isNumber()) {
                float v = (float) sc.asDouble();
                s.setLocalScale(v);
            } else if (sc.hasArrayElements() && sc.getArraySize() >= 3) {
                float x = (float) sc.getArrayElement(0).asDouble();
                float y = (float) sc.getArrayElement(1).asDouble();
                float z = (float) sc.getArrayElement(2).asDouble();
                s.setLocalScale(x, y, z);
            } else if (sc.hasMembers()) {
                float x = 1, y = 1, z = 1;
                Value mx = member(sc, "x");
                Value my = member(sc, "y");
                Value mz = member(sc, "z");
                if (mx != null && mx.isNumber()) x = (float) mx.asDouble();
                if (my != null && my.isNumber()) y = (float) my.asDouble();
                if (mz != null && mz.isNumber()) z = (float) mz.asDouble();
                s.setLocalScale(x, y, z);
            }
        }
    }

    // ---------- primitives builders ----------

    private Spatial buildBox(Value cfg, String name) {
        double size = num(cfg, "size", -1);
        float hx;
        float hy;
        float hz;

        if (size > 0) {
            float he = (float) clamp(size * 0.5, 0.001, 1e6);
            hx = hy = hz = he;
        } else {
            hx = (float) clamp(num(cfg, "hx", 0.5), 0.001, 1e6);
            hy = (float) clamp(num(cfg, "hy", 0.5), 0.001, 1e6);
            hz = (float) clamp(num(cfg, "hz", 0.5), 0.001, 1e6);
        }

        Geometry g = new Geometry(name, new Box(hx, hy, hz));
        g.setMaterial(defaultMat());
        return g;
    }

    private Spatial buildSphere(Value cfg, String name) {
        int zS = (int) clamp(num(cfg, "zSamples", 16), 3, 128);
        int radial = (int) clamp(num(cfg, "radialSamples", 16), 3, 256);
        float r = (float) clamp(num(cfg, "radius", 1.0), 0.001, 1e6);

        Geometry g = new Geometry(name, new Sphere(zS, radial, r));
        g.setMaterial(defaultMat());
        return g;
    }

    private Spatial buildCylinder(Value cfg, String name) {
        int axis = (int) clamp(num(cfg, "axisSamples", 2), 1, 64);
        int radial = (int) clamp(num(cfg, "radialSamples", 16), 3, 256);
        float r = (float) clamp(num(cfg, "radius", 0.5), 0.001, 1e6);
        float h = (float) clamp(num(cfg, "height", 1.0), 0.001, 1e6);

        Geometry g = new Geometry(name, new Cylinder(axis, radial, r, h, true));
        g.setMaterial(defaultMat());
        return g;
    }

    private Spatial buildCapsule(Value cfg, String name) {
        int zS = (int) clamp(num(cfg, "zSamples", 8), 3, 64);
        int radial = (int) clamp(num(cfg, "radialSamples", 16), 3, 256);

        float radius = (float) clamp(num(cfg, "radius", 0.35), 0.001, 1e6);
        float h = (float) clamp(num(cfg, "height", 1.8), 0.001, 1e6);
        float cylH = Math.max(0.001f, h - 2f * radius);

        Node root = new Node(name);

        Geometry mid = new Geometry(name + ".mid", new Cylinder(2, radial, radius, cylH, true));
        mid.setMaterial(defaultMat());
        root.attachChild(mid);

        Geometry top = new Geometry(name + ".top", new Sphere(zS, radial, radius));
        top.setMaterial(defaultMat());
        top.setLocalTranslation(0f, cylH * 0.5f, 0f);
        root.attachChild(top);

        Geometry bottom = new Geometry(name + ".bottom", new Sphere(zS, radial, radius));
        bottom.setMaterial(defaultMat());
        bottom.setLocalTranslation(0f, -cylH * 0.5f, 0f);
        root.attachChild(bottom);

        return root;
    }

    // ---------- NEW: model builder ----------

    private Spatial buildModel(Value cfg, String name) {
        String path = str(cfg, "path", null);
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("mesh.create: type='model' requires cfg.path");
        }

        ensureModelLoadersRegistered();

        Spatial loaded;
        try {
            loaded = assets.loadModel(path.trim());
        } catch (Throwable t) {
            throw new IllegalStateException("mesh.create: failed to load model path='" + path + "'", t);
        }

        if (loaded == null) {
            throw new IllegalStateException("mesh.create: model is null for path='" + path + "'");
        }

        // Optional clone: protects against shared state when AssetManager caches.
        boolean clone = bool(cfg, "clone", true);
        Spatial model = clone ? loaded.clone(true) : loaded;

        if (name != null && !name.isBlank()) model.setName(name);
        return model;
    }

    private Spatial buildSpatial(String type, Value cfg) {
        String t = normType(type);
        String name = str(cfg, "name", t);

        Spatial spatial = switch (t) {
            case "box" -> buildBox(cfg, name);
            case "sphere" -> buildSphere(cfg, name);
            case "cylinder" -> buildCylinder(cfg, name);
            case "capsule" -> buildCapsule(cfg, name);
            case "model" -> buildModel(cfg, name);
            default -> throw new IllegalArgumentException("mesh.create: unknown type=" + type);
        };

        return spatial;
    }

    private void applyMaterial(SurfaceApi.SurfaceHandle h, Spatial s, Value cfg) {
        Value m = member(cfg, "material");
        if (m == null || m.isNull()) return;

        if (s instanceof Geometry || s instanceof com.jme3.terrain.geomipmap.TerrainQuad) {
            engine.surface().setMaterial(h, m);
            return;
        }

        if (s instanceof Node) {
            Object handleOrCfg = m;

            if (m.hasMembers() && m.hasMember("def")) {
                MaterialApiImpl.MaterialHandle mh = engine.material().create(m);
                handleOrCfg = mh;
            }

            engine.surface().applyMaterialToChildren(h, handleOrCfg);
        }
    }

    private Map<String, Object> autoCollider(String meshType, Value meshCfg) {
        String t = normType(meshType);

        Map<String, Object> col = new HashMap<>();
        switch (t) {
            case "box" -> {
                double size = num(meshCfg, "size", -1);
                float hx;
                float hy;
                float hz;

                if (size > 0) {
                    float he = (float) clamp(size * 0.5, 0.001, 1e6);
                    hx = hy = hz = he;
                } else {
                    hx = (float) clamp(num(meshCfg, "hx", 0.5), 0.001, 1e6);
                    hy = (float) clamp(num(meshCfg, "hy", 0.5), 0.001, 1e6);
                    hz = (float) clamp(num(meshCfg, "hz", 0.5), 0.001, 1e6);
                }

                col.put("type", "box");
                col.put("halfExtents", new float[]{hx, hy, hz});
            }
            case "sphere" -> {
                col.put("type", "sphere");
                col.put("radius", num(meshCfg, "radius", 1.0));
            }
            case "cylinder" -> {
                double r = num(meshCfg, "radius", 0.5);
                double h = num(meshCfg, "height", 1.0);
                col.put("type", "cylinder");
                col.put("halfExtents", new double[]{r, h * 0.5, r});
            }
            case "capsule" -> {
                double r = num(meshCfg, "radius", 0.35);
                double h = num(meshCfg, "height", 1.8);
                double cylH = Math.max(0.001, h - 2.0 * r);

                col.put("type", "capsule");
                col.put("radius", r);
                col.put("height", cylH);
            }
            case "model" -> {
                // Default for models is decided in maybeCreatePhysics() by mass:
                // mesh (static) / dynamicMesh (moving)
                col.put("type", "mesh");
            }
            default -> { }
        }
        return col;
    }

    private void maybeCreatePhysics(SurfaceApi.SurfaceHandle h, Value cfg, String meshType) {
        Value phys = member(cfg, "physics");
        if (phys == null || phys.isNull()) return;

        boolean enabled = bool(phys, "enabled", true);
        if (!enabled) return;

        double mass = num(phys, "mass", 0.0);

        Map<String, Object> p = new HashMap<>();
        p.put("surface", h.id());
        p.put("mass", mass);

        Value friction = member(phys, "friction");
        if (friction != null && friction.isNumber()) p.put("friction", friction.asDouble());

        Value restitution = member(phys, "restitution");
        if (restitution != null && restitution.isNumber()) p.put("restitution", restitution.asDouble());

        Value kinematic = member(phys, "kinematic");
        if (kinematic != null && kinematic.isBoolean()) p.put("kinematic", kinematic.asBoolean());

        Value lockRotation = member(phys, "lockRotation");
        if (lockRotation != null && lockRotation.isBoolean()) p.put("lockRotation", lockRotation.asBoolean());

        Value damping = member(phys, "damping");
        if (damping != null && !damping.isNull() && damping.hasMembers()) {
            Map<String, Object> d = new HashMap<>();
            Value ld = member(damping, "linear");
            Value ad = member(damping, "angular");
            if (ld != null && ld.isNumber()) d.put("linear", ld.asDouble());
            if (ad != null && ad.isNumber()) d.put("angular", ad.asDouble());
            if (!d.isEmpty()) p.put("damping", d);
        }

        Value collider = member(phys, "collider");
        if (collider != null && !collider.isNull()) {
            p.put("collider", collider);
        } else {
            // Auto collider:
            // - primitives -> by dimensions
            // - models -> mesh for static (mass<=0), dynamicMesh for moving bodies
            String t = normType(meshType);
            if ("model".equals(t)) {
                Map<String, Object> auto = new HashMap<>();
                auto.put("type", (mass > 0.0) ? "dynamicMesh" : "mesh");
                p.put("collider", auto);
            } else {
                Map<String, Object> auto = autoCollider(meshType, cfg);
                if (!auto.isEmpty()) p.put("collider", auto);
            }
        }

        engine.physics().body(p);
    }

    private SurfaceApi.SurfaceHandle register(Spatial s, String kind, Value cfg) {
        s.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        // ✅ registry: provide api explicitly
        SurfaceApi.SurfaceHandle h = registry.register(s, kind, engine.surface());

        boolean attach = bool(cfg, "attach", true);
        if (attach) registry.attachToRoot(h.id());

        applyMaterial(h, s, cfg);
        maybeCreatePhysics(h, cfg, kind);

        return h;
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle create(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("mesh.create(cfg): cfg is required");

        String type = str(cfg, "type", "box");
        String kind = normType(type);

        Spatial s = buildSpatial(kind, cfg);
        if (s instanceof Geometry g && g.getMaterial() == null) g.setMaterial(defaultMat());

        applyTransform(s, cfg);
        return register(s, kind, cfg);
    }
}