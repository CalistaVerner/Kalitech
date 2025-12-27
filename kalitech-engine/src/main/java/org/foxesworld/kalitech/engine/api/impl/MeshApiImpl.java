// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl;

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

/**
 * Mesh factory that composes: Surface + Material + Physics using a single JS config object.
 */
public final class MeshApiImpl implements MeshApi {

    private static final Logger log = LogManager.getLogger(MeshApiImpl.class);

    private final EngineApiImpl engine;
    private final AssetManager assets;
    private final SurfaceRegistry registry;

    // Creating a new default material for every mesh is a common source of stutter when creating many meshes.
    // Cache it (and clone if you ever need per-geometry mutation).
    private volatile Material cachedDefaultMat;

    public MeshApiImpl(EngineApiImpl engine, AssetManager assets, SurfaceRegistry registry) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    private static Value member(Value v, String k) {
        return (v != null && !v.isNull() && v.hasMember(k)) ? v.getMember(k) : null;
    }

    private static String str(Value v, String k, String def) {
        Value m = member(v, k);
        return (m != null && !m.isNull() && m.isString()) ? m.asString() : def;
    }

    private static double num(Value v, String k, double def) {
        Value m = member(v, k);
        return (m != null && !m.isNull() && m.isNumber()) ? m.asDouble() : def;
    }

    private static boolean bool(Value v, String k, boolean def) {
        Value m = member(v, k);
        return (m != null && !m.isNull() && m.isBoolean()) ? m.asBoolean() : def;
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
            m = created;
        }
        return m;
    }

    private void applyTransform(Spatial s, Value cfg) {
        SurfaceApiImpl.applyTransform(s, cfg);
    }

    private Geometry buildBox(Value cfg, String name) {
        double size = num(cfg, "size", -1);

        float hx;
        float hy;
        float hz;

        if (size > 0) {
            float half = (float) clamp(size * 0.5, 0.001, 1e6);
            hx = hy = hz = half;
        } else {
            hx = (float) clamp(num(cfg, "hx", 0.5), 0.001, 1e6);
            hy = (float) clamp(num(cfg, "hy", 0.5), 0.001, 1e6);
            hz = (float) clamp(num(cfg, "hz", 0.5), 0.001, 1e6);
        }

        return new Geometry(name, new Box(hx, hy, hz));
    }

    private Geometry buildSphere(Value cfg, String name) {
        float r = (float) clamp(num(cfg, "radius", 0.5), 0.001, 1e6);
        int z = (int) clamp(num(cfg, "zSamples", 16), 4, 128);
        int rs = (int) clamp(num(cfg, "radialSamples", 16), 4, 128);
        return new Geometry(name, new Sphere(z, rs, r));
    }

    private Geometry buildCylinder(Value cfg, String name) {
        float r = (float) clamp(num(cfg, "radius", 0.35), 0.001, 1e6);
        float h = (float) clamp(num(cfg, "height", 1.2), 0.001, 1e6);
        int axis = (int) clamp(num(cfg, "axisSamples", 12), 3, 128);
        int radial = (int) clamp(num(cfg, "radialSamples", 12), 3, 128);
        boolean closed = bool(cfg, "closed", true);
        return new Geometry(name, new Cylinder(axis, radial, r, h, closed));
    }

    private Node buildCapsule(Value cfg, String name) {
        float radius = (float) clamp(num(cfg, "radius", 0.35), 0.001, 1e6);
        float height = (float) clamp(num(cfg, "height", 1.8), 0.001, 1e6);

        float cylH = Math.max(0.001f, height - 2f * radius);

        int axis = (int) clamp(num(cfg, "axisSamples", 12), 3, 128);
        int radial = (int) clamp(num(cfg, "radialSamples", 12), 3, 128);
        int zS = (int) clamp(num(cfg, "zSamples", 16), 4, 128);

        Node root = new Node(name);

        Geometry body = new Geometry(name + ".body", new Cylinder(axis, radial, radius, cylH, true));
        body.setMaterial(defaultMat());
        root.attachChild(body);

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

    private Spatial buildSpatial(String type, Value cfg) {
        String t = normType(type);
        String name = str(cfg, "name", t);

        Spatial spatial = switch (t) {
            case "box" -> buildBox(cfg, name);
            case "sphere" -> buildSphere(cfg, name);
            case "cylinder" -> buildCylinder(cfg, name);
            case "capsule" -> buildCapsule(cfg, name);
            default -> throw new IllegalArgumentException("mesh.create: unknown type=" + type);
        };

        // ✅ единое место пост-инициализации
        if (name != null && !name.isBlank()) {
            spatial.setName(name);
        }

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
                col.put("radius", num(meshCfg, "radius", 0.5));
            }
            case "cylinder" -> {
                double r = num(meshCfg, "radius", 0.35);
                double h = num(meshCfg, "height", 1.2);

                col.put("type", "cylinder");
                col.put("halfExtents", new float[]{(float) r, (float) (h * 0.5), (float) r});
            }
            case "capsule" -> {
                double r = num(meshCfg, "radius", 0.35);
                double h = num(meshCfg, "height", 1.8);
                double cylH = Math.max(0.001, h - 2.0 * r);

                col.put("type", "capsule");
                col.put("radius", r);
                col.put("height", cylH);
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
            Map<String, Object> auto = autoCollider(meshType, cfg);
            if (!auto.isEmpty()) p.put("collider", auto);
        }

        engine.physics().body(p);
    }

    private SurfaceApi.SurfaceHandle register(Spatial s, String kind, Value cfg) {
        s.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        SurfaceApi.SurfaceHandle h = registry.register(s, kind);

        boolean attach = bool(cfg, "attach", true);
        if (attach) registry.attachToRoot(h.id());

        applyMaterial(h, s, cfg);
        maybeCreatePhysics(h, cfg, kind);

        return h;
    }

    /**
     * Universal constructor.
     */
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