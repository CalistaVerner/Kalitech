// FILE: engine/api/impl/EditorLinesApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.control.AbstractControl;
import com.jme3.util.BufferUtils;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.EditorLinesApi;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.Future;

/**
 * Editor lines / grids.
 *
 * Goals:
 *  - thick lines as quad strips (stable across drivers)
 *  - NOT visible through objects (depth test + depth write ON, Opaque bucket)
 *  - "infinite" feel: grid patch recenters in XZ rarely (snapped), Y always below camera by yOffset
 *  - no per-frame jitter: only recenter when far enough from current patch center
 */
public final class EditorLinesApiImpl implements EditorLinesApi {

    private final EngineApiImpl engine;
    private final Logger log;
    private final SurfaceRegistry surfaces;

    public EditorLinesApiImpl(EngineApiImpl engine, SurfaceRegistry surfaces) {
        this.engine = engine;
        this.log = engine.getLog();
        this.surfaces = surfaces;
    }

    @HostAccess.Export
    @Override
    public SurfaceApi.SurfaceHandle createGridPlane(Value cfg) {
        // ----- cfg -----
        // patch size: halfExtent in world units (NOT "world size"). This is the size of the repeating patch.
        final double halfExtentD = num(cfg, "halfExtent", num(cfg, "size", 600.0));
        final double stepD = num(cfg, "step", 1.0);

        // Instead of fixed world y: keep grid always below camera by yOffset.
        // If user still provides "y" explicitly, treat it as additional offset (fine-tune).
        final double yOffsetD = num(cfg, "yOffset", 60.0);
        final double yAddD = num(cfg, "y", 0.0);

        // Opacity with depth-write tends to look wrong; keep alpha at 1 and let user darken colors.
        // We'll still accept opacity but clamp it and apply to alpha (can be useful if you want subtle grid).
        final double opacityD = num(cfg, "opacity", 1.0);

        final int majorStep = clamp((int) Math.round(num(cfg, "majorStep", 10.0)), 1, 1_000_000);

        // Colors (dark by default)
        final float minorR = (float) clamp(numPath(cfg, "minorColor", "r", 0.03), 0.0, 1.0);
        final float minorG = (float) clamp(numPath(cfg, "minorColor", "g", 0.035), 0.0, 1.0);
        final float minorB = (float) clamp(numPath(cfg, "minorColor", "b", 0.045), 0.0, 1.0);

        final float majorR = (float) clamp(numPath(cfg, "majorColor", "r", 0.07), 0.0, 1.0);
        final float majorG = (float) clamp(numPath(cfg, "majorColor", "g", 0.085), 0.0, 1.0);
        final float majorB = (float) clamp(numPath(cfg, "majorColor", "b", 0.12), 0.0, 1.0);

        // Thickness in WORLD units
        final float minorTh = (float) clamp(num(cfg, "minorThickness", 0.035), 0.0005, 10.0);
        final float majorTh = (float) clamp(num(cfg, "majorThickness", 0.09), 0.0005, 10.0);

        // Infinite behavior
        final boolean followCamera = bool(cfg, "followCamera", true);   // re-center patch in XZ when far
        final boolean snapToStep = bool(cfg, "snapToStep", true);       // snap XZ recenter to step
        final boolean snapY = bool(cfg, "snapY", false);                // usually false (avoid "breathing")
        final double recenterFracD = num(cfg, "recenterFrac", 0.40);    // 0.2..0.9

        final float halfExtent = (float) clamp(halfExtentD, 1.0, 500_000.0);
        final float step = (float) clamp(stepD, 0.01, 100_000.0);

        // yOffset: always below camera by this amount
        final float yOffset = (float) clamp(yOffsetD, 0.1, 500_000.0);
        final float yAdd = (float) clamp(yAddD, -500_000.0, 500_000.0);

        final float opacity = (float) clamp(opacityD, 0.0, 1.0);
        final float recenterFrac = (float) clamp(recenterFracD, 0.20, 0.90);

        // Convert halfExtent to line count
        int halfLines = Math.max(1, Math.round(halfExtent / step));
        // Keep sane; triangles scale fast. You can raise if you need.
        halfLines = clamp(halfLines, 1, 50_000);

        final int HL = halfLines;
        final float worldHalf = HL * step;

        try {
            if (engine.isJmeThread()) {
                return createOnJme(
                        HL, worldHalf, step,
                        opacity,
                        minorR, minorG, minorB, minorTh,
                        majorR, majorG, majorB, majorTh,
                        majorStep,
                        followCamera, snapToStep, snapY,
                        yOffset, yAdd,
                        recenterFrac
                );
            } else {
                Future<SurfaceApi.SurfaceHandle> f = engine.getApp().enqueue(() ->
                        createOnJme(
                                HL, worldHalf, step,
                                opacity,
                                minorR, minorG, minorB, minorTh,
                                majorR, majorG, majorB, majorTh,
                                majorStep,
                                followCamera, snapToStep, snapY,
                                yOffset, yAdd,
                                recenterFrac
                        )
                );
                return f.get();
            }
        } catch (Exception e) {
            throw new RuntimeException("EditorLines.createGridPlane failed: " + e, e);
        }
    }

