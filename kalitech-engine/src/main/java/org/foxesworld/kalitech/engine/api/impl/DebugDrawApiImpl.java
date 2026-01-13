// FILE: org/foxesworld/kalitech/engine/api/impl/DebugDrawApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.DebugDrawApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.bool;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.num;

/**
 * DebugDraw:
 * - Two batches: depth-tested and no-depth (so you can mix per-line flags without global state fights).
 * - TTL support (seconds) per command.
 * - Convenience shapes (box/sphere/circle/polyline/grid) for fast debug drawing from JS.
 * <p>
 * NOTE: If your Engine returns DebugDrawApi typed strictly by interface, add new methods to DebugDrawApi too
 * if you want them visible in JS. Existing methods remain 100% compatible.
 */
public final class DebugDrawApiImpl extends AbstractApiModule implements DebugDrawApi {

    private SimpleApplication app;
    private volatile boolean inited;

    private final Node node = new Node("__kt_debugDraw");

    // Two geometries so depthTest can vary per command without touching renderstate every call.
    private final Geometry geomDepth = new Geometry("__kt_debugLines_depth");
    private final Geometry geomNoDepth = new Geometry("__kt_debugLines_nodpth");
    private final Mesh meshDepth = new Mesh();
    private final Mesh meshNoDepth = new Mesh();
    // Commands (stored allocation-light: primitives + color + expire + layer)
    private final ArrayList<LineCmd> cmds = new ArrayList<>(2048);
    private Material matDepth;
    private Material matNoDepth;

    private boolean enabled = true;
    private boolean attached = false;

    private float timeSec = 0f;
    private int dirty = 1;
    // Reusable buffers (reallocated only when capacity is insufficient)
    private FloatBuffer posDepth, colDepth;
    private FloatBuffer posNoDepth, colNoDepth;

    // Tunables (JS defaults when field missing)
    private boolean defaultDepthTest = true;
    private boolean defaultDepthWrite = false; // debug lines usually should not write depth
    private float defaultAlpha = 1.0f;

    // --- Module ctor (for ApiRegistry.register(new DebugDrawApiImpl())) ---
    public DebugDrawApiImpl() {
        super("debug", "DebugDraw", "1.1.0");
    }

