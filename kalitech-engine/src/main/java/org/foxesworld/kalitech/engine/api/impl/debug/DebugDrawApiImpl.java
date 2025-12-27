package org.foxesworld.kalitech.engine.api.impl.debug;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.DebugDrawApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.*;

public final class DebugDrawApiImpl implements DebugDrawApi {

    private final SimpleApplication app;

    private final Node node = new Node("__kt_debugDraw");
    private final Geometry geom = new Geometry("__kt_debugLines");
    private final Mesh mesh = new Mesh();
    private final Material mat;

    private final ArrayList<LineCmd> lines = new ArrayList<>(1024);

    private boolean enabled = true;
    private boolean attached = false;

    private boolean depthTest = true;
    private boolean depthWrite = false;

    private float timeSec = 0f;
    private int dirty = 1;

    public DebugDrawApiImpl(EngineApiImpl engine) {
        this.app = Objects.requireNonNull(engine, "engine").getApp();

        mesh.setMode(Mesh.Mode.Lines);
        mesh.setDynamic();
        geom.setMesh(mesh);

        mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setBoolean("VertexColor", true);
        geom.setMaterial(mat);

        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        applyRenderState();
        node.attachChild(geom);

        ensureAttached();
    }

    @HostAccess.Export
    @Override
    public void enabled(boolean v) {
        this.enabled = v;
        if (!v) clear();
        ensureAttached();
    }

    @HostAccess.Export
    @Override
    public boolean enabled() {
        return enabled;
    }

    @HostAccess.Export
    @Override
    public void clear() {
        lines.clear();
        dirty = 1;
        rebuildMesh();
    }

    @HostAccess.Export
    @Override
    public void line(Value cfg) {
        if (!enabled) return;
        if (cfg == null || cfg.isNull()) return;

        Vector3f a = parseVec3(cfg.getMember("a"), 0, 0, 0);
        Vector3f b = parseVec3(cfg.getMember("b"), 0, 1, 0);

        ColorRGBA c = parseColor(cfg.getMember("color"), 1, 1, 0, 1);

        float ttl = (float) num(cfg, "ttl", 0.0);
        boolean dt = bool(cfg, "depthTest", this.depthTest);
        boolean dw = bool(cfg, "depthWrite", this.depthWrite);

        applyFlags(dt, dw);

        addLine(a, b, c, ttl);
    }

    @HostAccess.Export
    @Override
    public void ray(Value cfg) {
        if (!enabled) return;
        if (cfg == null || cfg.isNull()) return;

        Vector3f origin = parseVec3(cfg.getMember("origin"), 0, 0, 0);
        Vector3f dir = parseVec3(cfg.getMember("dir"), 0, 1, 0);
        float len = (float) num(cfg, "len", 1.0);

        if (dir.lengthSquared() < 1e-8f) dir.set(0, 1, 0);
        dir.normalizeLocal();

        Vector3f end = origin.add(dir.mult(len));

        ColorRGBA c = parseColor(cfg.getMember("color"), 1, 1, 0, 1);
        float ttl = (float) num(cfg, "ttl", 0.0);

        boolean dt = bool(cfg, "depthTest", this.depthTest);
        boolean dw = bool(cfg, "depthWrite", this.depthWrite);
        applyFlags(dt, dw);

        addLine(origin, end, c, ttl);

        boolean arrow = bool(cfg, "arrow", true);
        if (arrow) addArrow(end, dir, c, ttl, len * 0.08f);
    }

    @HostAccess.Export
    @Override
    public void axes(Value cfg) {
        if (!enabled) return;
        if (cfg == null || cfg.isNull()) return;

        Vector3f pos = parseVec3(cfg.getMember("pos"), 0, 0, 0);
        float size = (float) num(cfg, "size", 1.0);
        float ttl = (float) num(cfg, "ttl", 0.0);

        boolean dt = bool(cfg, "depthTest", this.depthTest);
        boolean dw = bool(cfg, "depthWrite", this.depthWrite);
        applyFlags(dt, dw);

        addLine(pos, pos.add(size, 0, 0), new ColorRGBA(1, 0, 0, 1), ttl);
        addLine(pos, pos.add(0, size, 0), new ColorRGBA(0, 1, 0, 1), ttl);
        addLine(pos, pos.add(0, 0, size), new ColorRGBA(0, 0, 1, 1), ttl);
    }

    @HostAccess.Export
    @Override
    public void tick(double tpf) {
        if (!enabled) return;

        float dt = (float) Math.max(0.0, tpf);
        timeSec += dt;

        boolean removed = false;
        for (Iterator<LineCmd> it = lines.iterator(); it.hasNext();) {
            LineCmd l = it.next();
            if (l.expireAt > 0f && timeSec >= l.expireAt) {
                it.remove();
                removed = true;
            }
        }

        if (removed) dirty = 1;
        if (dirty != 0) rebuildMesh();
    }

