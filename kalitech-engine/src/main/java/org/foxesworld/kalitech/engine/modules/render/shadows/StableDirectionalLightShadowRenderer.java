// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/StableDirectionalLightShadowRenderer.java
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

import java.lang.reflect.Field;
import java.util.Arrays;

public class StableDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer implements ShadowRenderer, ShadowTunable {

    private final ShadowSnapper snapper;
    private final ShadowSnapper.SnapResult snapResult = new ShadowSnapper.SnapResult();
    private final StableLightBasis stableBasis;
    private final SplitHysteresisManager hysteresis = new SplitHysteresisManager();
    private final StableCascadeFitter fitter = new StableCascadeFitter();
    private final ShadowCascadeMath cascadeMath = new ShadowCascadeMath();
    private final ShadowCascadeMath.CascadeFit fit = new ShadowCascadeMath.CascadeFit();
    private final Matrix3f basis = new Matrix3f();
    private final Vector3f axisRight = new Vector3f();
    private final Vector3f axisUp = new Vector3f();
    private final Vector3f axisDir = new Vector3f();
    private final Vector3f lightDir = new Vector3f();
    private Logger log;
    private boolean dbgEnabled = false;
    private int dbgEveryFrames = 60;
    private long dbgFrame = 0;

    private final Vector3f camPos = new Vector3f();
    private final Vector3f camDir = new Vector3f();
    private final Vector3f camUp = new Vector3f();
    private final Vector3f camLeft = new Vector3f();

    private final Vector3f tmp = new Vector3f();

    private final Vector4f splitFars4 = new Vector4f();
    private float[] fixedSplitDistances = null;
    private boolean snapEnabled = true;
    private float extentsPadding = 1.10f;
    private float backOffset = 1.10f;
    private float minNear = 1.0f;

    private float zPadding = 25.0f;
    private float minZSpan = 50.0f;
    private float quantTexels = 2.0f;

    private float shadowBias = 0.0008f;
    private float shadowSlopeBias = 2.0f;
    private float shadowNormalOffset = 0.0f;

    private boolean cascadeBlendEnabled = true;
    private float cascadeBlendLen = 1.5f;

    private float cameraSpeed = 0f;

    private long lastNs = 0L;
    private volatile Camera[] shadowCamsCached = null;

