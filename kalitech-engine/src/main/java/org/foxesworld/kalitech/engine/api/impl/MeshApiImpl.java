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
import org.foxesworld.kalitech.engine.api.interfaces.MeshApi;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;

public final class MeshApiImpl implements MeshApi {

    private static final Logger log = LogManager.getLogger(MeshApiImpl.class);

    private final EngineApiImpl engine;
    private final AssetManager assets;
    private final SurfaceRegistry registry;

    public MeshApiImpl(EngineApiImpl engine, AssetManager assets, SurfaceRegistry registry) {
        this.engine = Objects.requireNonNull(engine);
        this.assets = Objects.requireNonNull(assets);
        this.registry = Objects.requireNonNull(registry);
    }

    private static Value member(Value v, String k) {
        return (v != null && v.hasMember(k)) ? v.getMember(k) : null;
    }
    private static String str(Value v, String k, String def) {
        Value m = member(v, k);
        return (m != null && !m.isNull()) ? m.asString() : def;
    }
    private static double num(Value v, String k, double def) {
        Value m = member(v, k);
        return (m != null && !m.isNull() && m.isNumber()) ? m.asDouble() : def;
    }
    private static boolean bool(Value v, String k, boolean def) {
        Value m = member(v, k);
        return (m != null && !m.isNull() && m.isBoolean()) ? m.asBoolean() : def;
    }
    private static double clamp(double x, double a, double b) { return Math.max(a, Math.min(b, x)); }

    private Material defaultMat() {
        Material m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", ColorRGBA.White);
        return m;
    }

    private void applyTransform(Spatial s, Value cfg) {
        // используем вашу утилиту, если она уже есть
        // (у вас она точно есть в SurfaceApiImpl из прошлых частей)
        try { SurfaceApiImpl.applyTransform(s, cfg); } catch (Throwable ignored) {}
    }

    private SurfaceApi.SurfaceHandle register(Spatial s, String kind, Value cfg) {
        s.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        SurfaceApi.SurfaceHandle h = registry.register(s, kind);

        // material (если передали handle)
        Value mh = member(cfg, "material");
        if (mh != null && !mh.isNull()) engine.surface().setMaterial(h, mh);

        boolean attach = bool(cfg, "attach", true);
        if (attach) registry.attachToRoot(h.id());

        return h;
    }

    @Override
    @HostAccess.Export
    public SurfaceApi.SurfaceHandle box(Value cfg) {
        String name = str(cfg, "name", "box");
        float hx = (float) clamp(num(cfg, "hx", 0.5), 0.001, 1e6);
        float hy = (float) clamp(num(cfg, "hy", 0.5), 0.001, 1e6);
        float hz = (float) clamp(num(cfg, "hz", 0.5), 0.001, 1e6);

        Geometry g = new Geometry(name, new Box(hx, hy, hz));
        g.setMaterial(defaultMat());
        applyTransform(g, cfg);
        return register(g, "box", cfg);
    }

    @Override
    @HostAccess.Export
    public SurfaceApi.SurfaceHandle sphere(Value cfg) {
        String name = str(cfg, "name", "sphere");
        float r = (float) clamp(num(cfg, "radius", 0.5), 0.001, 1e6);
        int z = (int) clamp(num(cfg, "zSamples", 16), 4, 128);
        int rS = (int) clamp(num(cfg, "radialSamples", 16), 4, 128);

        Geometry g = new Geometry(name, new Sphere(z, rS, r));
        g.setMaterial(defaultMat());
        applyTransform(g, cfg);
        return register(g, "sphere", cfg);
    }

    @Override
    @HostAccess.Export
    public SurfaceApi.SurfaceHandle cylinder(Value cfg) {
        String name = str(cfg, "name", "cylinder");
        float r = (float) clamp(num(cfg, "radius", 0.35), 0.001, 1e6);
        float h = (float) clamp(num(cfg, "height", 1.2), 0.001, 1e6);
        int axis = (int) clamp(num(cfg, "axisSamples", 12), 3, 128);
        int radial = (int) clamp(num(cfg, "radialSamples", 12), 3, 128);
        boolean closed = bool(cfg, "closed", true);

        Geometry g = new Geometry(name, new Cylinder(axis, radial, r, h, closed));
        g.setMaterial(defaultMat());
        applyTransform(g, cfg);
        return register(g, "cylinder", cfg);
    }

    @Override
    @HostAccess.Export
    public SurfaceApi.SurfaceHandle capsule(Value cfg) {
        // Визуальная капсула: цилиндр + 2 сферы
        String name = str(cfg, "name", "capsule");
        float radius = (float) clamp(num(cfg, "radius", 0.35), 0.001, 1e6);
        float height = (float) clamp(num(cfg, "height", 1.2), 0.001, 1e6);

        // цилиндрическая часть: height - 2*radius (чтобы общая высота была height)
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

        applyTransform(root, cfg);
        return register(root, "capsule", cfg);
    }
}