    private void ensureAttached() {
        if (app == null) return;
        if (app.getRootNode() == null) return;

        if (!enabled) {
            if (attached) {
                node.removeFromParent();
                attached = false;
            }
            return;
        }

        if (!attached) {
            app.getRootNode().attachChild(node);
            attached = true;
        }
    }

    private void applyFlags(boolean depthTest, boolean depthWrite) {
        if (this.depthTest == depthTest && this.depthWrite == depthWrite) return;
        this.depthTest = depthTest;
        this.depthWrite = depthWrite;
        applyRenderState();
    }

    private void applyRenderState() {
        var rs = mat.getAdditionalRenderState();
        rs.setDepthTest(depthTest);
        rs.setDepthWrite(depthWrite);
        rs.setWireframe(false);
        rs.setFaceCullMode(RenderState.FaceCullMode.Off);
        dirty = 1;
    }

    private void addLine(Vector3f a, Vector3f b, ColorRGBA c, float ttl) {
        float expire = (ttl > 0f) ? (timeSec + ttl) : 0f;
        lines.add(new LineCmd(a.clone(), b.clone(), c.clone(), expire));
        dirty = 1;
    }

    private void addArrow(Vector3f tip, Vector3f dirN, ColorRGBA c, float ttl, float size) {
        size = Math.max(0.01f, size);

        Vector3f up = Math.abs(dirN.y) < 0.95f ? Vector3f.UNIT_Y : Vector3f.UNIT_X;
        Vector3f right = dirN.cross(up).normalizeLocal();
        Vector3f back = dirN.negate();

        Vector3f p1 = tip.add(back.mult(size)).add(right.mult(size * 0.6f));
        Vector3f p2 = tip.add(back.mult(size)).add(right.mult(-size * 0.6f));

        addLine(tip, p1, c, ttl);
        addLine(tip, p2, c, ttl);
    }

    private void rebuildMesh() {
        dirty = 0;

        int n = lines.size();
        if (n <= 0) {
            mesh.clearBuffer(VertexBuffer.Type.Position);
            mesh.clearBuffer(VertexBuffer.Type.Color);
            mesh.updateBound();
            mesh.setStatic();
            mesh.setDynamic();
            return;
        }

        int verts = n * 2;
        FloatBuffer pb = BufferUtils.createFloatBuffer(verts * 3);
        FloatBuffer cb = BufferUtils.createFloatBuffer(verts * 4);

        for (int i = 0; i < n; i++) {
            LineCmd l = lines.get(i);

            pb.put(l.a.x).put(l.a.y).put(l.a.z);
            pb.put(l.b.x).put(l.b.y).put(l.b.z);

            cb.put(l.c.r).put(l.c.g).put(l.c.b).put(l.c.a);
            cb.put(l.c.r).put(l.c.g).put(l.c.b).put(l.c.a);
        }

        pb.flip();
        cb.flip();

        mesh.setBuffer(VertexBuffer.Type.Position, 3, pb);
        mesh.setBuffer(VertexBuffer.Type.Color, 4, cb);

        mesh.updateBound();
        mesh.updateCounts();
    }

    private static Vector3f parseVec3(Value v, float dx, float dy, float dz) {
        if (v == null || v.isNull()) return new Vector3f(dx, dy, dz);

        try {
            if (v.hasArrayElements() && v.getArraySize() >= 3) {
                return new Vector3f(
                        (float) v.getArrayElement(0).asDouble(),
                        (float) v.getArrayElement(1).asDouble(),
                        (float) v.getArrayElement(2).asDouble()
                );
            }
            if (v.hasMembers()) {
                float x = (float) num(v, "x", dx);
                float y = (float) num(v, "y", dy);
                float z = (float) num(v, "z", dz);
                return new Vector3f(x, y, z);
            }
        } catch (Throwable ignored) {}

        return new Vector3f(dx, dy, dz);
    }

    private static ColorRGBA parseColor(Value v, float dr, float dg, float db, float da) {
        if (v == null || v.isNull()) return new ColorRGBA(dr, dg, db, da);

        try {
            if (v.hasArrayElements()) {
                long n = v.getArraySize();
                if (n >= 3) {
                    float r = (float) v.getArrayElement(0).asDouble();
                    float g = (float) v.getArrayElement(1).asDouble();
                    float b = (float) v.getArrayElement(2).asDouble();
                    float a = (n >= 4) ? (float) v.getArrayElement(3).asDouble() : da;
                    return new ColorRGBA(r, g, b, a);
                }
            }
            if (v.hasMembers() && (v.hasMember("r") || v.hasMember("g") || v.hasMember("b"))) {
                float r = (float) num(v, "r", dr);
                float g = (float) num(v, "g", dg);
                float b = (float) num(v, "b", db);
                float a = (float) num(v, "a", da);
                return new ColorRGBA(r, g, b, a);
            }
        } catch (Throwable ignored) {}

        return new ColorRGBA(dr, dg, db, da);
    }

    private record LineCmd(Vector3f a, Vector3f b, ColorRGBA c, float expireAt) {}
}