    private SurfaceApi.SurfaceHandle createOnJme(
            int halfLines, float worldHalf, float step,
            float opacity,
            float minorR, float minorG, float minorB, float minorTh,
            float majorR, float majorG, float majorB, float majorTh,
            int majorStep,
            boolean followCamera, boolean snapToStep, boolean snapY,
            float yOffset, float yAdd,
            float recenterFrac
    ) {
        Node root = new Node("editor.grid.node");

        // Build meshes around local origin; control will reposition node.
        Mesh minor = buildGridQuadsMesh(halfLines, worldHalf, step, 0f, minorTh, majorStep, false);
        Mesh major = buildGridQuadsMesh(halfLines, worldHalf, step, 0f, majorTh, majorStep, true);

        // OPAQUE + depth write ON => grid does NOT show through objects.
        // If you need transparency, keep alpha but then sorting may appear; recommended: dark colors.
        Material mMinor = new Material(engine.getAssets(), "Common/MatDefs/Misc/Unshaded.j3md");
        mMinor.setColor("Color", new ColorRGBA(minorR, minorG, minorB, opacity));
        RenderState rs0 = mMinor.getAdditionalRenderState();
        rs0.setBlendMode(RenderState.BlendMode.Off);
        rs0.setDepthTest(true);
        rs0.setDepthWrite(true);
        rs0.setFaceCullMode(RenderState.FaceCullMode.Off);

        Material mMajor = new Material(engine.getAssets(), "Common/MatDefs/Misc/Unshaded.j3md");
        mMajor.setColor("Color", new ColorRGBA(majorR, majorG, majorB, opacity));
        RenderState rs1 = mMajor.getAdditionalRenderState();
        rs1.setBlendMode(RenderState.BlendMode.Off);
        rs1.setDepthTest(true);
        rs1.setDepthWrite(true);
        rs1.setFaceCullMode(RenderState.FaceCullMode.Off);

        Geometry gMinor = new Geometry("editor.grid.minor", minor);
        gMinor.setMaterial(mMinor);
        gMinor.setQueueBucket(RenderQueue.Bucket.Opaque);

        Geometry gMajor = new Geometry("editor.grid.major", major);
        gMajor.setMaterial(mMajor);
        gMajor.setQueueBucket(RenderQueue.Bucket.Opaque);

        root.attachChild(gMinor);
        root.attachChild(gMajor);

        // Behavior: keep under camera, and optionally "infinite" by recentering patch in XZ rarely
        if (followCamera) {
            root.addControl(new UnderCameraGridControl(engine, step, snapToStep, snapY, worldHalf, yOffset, yAdd, recenterFrac));
        } else {
            // static: just put it at some default "below camera" at creation time
            Vector3f cam = engine.getApp().getCamera().getLocation();
            root.setLocalTranslation(cam.x, cam.y - yOffset + yAdd, cam.z);
        }

        // Register in SurfaceRegistry (NEW signature: pass api explicitly, no legacy bind)
        SurfaceApi.SurfaceHandle h = surfaces.register(root, "editor.grid", engine.surface());
        surfaces.attachToRoot(h.id());

        log.info("EditorLines: grid created handle={} patchHalf={} step={} majorStep={} minorTh={} majorTh={} followCamera={} yOffset={}",
                h.id(), worldHalf, step, majorStep, minorTh, majorTh, followCamera, yOffset);

        return h;
    }

    /**
     * Keeps the grid always under camera:
     *  - Y each update: camY - yOffset (+ yAdd)
     *  - XZ: recenters only when camera drifts far from current patch center (reduces "following" feel)
     */
    private static final class UnderCameraGridControl extends AbstractControl {
        private final EngineApiImpl engine;
        private final float step;
        private final boolean snapXZ;
        private final boolean snapY;
        private final float worldHalf;
        private final float yOffset;
        private final float yAdd;
        private final float recenterDist;

        private float cx;
        private float cz;

        UnderCameraGridControl(EngineApiImpl engine,
                               float step,
                               boolean snapXZ,
                               boolean snapY,
                               float worldHalf,
                               float yOffset,
                               float yAdd,
                               float recenterFrac) {
            this.engine = engine;
            this.step = step;
            this.snapXZ = snapXZ;
            this.snapY = snapY;
            this.worldHalf = worldHalf;
            this.yOffset = Math.max(0.01f, yOffset);
            this.yAdd = yAdd;
            float frac = clamp(recenterFrac, 0.20f, 0.90f);
            this.recenterDist = Math.max(10f, worldHalf * frac);
            this.cx = Float.NaN;
            this.cz = Float.NaN;
        }