    public StableDirectionalLightShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
        super(assets, shadowMapSize, nbSplits);
        this.snapper = new ShadowSnapper(shadowMapSize);
        this.stableBasis = new StableLightBasis(Math.max(1, nbSplits), null);
    }

    // ---- ShadowTunable ----

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

    private static FrameBuffer[] tryGetShadowFbsByReflection(Object self) {
        try {
            Class<?> c = self.getClass();
            while (c != null && c != Object.class) {
                for (String n : new String[]{"shadowFB", "shadowFbs", "shadowFBOs", "shadowFramebuffers"}) {
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

    private static float maxAbs4(float a, float b, float c, float d) {
        float m = Math.abs(a);
        float x = Math.abs(b);
        if (x > m) m = x;
        x = Math.abs(c);
        if (x > m) m = x;
        x = Math.abs(d);
        if (x > m) m = x;
        return Math.max(0.001f, m);
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

    private static String f3(float v) {
        return String.format("%.3f", v);
    }

    private static String f6(float v) {
        return String.format("%.6f", v);
    }

    @Override
    public void setDebug(Logger log, boolean enabled, int everyFrames) {
        this.log = log;
        this.dbgEnabled = enabled;
        this.dbgEveryFrames = Math.max(1, everyFrames);
    }

    @Override
    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
    }

    @Override
    public void setExtentsPadding(float padding) {
        this.extentsPadding = Math.max(1.0f, padding);
    }

    @Override
    public void setShadowBias(float bias) {
        this.shadowBias = Math.max(0f, bias);
    }

    // ---- Material ----

    @Override
    public void setShadowSlopeBias(float slopeBias) {
        this.shadowSlopeBias = Math.max(0f, slopeBias);
    }

    @Override
    public void setShadowNormalOffset(float normalOffset) {
        this.shadowNormalOffset = Math.max(0f, normalOffset);
    }

    // ---- Core ----

    @Override
    public void setCascadeBlendEnabled(boolean enabled) {
        this.cascadeBlendEnabled = enabled;
    }

    @Override
    public void setCascadeBlendLength(float len) {
        this.cascadeBlendLen = Math.max(0f, len);
    }

    @Override
    public void setSplitDistances(float... distances) {
        if (distances == null || distances.length == 0) {
            this.fixedSplitDistances = null;
            hysteresis.reset();
            return;
        }
        this.fixedSplitDistances = distances.clone();
        Arrays.sort(this.fixedSplitDistances);
        hysteresis.reset();
    }

    // Optional: feed from engine
    public void setCameraSpeed(float speed) {
        this.cameraSpeed = Math.max(0f, speed);
    }

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

    @Override
    protected void updateShadowCams(Camera viewCam) {
        super.updateShadowCams(viewCam);

        final DirectionalLight dl = getLight();
        if (dl == null || viewCam == null) return;

        // dt (for basis stabilization & snapper)
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

        // 1) split fars (stable)
        float[] splitFars = computeStableSplits(vNear, vFar, cams.length, cameraSpeed);

        // 2) cascades
        for (int i = 0; i < cams.length; i++) {
            Camera sc = cams[i];
            if (sc == null) continue;

            float cNear = (i == 0) ? Math.max(minNear, vNear) : Math.max(minNear, splitFars[i - 1]);
            float cFar = Math.max(cNear + 0.001f, splitFars[i]);

            // stable basis
            stableBasis.computeBasis(i, lightDir, dt, basis);
            basis.getColumn(0, axisRight);
            basis.getColumn(1, axisUp);
            basis.getColumn(2, axisDir);

            // fit split in LIGHT space (robust, no sphere wobble)
            cascadeMath.fitSplit(viewCam, cNear, cFar, axisDir, fit);

            // square radius + padding
            float r0 = maxAbs4(fit.left, fit.right, fit.bottom, fit.top);
            r0 *= Math.max(1.0f, extentsPadding);

            // quantize radius in texel space (this is the big anti-shimmer piece)
            float texel0 = (2f * r0) / (float) Math.max(1, snapper.getShadowMapSize());
            float step = (quantTexels > 0f) ? (texel0 * quantTexels) : 0f;
            float r = (step > 0f) ? (float) (Math.ceil(r0 / step) * step) : r0;

            // recompute texel (after quantization)
            float texel = (2f * r) / (float) Math.max(1, snapper.getShadowMapSize());

            // stable z-fit (use split min/max z from fit, pad + min span)
            float z0 = fit.minZ - zPadding;
            float z1 = fit.maxZ + zPadding;
            float span = z1 - z0;
            if (span < minZSpan) {
                float mid = 0.5f * (z0 + z1);
                float half = 0.5f * minZSpan;
                z0 = mid - half;
                z1 = mid + half;
            }

            // place camera
            Vector3f center = fit.centerWorld;
            Vector3f camLoc = tmp.set(axisDir).multLocal(-(r * backOffset)).addLocal(center);

            float distToCenter = r * backOffset;
            float near = distToCenter + z0;
            float far = distToCenter + z1;
            if (near < minNear) near = minNear;
            if (far <= near + 0.001f) far = near + 0.001f;

            // ortho
            sc.setParallelProjection(true);
            sc.setLocation(camLoc);
            sc.setAxes(axisRight.negate(), axisUp, axisDir); // left=-right
            sc.setFrustum(near, far, -r, r, r, -r);

            boolean snapped = false;
            if (snapEnabled) {
                snapped = snapper.snap(i, sc, basis, dt, snapResult);
            } else {
                sc.update();
            }

            if (emit) {
                log.debug("[shadow][stable] split={} range=[{}..{}] r0={} r={} texel={} snapped={} td=({}, {})",
                        i, f3(cNear), f3(cFar),
                        f3(r0), f3(r), f6(texel),
                        snapped,
                        snapResult.texelDx, snapResult.texelDy
                );
            }
        }

        // export split fars
        splitFars4.set(
                (splitFars.length > 0) ? splitFars[0] : vFar,
                (splitFars.length > 1) ? splitFars[1] : vFar,
                (splitFars.length > 2) ? splitFars[2] : vFar,
                (splitFars.length > 3) ? splitFars[3] : vFar
        );
    }

    private float[] computeStableSplits(float near, float far, int n, float camSpeed) {
        if (n <= 0) return new float[0];

        float[] wanted = new float[n];

        if (fixedSplitDistances != null && fixedSplitDistances.length >= n) {
            for (int i = 0; i < n; i++) wanted[i] = Math.max(near, fixedSplitDistances[i]);
        } else {
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
                wanted[i] = FastMath.interpolateLinear(FastMath.clamp(lambda, 0f, 1f), lin, log);
            }
        }

        return hysteresis.stabilize(wanted, camSpeed);
    }

    @Override
    public void clearShadows(RenderManager rm, ViewPort vp) {
        if (rm == null) return;
        Renderer r = rm.getRenderer();
        if (r == null) return;

        FrameBuffer[] fbs = tryGetShadowFbsByReflection(this);
        if (fbs == null || fbs.length == 0) return;

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
}