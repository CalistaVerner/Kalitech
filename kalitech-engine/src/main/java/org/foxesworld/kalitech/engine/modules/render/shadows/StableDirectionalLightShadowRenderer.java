// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/StableDirectionalLightShadowRenderer.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.texture.FrameBuffer;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadows.filters.ShadowSnapperFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.filters.SplitHysteresisFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.filters.StableCascadeFitterFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowPipeline;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Stable Cascaded Shadow Maps with modular pipeline (CDPR-style).
 * <p>
 * Pipeline order:
 * beginFrame
 * - split compute -> beforeSplits -> afterSplits
 * - per cascade:
 * beforeCascade
 * raw fit (sphere) -> afterFit (stable fit / quant / z-fit)
 * place camera (stable basis) -> snap via ShadowSnapperFilter -> afterSnap
 * endFrame
 */
public class StableDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer
        implements ShadowRenderer, ShadowTunable {

    private Logger log;
    private boolean dbgEnabled = false;
    private int dbgEveryFrames = 60;
    private long dbgFrame = 0;

    // pipeline
    private final ShadowPipeline pipeline = new ShadowPipeline();
    private final ShadowFrameContext frameCtx = new ShadowFrameContext();

    // stable components
    private final StableLightBasis stableBasis;
    private final ShadowSnapper snapper;

    // default baseline filters (installed by default)
    private final SplitHysteresisFilter hysteresisFilter = new SplitHysteresisFilter();
    private final StableCascadeFitterFilter fitterFilter = new StableCascadeFitterFilter();
    private final ShadowSnapperFilter snapperFilter;
    private final Vector4f splitFars4 = new Vector4f();
    private float backOffset = 1.10f;
    private float minNear = 1.0f;
    // temps
    private final Vector3f lightDir = new Vector3f();
    private float shadowSlopeBias = 2.0f;
    private float shadowNormalOffset = 0.0f;
    private final Matrix3f basis = new Matrix3f();
    private float cascadeBlendLen = 1.5f;
    private final Vector3f axisRight = new Vector3f();
    private final Vector3f axisUp = new Vector3f();
    private final Vector3f axisDir = new Vector3f();
    private final Vector3f camPos = new Vector3f();
    private final Vector3f camDir = new Vector3f();
    private final Vector3f camUp = new Vector3f();
    private final Vector3f camLeft = new Vector3f();
    private final Vector3f sphereCenter = new Vector3f();
    private final Vector3f tmp = new Vector3f();
    // tunables
    private boolean snapEnabled = true;
    // bias knobs
    private float shadowBias = 0.0008f;
    // cascade blend uniforms
    private boolean cascadeBlendEnabled = true;
    // fixed split distances (optional)
    private float[] fixedSplitDistances = null;
    // cached reflection access
    private volatile Camera[] shadowCamsCached = null;
    // dt helper
    private long lastNs = 0L;
    // external input
    private float cameraSpeed = 0f;

    public StableDirectionalLightShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
        super(assets, shadowMapSize, nbSplits);

        this.stableBasis = new StableLightBasis(Math.max(1, nbSplits), null);
        this.snapper = new ShadowSnapper(shadowMapSize);
        this.snapperFilter = new ShadowSnapperFilter(this.snapper);

        // Default CDPR baseline pipeline
        pipeline.add(hysteresisFilter);
        pipeline.add(fitterFilter);
        pipeline.add(snapperFilter);
    }

    // ---------------- pipeline API ----------------

    private static boolean isValidSplitArray(float[] a) {
        if (a == null || a.length == 0) return false;
        for (float v : a) if (!(v > 0f)) return false;
        return true;
    }

    private static float fovToRadians(float fov) {
        if (fov > FastMath.PI) return fov * FastMath.DEG_TO_RAD;
        return fov;
    }

    private static void normLocal(Vector3f v) {
        float x = v.x, y = v.y, z = v.z;
        float len2 = x * x + y * y + z * z;
        if (len2 <= 1e-20f) return;
        float inv = FastMath.invSqrt(len2);
        v.x = x * inv;
        v.y = y * inv;
        v.z = z * inv;
    }

    private static Camera[] tryGetShadowCamsByReflection(Object self) {
        try {
            Class<?> c = self.getClass();
            while (c != null && c != Object.class) {
                for (String n : new String[]{"shadowCam", "shadowCams", "shadowCameras"}) {
                    try {
                        Field f = c.getDeclaredField(n);
                        f.setAccessible(true);
                        Object v = f.get(self);
                        if (v instanceof Camera[]) return (Camera[]) v;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ---------------- ShadowTunable ----------------

    @Override
    public void setDebug(Logger log, boolean enabled, int everyFrames) {
        this.log = log;
        this.dbgEnabled = enabled;
        this.dbgEveryFrames = Math.max(1, everyFrames);
    }

    private static FrameBuffer[] tryGetShadowFbsByReflection(Object self) {
        try {
            Class<?> c = self.getClass();
            while (c != null && c != Object.class) {
                for (String n : new String[]{"shadowFB", "shadowFbs", "shadowFBOs", "shadowFbo", "shadowFramebuffers"}) {
                    try {
                        Field f = c.getDeclaredField(n);
                        f.setAccessible(true);
                        Object v = f.get(self);
                        if (v instanceof FrameBuffer[]) return (FrameBuffer[]) v;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String f3(float v) {
        return String.format("%.3f", v);
    }

    @Override
    public void setShadowBias(float bias) {
        this.shadowBias = Math.max(0f, bias);
    }

    @Override
    public void setShadowSlopeBias(float slopeBias) {
        this.shadowSlopeBias = Math.max(0f, slopeBias);
    }

    @Override
    public void setShadowNormalOffset(float normalOffset) {
        this.shadowNormalOffset = Math.max(0f, normalOffset);
    }

    @Override
    public void setCascadeBlendEnabled(boolean enabled) {
        this.cascadeBlendEnabled = enabled;
    }

    @Override
    public void setCascadeBlendLength(float len) {
        this.cascadeBlendLen = Math.max(0f, len);
    }

    public StableDirectionalLightShadowRenderer addFilter(ShadowFilter f) {
        pipeline.add(f);
        return this;
    }

    public StableDirectionalLightShadowRenderer removeFilter(ShadowFilter f) {
        pipeline.remove(f);
        return this;
    }

    public void clearFilters(boolean keepBaseline) {
        pipeline.clear();
        if (keepBaseline) {
            pipeline.add(hysteresisFilter);
            pipeline.add(fitterFilter);
            pipeline.add(snapperFilter);
        }
    }

    public ShadowPipeline pipeline() {
        return pipeline;
    }

    @Override public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
    }

    @Override
    public void setExtentsPadding(float padding) {
        fitterFilter.extentsPadding = Math.max(1.0f, padding);
    }

    @Override
    public void setSplitDistances(float... distances) {
        if (distances == null || distances.length == 0) {
            fixedSplitDistances = null;
            hysteresisFilter.resetHistory();
            return;
        }
        fixedSplitDistances = distances.clone();
        Arrays.sort(fixedSplitDistances);
        hysteresisFilter.resetHistory();
    }

    // ---------------- material params ----------------

    @Override
    protected void setMaterialParameters(Material material) {
        super.setMaterialParameters(material);
        if (material == null) return;

        if (material.getParam("ShadowBias") != null) material.setFloat("ShadowBias", shadowBias);
        if (material.getParam("ShadowSlopeBias") != null) material.setFloat("ShadowSlopeBias", shadowSlopeBias);
        if (material.getParam("ShadowNormalOffset") != null)
            material.setFloat("ShadowNormalOffset", shadowNormalOffset);

        if (material.getParam("CascadeBlendEnabled") != null)
            material.setBoolean("CascadeBlendEnabled", cascadeBlendEnabled);
        if (material.getParam("CascadeBlendLen") != null) material.setFloat("CascadeBlendLen", cascadeBlendLen);

        if (material.getParam("ShadowSplitFars") != null) material.setVector4("ShadowSplitFars", splitFars4);
    }

    @Override
    protected void clearMaterialParameters(Material material) {
        super.clearMaterialParameters(material);
        if (material == null) return;

        if (material.getParam("ShadowBias") != null) material.clearParam("ShadowBias");
        if (material.getParam("ShadowSlopeBias") != null) material.clearParam("ShadowSlopeBias");
        if (material.getParam("ShadowNormalOffset") != null) material.clearParam("ShadowNormalOffset");

        if (material.getParam("CascadeBlendEnabled") != null) material.clearParam("CascadeBlendEnabled");
        if (material.getParam("CascadeBlendLen") != null) material.clearParam("CascadeBlendLen");

        if (material.getParam("ShadowSplitFars") != null) material.clearParam("ShadowSplitFars");
    }

    // ---------------- hard clear ----------------

    // Extra knobs (still accessible)
    public void setBackOffset(float backOffset) {
        this.backOffset = Math.max(0.5f, backOffset);
    }

    // ---------------- core: orchestrated pipeline ----------------

    public void setMinNear(float minNear) {
        this.minNear = Math.max(0.01f, minNear);
    }

    // ---------------- split compute ----------------

    public void setZPadding(float zPadding) {
        fitterFilter.zPadding = Math.max(0f, zPadding);
    }

    public void setMinZSpan(float minZSpan) {
        fitterFilter.minZSpan = Math.max(1f, minZSpan);
    }

    // ---------------- fit sphere ----------------

    public void setQuantTexels(float quantTexels) {
        fitterFilter.quantTexels = Math.max(0f, quantTexels);
    }

    // ---------------- helpers / reflection ----------------

    public void setCameraSpeed(float worldUnitsPerSecond) {
        this.cameraSpeed = Math.max(0f, worldUnitsPerSecond);
    }

    @Override
    public void clearShadows(RenderManager rm, ViewPort vp) {
        FrameBuffer[] fbs = tryGetShadowFbsByReflection(this);
        if (fbs == null || fbs.length == 0) return;
        if (rm == null) return;
        Renderer r = rm.getRenderer();
        if (r == null) return;

        FrameBuffer prev = r.getCurrentFrameBuffer();
        try {
            for (FrameBuffer fb : fbs) {
                if (fb == null) continue;
                r.setFrameBuffer(fb);
                r.clearBuffers(false, true, false);
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                r.setFrameBuffer(prev);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void updateShadowCams(Camera viewCam) {
        super.updateShadowCams(viewCam);

        final DirectionalLight dl = getLight();
        if (dl == null || viewCam == null) return;

        long now = System.nanoTime();
        float dt = 1f / 60f;
        if (lastNs != 0L) {
            dt = (now - lastNs) * 1e-9f;
            dt = FastMath.clamp(dt, 1e-4f, 0.1f);
        }
        lastNs = now;

        Camera[] cams = shadowCamsCached;
        if (cams == null) {
            cams = tryGetShadowCamsByReflection(this);
            shadowCamsCached = cams;
        }
        if (cams == null || cams.length == 0) return;

        // view camera basis
        camPos.set(viewCam.getLocation());
        camDir.set(viewCam.getDirection());
        normLocal(camDir);
        camUp.set(viewCam.getUp());
        normLocal(camUp);
        camLeft.set(viewCam.getLeft());
        normLocal(camLeft);

        float vNear = viewCam.getFrustumNear();
        float vFar = viewCam.getFrustumFar();
        if (!(vFar > vNear) || !(vNear > 0f)) return;

        lightDir.set(dl.getDirection());
        normLocal(lightDir);

        final boolean emit = dbgEnabled && log != null && log.isDebugEnabled()
                && ((dbgFrame++ % (long) dbgEveryFrames) == 0L);

        // init frame context
        frameCtx.ensure(cams.length);
        frameCtx.viewCam = viewCam;
        frameCtx.light = dl;
        frameCtx.dt = dt;
        frameCtx.cameraSpeed = cameraSpeed;
        frameCtx.viewNear = vNear;
        frameCtx.viewFar = vFar;
        frameCtx.mapSize = snapper.getShadowMapSize();
        frameCtx.lightDir.set(lightDir);

        pipeline.beginFrame(frameCtx);

        // 1) compute split fars wanted
        computeSplitsWanted(frameCtx);

        // 2) allow filters to stabilize / override split fars
        pipeline.beforeSplits(frameCtx);

        // if nobody wrote final splits, default to wanted
        if (!isValidSplitArray(frameCtx.splitFarsFinal)) {
            System.arraycopy(frameCtx.splitFarsWanted, 0, frameCtx.splitFarsFinal, 0, frameCtx.cascades);
        }

        pipeline.afterSplits(frameCtx);

        // 3) per cascade
        for (int i = 0; i < cams.length; i++) {
            Camera sc = cams[i];
            if (sc == null) continue;

            float cNear = (i == 0) ? Math.max(minNear, vNear) : Math.max(minNear, frameCtx.splitFarsFinal[i - 1]);
            float cFar = Math.max(cNear + 0.001f, frameCtx.splitFarsFinal[i]);

            ShadowFrameContext.CascadeData cd = frameCtx.c[i];
            cd.rangeNear = cNear;
            cd.rangeFar = cFar;

            pipeline.beforeCascade(frameCtx, i);

            // raw fit (sphere around frustum slice)
            float fovY = fovToRadians(viewCam.getFov());
            float rawRadius = fitCascadeSphere(fovY, viewCam.getAspect(), cNear, cFar, sphereCenter);

            cd.centerWS.set(sphereCenter);
            cd.radius = rawRadius;
            cd.zNearRel = -rawRadius;
            cd.zFarRel = +rawRadius;
            cd.quantized = false;
            cd.snapped = false;

            // stable fit / quant / z-fit via filters
            pipeline.afterFit(frameCtx, i);

            // stable basis for this cascade (deterministic)
            stableBasis.computeBasis(i, lightDir, dt, basis);
            frameCtx.basis.set(basis);

            basis.getColumn(0, axisRight);
            basis.getColumn(1, axisUp);
            basis.getColumn(2, axisDir);

            // place shadow camera from fitted data
            float radius = Math.max(0.001f, cd.radius);
            Vector3f center = cd.centerWS;

            Vector3f camLoc = tmp.set(axisDir).multLocal(-radius * backOffset).addLocal(center);

            float distToCenter = radius * backOffset;
            float near = distToCenter + cd.zNearRel;
            float far = distToCenter + cd.zFarRel;

            if (near < minNear) near = minNear;
            if (far <= near + 0.001f) far = near + 0.001f;

            sc.setParallelProjection(true);
            sc.setLocation(camLoc);
            sc.setAxes(axisRight.negate(), axisUp, axisDir);
            sc.setFrustum(near, far, -radius, radius, radius, -radius);

            // snap (baseline snapper filter owns the snapping call)
            boolean snapped = false;
            if (snapEnabled) {
                snapped = snapperFilter.snap(i, sc, frameCtx, dt);
            } else {
                sc.update();
            }
            cd.snapped = snapped;

            pipeline.afterSnap(frameCtx, i);

            if (emit) {
                log.debug("[shadow][pipe] split={} range=[{}..{}] r={} quant={} snapped={}",
                        i, f3(cNear), f3(cFar), f3(radius), cd.quantized, snapped);
            }
        }

        pipeline.endFrame(frameCtx);

        // export split far vector to shader
        float[] sf = frameCtx.splitFarsFinal;
        splitFars4.set(
                (sf.length > 0) ? sf[0] : vFar,
                (sf.length > 1) ? sf[1] : vFar,
                (sf.length > 2) ? sf[2] : vFar,
                (sf.length > 3) ? sf[3] : vFar
        );
    }

    private void computeSplitsWanted(ShadowFrameContext ctx) {
        int n = ctx.cascades;
        float near = ctx.viewNear;
        float far = ctx.viewFar;

        if (fixedSplitDistances != null && fixedSplitDistances.length >= n) {
            for (int i = 0; i < n; i++) ctx.splitFarsWanted[i] = Math.max(near, fixedSplitDistances[i]);
            // default: if filters don't override, copy to final
            System.arraycopy(ctx.splitFarsWanted, 0, ctx.splitFarsFinal, 0, n);
            return;
        }

        float lambda = 0.65f;
        try {
            lambda = getLambda();
        } catch (Throwable ignored) {
        }

        float N = Math.max(minNear, near);
        float F = Math.max(N + 0.001f, far);

        float range = F - N;
        float ratio = F / N;

        for (int i = 0; i < n; i++) {
            float p = (i + 1f) / (float) n;
            float log = N * (float) Math.pow(ratio, p);
            float lin = N + range * p;
            ctx.splitFarsWanted[i] = FastMath.interpolateLinear(FastMath.clamp(lambda, 0f, 1f), lin, log);
        }

        // default: if filters don't override, copy to final
        System.arraycopy(ctx.splitFarsWanted, 0, ctx.splitFarsFinal, 0, n);
    }

    private float fitCascadeSphere(float fovY, float aspect, float sliceNear, float sliceFar, Vector3f outCenter) {
        float tanY = FastMath.tan(0.5f * fovY);
        float tanX = tanY * aspect;

        // centers of near/far planes
        Vector3f cn = tmp.set(camDir).multLocal(sliceNear).addLocal(camPos);
        Vector3f cf = new Vector3f(camDir).multLocal(sliceFar).addLocal(camPos);

        float nh = sliceNear * tanY;
        float nw = sliceNear * tanX;
        float fh = sliceFar * tanY;
        float fw = sliceFar * tanX;

        // 8 corners (no allocations other than the one "cf" above)
        Vector3f[] c = new Vector3f[8];
        for (int i = 0; i < 8; i++) c[i] = new Vector3f();

        // near
        c[0].set(cn).addLocal(camUp.x * nh + camLeft.x * nw, camUp.y * nh + camLeft.y * nw, camUp.z * nh + camLeft.z * nw);
        c[1].set(cn).addLocal(camUp.x * nh - camLeft.x * nw, camUp.y * nh - camLeft.y * nw, camUp.z * nh - camLeft.z * nw);
        c[2].set(cn).addLocal(-camUp.x * nh - camLeft.x * nw, -camUp.y * nh - camLeft.y * nw, -camUp.z * nh - camLeft.z * nw);
        c[3].set(cn).addLocal(-camUp.x * nh + camLeft.x * nw, -camUp.y * nh + camLeft.y * nw, -camUp.z * nh + camLeft.z * nw);

        // far
        c[4].set(cf).addLocal(camUp.x * fh + camLeft.x * fw, camUp.y * fh + camLeft.y * fw, camUp.z * fh + camLeft.z * fw);
        c[5].set(cf).addLocal(camUp.x * fh - camLeft.x * fw, camUp.y * fh - camLeft.y * fw, camUp.z * fh - camLeft.z * fw);
        c[6].set(cf).addLocal(-camUp.x * fh - camLeft.x * fw, -camUp.y * fh - camLeft.y * fw, -camUp.z * fh - camLeft.z * fw);
        c[7].set(cf).addLocal(-camUp.x * fh + camLeft.x * fw, -camUp.y * fh + camLeft.y * fw, -camUp.z * fh + camLeft.z * fw);

        // sphere center = AABB center
        Vector3f min = new Vector3f(c[0]);
        Vector3f max = new Vector3f(c[0]);
        for (int i = 1; i < 8; i++) {
            Vector3f p = c[i];
            if (p.x < min.x) min.x = p.x;
            if (p.y < min.y) min.y = p.y;
            if (p.z < min.z) min.z = p.z;
            if (p.x > max.x) max.x = p.x;
            if (p.y > max.y) max.y = p.y;
            if (p.z > max.z) max.z = p.z;
        }

        outCenter.set(min).addLocal(max).multLocal(0.5f);

        float r = 0f;
        for (int i = 0; i < 8; i++) {
            float d = c[i].distance(outCenter);
            if (d > r) r = d;
        }
        return Math.max(0.001f, r);
    }
}