        @Override
        protected void controlUpdate(float tpf) {
            if (spatial == null) return;

            Vector3f cam = engine.getApp().getCamera().getLocation();

            // --- Y always under camera ---
            float y = cam.y - yOffset + yAdd;
            if (snapY && step > 1e-6f) {
                y = (float) (Math.round(y / step) * step);
            }

            // init center at first tick
            if (Float.isNaN(cx) || Float.isNaN(cz)) {
                cx = cam.x;
                cz = cam.z;
                if (snapXZ && step > 1e-6f) {
                    cx = (float) (Math.round(cx / step) * step);
                    cz = (float) (Math.round(cz / step) * step);
                }
                spatial.setLocalTranslation(cx, y, cz);
                return;
            }

            // --- XZ: recenter rarely to avoid "it follows me" feeling ---
            boolean need = (Math.abs(cam.x - cx) > recenterDist) || (Math.abs(cam.z - cz) > recenterDist);
            if (need) {
                float nx = cam.x;
                float nz = cam.z;
                if (snapXZ && step > 1e-6f) {
                    nx = (float) (Math.round(nx / step) * step);
                    nz = (float) (Math.round(nz / step) * step);
                }
                cx = nx;
                cz = nz;
                spatial.setLocalTranslation(cx, y, cz);
            } else {
                // keep same XZ, only update Y
                Vector3f p = spatial.getLocalTranslation();
                if (p.y != y) spatial.setLocalTranslation(p.x, y, p.z);
            }
        }

        @Override
        protected void controlRender(RenderManager rm, ViewPort vp) {
        }
    }

    @HostAccess.Export
    @Override
    public void destroy(Object handle) {
        if (handle == null) return;

        Runnable r = () -> {
            int id = handleId(handle);
            surfaces.destroy(id);
            log.info("EditorLines: destroyed handle={}", id);
        };

        if (engine.isJmeThread()) r.run();
        else engine.getApp().enqueue(() -> { r.run(); return null; });
    }

    private int handleId(Object handle) {
        if (handle instanceof SurfaceApi.SurfaceHandle h) return h.id();
        if (handle instanceof Number n) return n.intValue();
        throw new IllegalArgumentException("EditorLines.destroy: invalid handle type: " + handle.getClass().getName());
    }

    /**
     * Thick lines as quads (2 triangles).
     * y parameter is local-space y; we keep it 0 and position by control to avoid float jitter in vertices.
     */
    private static Mesh buildGridQuadsMesh(int halfLines, float worldHalf, float step, float y,
                                           float thickness, int majorStep, boolean majorOnly) {

        int countAxis = 0;
        for (int i = -halfLines; i <= halfLines; i++) {
            boolean isMajor = (i % majorStep) == 0;
            if (majorOnly ? isMajor : !isMajor) countAxis++;
        }

        int lineCount = countAxis * 2;
        int vCount = lineCount * 4;
        int iCount = lineCount * 6;

        FloatBuffer pos = BufferUtils.createFloatBuffer(vCount * 3);
        IntBuffer idx = BufferUtils.createIntBuffer(iCount);

        float halfT = thickness * 0.5f;
        int v = 0;

        // Lines parallel to Z (vary X), thickness along X
        for (int i = -halfLines; i <= halfLines; i++) {
            boolean isMajor = (i % majorStep) == 0;
            if (majorOnly ? !isMajor : isMajor) continue;

            float x = i * step;

            int v0 = v++;
            int v1 = v++;
            int v2 = v++;
            int v3 = v++;

            pos.put(x - halfT).put(y).put(-worldHalf);
            pos.put(x + halfT).put(y).put(-worldHalf);
            pos.put(x + halfT).put(y).put(+worldHalf);
            pos.put(x - halfT).put(y).put(+worldHalf);

            idx.put(v0).put(v1).put(v2);
            idx.put(v0).put(v2).put(v3);
        }

        // Lines parallel to X (vary Z), thickness along Z
        for (int i = -halfLines; i <= halfLines; i++) {
            boolean isMajor = (i % majorStep) == 0;
            if (majorOnly ? !isMajor : isMajor) continue;

            float z = i * step;

            int v0 = v++;
            int v1 = v++;
            int v2 = v++;
            int v3 = v++;

            pos.put(-worldHalf).put(y).put(z - halfT);
            pos.put(+worldHalf).put(y).put(z - halfT);
            pos.put(+worldHalf).put(y).put(z + halfT);
            pos.put(-worldHalf).put(y).put(z + halfT);

            idx.put(v0).put(v1).put(v2);
            idx.put(v0).put(v2).put(v3);
        }

        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Triangles);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, pos);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, idx);
        mesh.updateBound();
        return mesh;
    }

    // ---- cfg helpers ----

    private static Value member(Value v, String k) { return (v != null && v.hasMember(k)) ? v.getMember(k) : null; }

    private static boolean bool(Value v, String k, boolean def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    private static double num(Value v, String k, double def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asDouble();
        } catch (Exception e) {
            return def;
        }
    }

    private static double numPath(Value v, String k1, String k2, double def) {
        try {
            Value a = member(v, k1);
            if (a == null || a.isNull() || !a.hasMember(k2)) return def;
            Value b = a.getMember(k2);
            return (b == null || b.isNull()) ? def : b.asDouble();
        } catch (Exception e) {
            return def;
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}