    // --- Module lifecycle ---
    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        bind(ctx.engine);
    }

    private void bind(EngineApiImpl engine) {
        this.app = Objects.requireNonNull(engine, "engine").getApp();
        initOnce();
    }

    private static void setupMesh(Mesh m) {
        m.setMode(Mesh.Mode.Lines);
        m.setDynamic();
    }

    private static void applyRenderState(Material mat, boolean depthTest, boolean depthWrite) {
        RenderState rs = mat.getAdditionalRenderState();
        rs.setDepthTest(depthTest);
        rs.setDepthWrite(depthWrite);
        rs.setWireframe(false);
        rs.setFaceCullMode(RenderState.FaceCullMode.Off);
        rs.setBlendMode(RenderState.BlendMode.Alpha);
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
        } catch (Throwable ignored) {
        }

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
        } catch (Throwable ignored) {
        }

        return new ColorRGBA(dr, dg, db, da);
    }

    // --------------------------------------------------------------------
    // DebugDrawApi (existing contract)
    // --------------------------------------------------------------------

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

    private static Quaternion parseQuat(Value v) {
        if (v == null || v.isNull()) return null;
        try {
            if (v.hasArrayElements() && v.getArraySize() >= 4) {
                float x = (float) v.getArrayElement(0).asDouble();
                float y = (float) v.getArrayElement(1).asDouble();
                float z = (float) v.getArrayElement(2).asDouble();
                float w = (float) v.getArrayElement(3).asDouble();
                return new Quaternion(x, y, z, w);
            }
            if (v.hasMembers() && v.hasMember("w")) {
                float x = (float) num(v, "x", 0.0);
                float y = (float) num(v, "y", 0.0);
                float z = (float) num(v, "z", 0.0);
                float w = (float) num(v, "w", 1.0);
                return new Quaternion(x, y, z, w);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void initOnce() {
        if (inited) return;
        inited = true;

        // Meshes
        setupMesh(meshDepth);
        setupMesh(meshNoDepth);

        geomDepth.setMesh(meshDepth);
        geomNoDepth.setMesh(meshNoDepth);

        // Materials
        matDepth = makeUnshadedVertexColorMaterial();
        matNoDepth = makeUnshadedVertexColorMaterial();

        geomDepth.setMaterial(matDepth);
        geomNoDepth.setMaterial(matNoDepth);

        // Transparent bucket so alpha works consistently
        geomDepth.setQueueBucket(RenderQueue.Bucket.Transparent);
        geomNoDepth.setQueueBucket(RenderQueue.Bucket.Transparent);

        // Render state (split by layer)
        applyRenderState(matDepth, true, defaultDepthWrite);
        applyRenderState(matNoDepth, false, defaultDepthWrite);

        node.attachChild(geomDepth);
        node.attachChild(geomNoDepth);

        ensureAttached();
    }

    private Material makeUnshadedVertexColorMaterial() {
        Material m = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        m.setBoolean("VertexColor", true);
        return m;
    }

    @HostAccess.Export
    @Override
    public void clear() {
        cmds.clear();
        dirty = 1;
        rebuildMeshesIfDirty();
    }

    /**
     * cfg:
     *  - a: [x,y,z] or {x,y,z}
     *  - b: [x,y,z] or {x,y,z}
     *  - color: [r,g,b,a?] or {r,g,b,a?}  (0..1)
     *  - ttl: seconds (0 => persistent until clear)
     *  - depthTest: boolean (default = this.defaultDepthTest)
     *  - depthWrite: boolean (default = this.defaultDepthWrite)  (NOTE: currently applied per-layer; see below)
     */
    @HostAccess.Export
    @Override
    public void line(Value cfg) {
        if (!enabled) return;
        if (cfg == null || cfg.isNull()) return;

        Vector3f a = parseVec3(cfg.getMember("a"), 0, 0, 0);
        Vector3f b = parseVec3(cfg.getMember("b"), 0, 1, 0);

        ColorRGBA c = parseColor(cfg.getMember("color"), 1, 1, 0, defaultAlpha);

        float ttl = (float) num(cfg, "ttl", 0.0);
        boolean dt = bool(cfg, "depthTest", this.defaultDepthTest);

        // depthWrite is per-material; we still accept it, but we apply it as "default" knob
        // to avoid constant material flipping. If you want per-command depthWrite in future:
        // add 4 layers (dt/dw combinations). For now: accept and treat as a global default.
        boolean dw = bool(cfg, "depthWrite", this.defaultDepthWrite);
        if (dw != this.defaultDepthWrite) {
            this.defaultDepthWrite = dw;
            applyRenderState(matDepth, true, defaultDepthWrite);
            applyRenderState(matNoDepth, false, defaultDepthWrite);
            dirty = 1;
        }

        addLine(a, b, c, ttl, dt);
    }

    // --------------------------------------------------------------------
    // Convenience API (add to DebugDrawApi interface if you want strict typing)
    // --------------------------------------------------------------------

    /**
     * cfg:
     *  - origin, dir, len
     *  - color, ttl
     *  - arrow: boolean (default true)
     *  - arrowSize: number (default len*0.08)
     *  - depthTest/depthWrite like line()
     */
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

        ColorRGBA c = parseColor(cfg.getMember("color"), 1, 1, 0, defaultAlpha);
        float ttl = (float) num(cfg, "ttl", 0.0);

        boolean dt = bool(cfg, "depthTest", this.defaultDepthTest);
        boolean dw = bool(cfg, "depthWrite", this.defaultDepthWrite);
        if (dw != this.defaultDepthWrite) {
            this.defaultDepthWrite = dw;
            applyRenderState(matDepth, true, defaultDepthWrite);
            applyRenderState(matNoDepth, false, defaultDepthWrite);
            dirty = 1;
        }

        addLine(origin, end, c, ttl, dt);

        boolean arrow = bool(cfg, "arrow", true);
        if (arrow) {
            float as = (float) num(cfg, "arrowSize", len * 0.08);
            addArrow(end, dir, c, ttl, as, dt);
        }
    }

    /**
     * cfg:
     *  - pos: vec3
     *  - size: number
     *  - ttl: seconds
     *  - depthTest/depthWrite like line()
     */
    @HostAccess.Export
    @Override
    public void axes(Value cfg) {
        if (!enabled) return;
        if (cfg == null || cfg.isNull()) return;

        Vector3f pos = parseVec3(cfg.getMember("pos"), 0, 0, 0);
        float size = (float) num(cfg, "size", 1.0);
        float ttl = (float) num(cfg, "ttl", 0.0);

        boolean dt = bool(cfg, "depthTest", this.defaultDepthTest);
        boolean dw = bool(cfg, "depthWrite", this.defaultDepthWrite);
        if (dw != this.defaultDepthWrite) {
            this.defaultDepthWrite = dw;
            applyRenderState(matDepth, true, defaultDepthWrite);
            applyRenderState(matNoDepth, false, defaultDepthWrite);
            dirty = 1;
        }

        addLine(pos, pos.add(size, 0, 0), new ColorRGBA(1, 0, 0, 1), ttl, dt);
        addLine(pos, pos.add(0, size, 0), new ColorRGBA(0, 1, 0, 1), ttl, dt);
        addLine(pos, pos.add(0, 0, size), new ColorRGBA(0, 0, 1, 1), ttl, dt);
    }

    @HostAccess.Export
    @Override
    public void tick(double tpf) {
        if (!enabled) return;

        float dt = (float) Math.max(0.0, tpf);
        timeSec += dt;

        boolean removed = false;
        for (Iterator<LineCmd> it = cmds.iterator(); it.hasNext(); ) {
            LineCmd c = it.next();
            if (c.expireAt > 0f && timeSec >= c.expireAt) {
                it.remove();
                removed = true;
            }
        }

        if (removed) dirty = 1;
        rebuildMeshesIfDirty();
    }

    /**
     * Draw AABB/OBB as 12 edges.
     * <p>
     * cfg:
     * - center: vec3 (default 0,0,0)
     * - half: vec3 (default 0.5,0.5,0.5)  OR size: vec3 (interpreted as full size)
     * - rot: quat {x,y,z,w} OR eulerDeg {x,y,z} (optional)
     * - color, ttl, depthTest
     */
    @HostAccess.Export
    public void box(Value cfg) {
        if (!enabled) return;
        if (cfg == null || cfg.isNull()) return;

        Vector3f center = parseVec3(cfg.getMember("center"), 0, 0, 0);

        Vector3f half = parseVec3(cfg.getMember("half"), Float.NaN, Float.NaN, Float.NaN);
        if (!Float.isFinite(half.x) || !Float.isFinite(half.y) || !Float.isFinite(half.z)) {
            Vector3f size = parseVec3(cfg.getMember("size"), 1, 1, 1);
            half = size.mult(0.5f);
        }

        Quaternion rot = parseQuat(cfg.getMember("rot"));
        if (rot == null) {
            Vector3f eulerDeg = parseVec3(cfg.getMember("eulerDeg"), Float.NaN, Float.NaN, Float.NaN);
            if (Float.isFinite(eulerDeg.x) && Float.isFinite(eulerDeg.y) && Float.isFinite(eulerDeg.z)) {
                rot = new Quaternion().fromAngles(
                        eulerDeg.x * FastMath.DEG_TO_RAD,
                        eulerDeg.y * FastMath.DEG_TO_RAD,
                        eulerDeg.z * FastMath.DEG_TO_RAD
                );
            } else {
                rot = new Quaternion();
            }
        }

        ColorRGBA col = parseColor(cfg.getMember("color"), 0.95f, 0.95f, 0.95f, defaultAlpha);
        float ttl = (float) num(cfg, "ttl", 0.0);
        boolean dt = bool(cfg, "depthTest", this.defaultDepthTest);

        // 8 corners in local space
        Vector3f[] p = new Vector3f[8];
        p[0] = new Vector3f(-half.x, -half.y, -half.z);
        p[1] = new Vector3f(+half.x, -half.y, -half.z);
        p[2] = new Vector3f(+half.x, +half.y, -half.z);
        p[3] = new Vector3f(-half.x, +half.y, -half.z);
        p[4] = new Vector3f(-half.x, -half.y, +half.z);
        p[5] = new Vector3f(+half.x, -half.y, +half.z);
        p[6] = new Vector3f(+half.x, +half.y, +half.z);
        p[7] = new Vector3f(-half.x, +half.y, +half.z);

        for (int i = 0; i < 8; i++) {
            rot.multLocal(p[i]);
            p[i].addLocal(center);
        }

        // bottom rect (0-1-2-3)
        addLine(p[0], p[1], col, ttl, dt);
        addLine(p[1], p[2], col, ttl, dt);
        addLine(p[2], p[3], col, ttl, dt);
        addLine(p[3], p[0], col, ttl, dt);

        // top rect (4-5-6-7)
        addLine(p[4], p[5], col, ttl, dt);
        addLine(p[5], p[6], col, ttl, dt);
        addLine(p[6], p[7], col, ttl, dt);
        addLine(p[7], p[4], col, ttl, dt);

        // verticals
        addLine(p[0], p[4], col, ttl, dt);
        addLine(p[1], p[5], col, ttl, dt);
        addLine(p[2], p[6], col, ttl, dt);
        addLine(p[3], p[7], col, ttl, dt);
    }

    /**
     * cfg:
     * - center: vec3
     * - radius: number
     * - segments: int (default 24; clamped 6..256)
     * - color, ttl, depthTest
     * - mode: "wire" only for now (lines)
     */
    @HostAccess.Export
    public void sphere(Value cfg) {
        if (!enabled) return;
        if (cfg == null || cfg.isNull()) return;

        Vector3f center = parseVec3(cfg.getMember("center"), 0, 0, 0);
        float r = (float) num(cfg, "radius", 1.0);
        int seg = (int) Math.max(6, Math.min(256, (int) num(cfg, "segments", 24.0)));

        ColorRGBA col = parseColor(cfg.getMember("color"), 0.9f, 0.9f, 0.9f, defaultAlpha);
        float ttl = (float) num(cfg, "ttl", 0.0);
        boolean dt = bool(cfg, "depthTest", this.defaultDepthTest);

        // 3 great circles: XY, XZ, YZ
        drawCircle(center, Vector3f.UNIT_Z, r, seg, col, ttl, dt); // XY plane (normal Z)
        drawCircle(center, Vector3f.UNIT_Y, r, seg, col, ttl, dt); // XZ plane (normal Y)
        drawCircle(center, Vector3f.UNIT_X, r, seg, col, ttl, dt); // YZ plane (normal X)
    }

    // --------------------------------------------------------------------
    // Internals
    // --------------------------------------------------------------------

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

    /**
     * cfg:
     * - center: vec3
     * - normal: vec3 (default 0,1,0)
     * - radius: number
     * - segments: int
     * - color, ttl, depthTest
     */
    @HostAccess.Export
    public void circle(Value cfg) {
        if (!enabled) return;
        if (cfg == null || cfg.isNull()) return;

        Vector3f center = parseVec3(cfg.getMember("center"), 0, 0, 0);
        Vector3f normal = parseVec3(cfg.getMember("normal"), 0, 1, 0);
        float r = (float) num(cfg, "radius", 1.0);
        int seg = (int) Math.max(6, Math.min(256, (int) num(cfg, "segments", 24.0)));

        if (normal.lengthSquared() < 1e-8f) normal.set(0, 1, 0);
        normal.normalizeLocal();

        ColorRGBA col = parseColor(cfg.getMember("color"), 0.9f, 0.9f, 0.9f, defaultAlpha);
        float ttl = (float) num(cfg, "ttl", 0.0);
        boolean dt = bool(cfg, "depthTest", this.defaultDepthTest);

        drawCircle(center, normal, r, seg, col, ttl, dt);
    }


// --------------------------------------------------------------------
// INTERNAL FAST PATH (Java-only, package-private)
// Used by api.impl modules (TerrainApiImpl etc.)
// Not exported to JS.
// IMPORTANT: matches this DebugDrawApiImpl implementation (two-layer batching).
// --------------------------------------------------------------------

    void lineFast(Vector3f a, Vector3f b, ColorRGBA c, float ttl, boolean depthTest) {
        if (!enabled) return;
        if (a == null || b == null || c == null) return;
        addLine(a, b, c, ttl, depthTest);
    }

    void pointFast(Vector3f p, float radius, ColorRGBA c, float ttl, boolean depthTest) {
        if (!enabled) return;
        if (p == null || c == null) return;

        float r = Math.max(0.01f, radius);

        // tiny cross (3 lines)
        lineFast(new Vector3f(p.x - r, p.y, p.z), new Vector3f(p.x + r, p.y, p.z), c, ttl, depthTest);
        lineFast(new Vector3f(p.x, p.y - r, p.z), new Vector3f(p.x, p.y + r, p.z), c, ttl, depthTest);
        lineFast(new Vector3f(p.x, p.y, p.z - r), new Vector3f(p.x, p.y, p.z + r), c, ttl, depthTest);
    }



    /**
     * cfg:
     * - points: array of vec3 (>=2)
     * - closed: boolean (default false)
     * - color, ttl, depthTest
     */
    @HostAccess.Export
    public void polyline(Value cfg) {
        if (!enabled) return;
        if (cfg == null || cfg.isNull()) return;

        Value pts = cfg.getMember("points");
        if (pts == null || pts.isNull() || !pts.hasArrayElements()) return;

        long n = pts.getArraySize();
        if (n < 2) return;

        boolean closed = bool(cfg, "closed", false);
        ColorRGBA col = parseColor(cfg.getMember("color"), 0.9f, 0.9f, 0.9f, defaultAlpha);
        float ttl = (float) num(cfg, "ttl", 0.0);
        boolean dt = bool(cfg, "depthTest", this.defaultDepthTest);

        Vector3f prev = parseVec3(pts.getArrayElement(0), 0, 0, 0);
        Vector3f first = prev.clone();

        for (int i = 1; i < (int) n; i++) {
            Vector3f cur = parseVec3(pts.getArrayElement(i), prev.x, prev.y, prev.z);
            addLine(prev, cur, col, ttl, dt);
            prev = cur;
        }

        if (closed) addLine(prev, first, col, ttl, dt);
    }

    /**
     * Simple grid on XZ plane.
     * <p>
     * cfg:
     * - center: vec3
     * - halfSize: number (default 5)
     * - step: number (default 1)
     * - colorMajor, colorMinor, ttl, depthTest
     * - majorEvery: int (default 5)
     */
    @HostAccess.Export
    public void grid(Value cfg) {
        if (!enabled) return;
        if (cfg == null || cfg.isNull()) return;

        Vector3f center = parseVec3(cfg.getMember("center"), 0, 0, 0);
        float half = (float) num(cfg, "halfSize", 5.0);
        float step = (float) num(cfg, "step", 1.0);
        if (!(step > 1e-6f)) step = 1.0f;

        int majorEvery = (int) Math.max(1, (int) num(cfg, "majorEvery", 5.0));
        ColorRGBA cMinor = parseColor(cfg.getMember("colorMinor"), 0.25f, 0.35f, 0.45f, 0.75f);
        ColorRGBA cMajor = parseColor(cfg.getMember("colorMajor"), 0.55f, 0.65f, 0.75f, 0.9f);
        float ttl = (float) num(cfg, "ttl", 0.0);
        boolean dt = bool(cfg, "depthTest", this.defaultDepthTest);

        int linesEachSide = (int) Math.floor(half / step);
        float y = center.y;

        for (int i = -linesEachSide; i <= linesEachSide; i++) {
            boolean major = (i % majorEvery) == 0;
            ColorRGBA col = major ? cMajor : cMinor;

            float x = center.x + i * step;
            float z0 = center.z - half;
            float z1 = center.z + half;
            addLine(new Vector3f(x, y, z0), new Vector3f(x, y, z1), col, ttl, dt);

            float z = center.z + i * step;
            float x0 = center.x - half;
            float x1 = center.x + half;
            addLine(new Vector3f(x0, y, z), new Vector3f(x1, y, z), col, ttl, dt);
        }
    }

    private void addLine(Vector3f a, Vector3f b, ColorRGBA c, float ttl, boolean depthTest) {
        float expire = (ttl > 0f) ? (timeSec + ttl) : 0f;
        cmds.add(new LineCmd(
                a.x, a.y, a.z,
                b.x, b.y, b.z,
                c.r, c.g, c.b, c.a,
                expire,
                depthTest
        ));
        dirty = 1;
    }

    private void addArrow(Vector3f tip, Vector3f dirN, ColorRGBA c, float ttl, float size, boolean depthTest) {
        size = Math.max(0.01f, size);

        Vector3f up = Math.abs(dirN.y) < 0.95f ? Vector3f.UNIT_Y : Vector3f.UNIT_X;
        Vector3f right = dirN.cross(up).normalizeLocal();
        Vector3f back = dirN.negate();

        Vector3f p1 = tip.add(back.mult(size)).add(right.mult(size * 0.6f));
        Vector3f p2 = tip.add(back.mult(size)).add(right.mult(-size * 0.6f));

        addLine(tip, p1, c, ttl, depthTest);
        addLine(tip, p2, c, ttl, depthTest);
    }

    private void drawCircle(Vector3f center, Vector3f normalN, float radius, int segments, ColorRGBA col, float ttl, boolean depthTest) {
        radius = Math.max(0.0001f, radius);

        // Build orthonormal basis (u,v) in plane of circle.
        Vector3f n = normalN.clone();
        if (n.lengthSquared() < 1e-8f) n.set(0, 1, 0);
        n.normalizeLocal();

        Vector3f ref = (Math.abs(n.y) < 0.95f) ? Vector3f.UNIT_Y : Vector3f.UNIT_X;
        Vector3f u = n.cross(ref).normalizeLocal();
        Vector3f v = n.cross(u).normalizeLocal();

        float step = FastMath.TWO_PI / (float) segments;

        Vector3f prev = null;
        Vector3f first = null;

        for (int i = 0; i <= segments; i++) {
            float a = i * step;
            float ca = FastMath.cos(a);
            float sa = FastMath.sin(a);

            Vector3f p = center.add(u.mult(radius * ca)).add(v.mult(radius * sa));

            if (i == 0) {
                first = p;
                prev = p;
            } else {
                addLine(prev, p, col, ttl, depthTest);
                prev = p;
            }
        }

        // Close (in case of numeric drift)
        if (first != null && prev != null) addLine(prev, first, col, ttl, depthTest);
    }

    private void rebuildMeshesIfDirty() {
        if (dirty == 0) return;
        dirty = 0;

        int n = cmds.size();
        if (n <= 0) {
            clearMesh(meshDepth);
            clearMesh(meshNoDepth);
            return;
        }

        // Count per layer
        int nDepth = 0;
        int nNoDepth = 0;
        for (int i = 0; i < n; i++) {
            if (cmds.get(i).depthTest) nDepth++;
            else nNoDepth++;
        }

        // Build each mesh
        buildMesh(meshDepth, true, nDepth);
        buildMesh(meshNoDepth, false, nNoDepth);

        ensureAttached();
    }

    private void clearMesh(Mesh m) {
        m.clearBuffer(VertexBuffer.Type.Position);
        m.clearBuffer(VertexBuffer.Type.Color);
        m.updateBound();
        m.updateCounts();

        // keep dynamic flag
        m.setStatic();
        m.setDynamic();
    }

    private void buildMesh(Mesh m, boolean depthLayer, int lineCount) {
        if (lineCount <= 0) {
            clearMesh(m);
            return;
        }

        int verts = lineCount * 2;
        int posFloats = verts * 3;
        int colFloats = verts * 4;

        FloatBuffer pb = depthLayer ? ensurePosDepth(posFloats) : ensurePosNoDepth(posFloats);
        FloatBuffer cb = depthLayer ? ensureColDepth(colFloats) : ensureColNoDepth(colFloats);

        pb.clear();
        cb.clear();

        for (int i = 0; i < cmds.size(); i++) {
            LineCmd c = cmds.get(i);
            if (c.depthTest != depthLayer) continue;

            pb.put(c.ax).put(c.ay).put(c.az);
            pb.put(c.bx).put(c.by).put(c.bz);

            cb.put(c.r).put(c.g).put(c.b).put(c.a);
            cb.put(c.r).put(c.g).put(c.b).put(c.a);
        }

        pb.flip();
        cb.flip();

        m.setBuffer(VertexBuffer.Type.Position, 3, pb);
        m.setBuffer(VertexBuffer.Type.Color, 4, cb);

        m.updateBound();
        m.updateCounts();
    }

    private FloatBuffer ensurePosDepth(int floats) {
        if (posDepth == null || posDepth.capacity() < floats) posDepth = BufferUtils.createFloatBuffer(floats);
        return posDepth;
    }

    // --------------------------------------------------------------------
    // Parsers (JS-friendly)
    // --------------------------------------------------------------------

    private FloatBuffer ensureColDepth(int floats) {
        if (colDepth == null || colDepth.capacity() < floats) colDepth = BufferUtils.createFloatBuffer(floats);
        return colDepth;
    }

    private FloatBuffer ensurePosNoDepth(int floats) {
        if (posNoDepth == null || posNoDepth.capacity() < floats) posNoDepth = BufferUtils.createFloatBuffer(floats);
        return posNoDepth;
    }

    private FloatBuffer ensureColNoDepth(int floats) {
        if (colNoDepth == null || colNoDepth.capacity() < floats) colNoDepth = BufferUtils.createFloatBuffer(floats);
        return colNoDepth;
    }

    // --------------------------------------------------------------------
    // Data
    // --------------------------------------------------------------------

    private record LineCmd(
            float ax, float ay, float az,
            float bx, float by, float bz,
            float r, float g, float b, float a,
            float expireAt,
            boolean depthTest
    ) {
    }
}
