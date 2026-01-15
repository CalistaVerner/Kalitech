// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/PoissonPcfFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.shader.VarType;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;

/**
 * Poisson PCF / CDPR-style shadow softening filter (data-driven).
 * <p>
 * Consumes camera deltas from {@link ShadowKeys#VIEW_CAM_MOVE_WORLD} and {@link ShadowKeys#VIEW_CAM_ROTATE_DEG}
 * when available, avoiding duplicate computations.
 */
public final class PoissonPcfFilter implements ShadowFilter {

    private static final Vector2f[] POISSON_32 = new Vector2f[]{
            new Vector2f(-0.613392f, 0.617481f),
            new Vector2f(0.170019f, -0.040254f),
            new Vector2f(-0.299417f, 0.791925f),
            new Vector2f(0.645680f, 0.493210f),
            new Vector2f(-0.651784f, 0.717887f),
            new Vector2f(0.421003f, 0.027070f),
            new Vector2f(-0.817194f, -0.271096f),
            new Vector2f(-0.705374f, -0.668203f),
            new Vector2f(0.977050f, -0.108615f),
            new Vector2f(0.063326f, 0.142369f),
            new Vector2f(0.203528f, 0.214331f),
            new Vector2f(-0.667531f, 0.326090f),
            new Vector2f(-0.098422f, -0.295755f),
            new Vector2f(-0.885922f, 0.215369f),
            new Vector2f(0.566637f, 0.605213f),
            new Vector2f(0.039766f, -0.396100f),
            new Vector2f(0.751946f, 0.453352f),
            new Vector2f(0.078707f, -0.715323f),
            new Vector2f(-0.075838f, -0.529344f),
            new Vector2f(0.724479f, -0.580798f),
            new Vector2f(0.222999f, -0.215125f),
            new Vector2f(-0.467574f, -0.405438f),
            new Vector2f(-0.248268f, -0.814753f),
            new Vector2f(0.354411f, -0.887570f),
            new Vector2f(0.175817f, 0.382366f),
            new Vector2f(0.487472f, -0.063082f),
            new Vector2f(-0.084078f, 0.898312f),
            new Vector2f(0.488876f, -0.783441f),
            new Vector2f(0.470016f, 0.217933f),
            new Vector2f(-0.696890f, -0.549791f),
            new Vector2f(-0.149693f, 0.605762f),
            new Vector2f(0.034211f, 0.979980f)
    };
    private static final Vector2f[] POISSON_16 = new Vector2f[]{
            POISSON_32[0], POISSON_32[1], POISSON_32[2], POISSON_32[3],
            POISSON_32[4], POISSON_32[5], POISSON_32[6], POISSON_32[7],
            POISSON_32[8], POISSON_32[9], POISSON_32[10], POISSON_32[11],
            POISSON_32[12], POISSON_32[13], POISSON_32[14], POISSON_32[15]
    };
    private static final Vector2f[] POISSON_8 = new Vector2f[]{
            POISSON_32[0], POISSON_32[1], POISSON_32[2], POISSON_32[3],
            POISSON_32[4], POISSON_32[5], POISSON_32[6], POISSON_32[7]
    };
    private final float[] radiusBySplit = new float[]{1f, 1f, 1f, 1f};
    private final Vector3f lastCamPos = new Vector3f();
    private final Quaternion lastCamRot = new Quaternion();
    private final Quaternion invPrev = new Quaternion();
    private final Quaternion delta = new Quaternion();
    public boolean enabled = true;
    public int samples = 16;
    public float baseRadiusTexels = 1.0f;
    public float split0 = 1.0f;
    public float split1 = 1.25f;
    public float split2 = 1.65f;
    public float split3 = 2.25f;
    public boolean rotateKernel = true;
    public int rotateEveryFrames = 1;
    public boolean rotateOnlyOnCameraEvent = true;
    public float camMoveEventThreshold = 0.02f;
    public float camRotateEventThresholdDeg = 0.25f;
    private boolean camInitialized;
    private float lastMoveWorld;
    private float lastRotateDeg;
    private long lastRotFrameId = Long.MIN_VALUE;
    private float rotationRad;
    private long lastRotSeedFrameId = Long.MIN_VALUE;

    private static int sanitizeSamples(int s) {
        if (s <= 8) return 8;
        if (s <= 12) return 12;
        if (s <= 16) return 16;
        if (s <= 24) return 24;
        return 32;
    }

    private static Vector2f[] pickKernel(int samples) {
        int s = sanitizeSamples(samples);
        if (s == 8) return POISSON_8;
        if (s == 16) return POISSON_16;
        return POISSON_32;
    }

    private static float clampPos(float v, float def) {
        if (!(v > 0f)) return def;
        return v;
    }

    private static int murmurMix(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (!enabled) return;

        radiusBySplit[0] = clampPos(split0, 1.0f);
        radiusBySplit[1] = clampPos(split1, radiusBySplit[0]);
        radiusBySplit[2] = clampPos(split2, radiusBySplit[1]);
        radiusBySplit[3] = clampPos(split3, radiusBySplit[2]);

        if (ctx.numSplits < 4) {
            for (int i = ctx.numSplits; i < 4; i++) radiusBySplit[i] = radiusBySplit[Math.max(0, ctx.numSplits - 1)];
        }

        updateCameraDeltas(ctx);
        updateRotation(ctx);
    }

    @Override
    public void setMaterialParameters(ShadowFrameContext ctx, Material material) {
        if (!enabled || material == null) return;

        material.setInt("ShadowPcfEnabled", 1);
        material.setInt("ShadowPcfSamples", sanitizeSamples(samples));
        material.setFloat("ShadowPcfBaseRadius", Math.max(0.0f, baseRadiusTexels));
        material.setParam("ShadowPcfRadiusBySplit", VarType.FloatArray, radiusBySplit);

        Vector2f[] kernel = pickKernel(samples);
        material.setParam("ShadowPoisson", VarType.Vector2Array, kernel);
        material.setFloat("ShadowPoissonRotation", rotationRad);
    }

    @Override
    public void clearMaterialParameters(ShadowFrameContext ctx, Material material) {
        if (material == null) return;
        material.setInt("ShadowPcfEnabled", 0);
    }

    private void updateCameraDeltas(ShadowFrameContext ctx) {
        Float move = ctx.ws.get(ShadowKeys.VIEW_CAM_MOVE_WORLD);
        Float rot = ctx.ws.get(ShadowKeys.VIEW_CAM_ROTATE_DEG);
        if (move != null && rot != null) {
            lastMoveWorld = move;
            lastRotateDeg = rot;
            return;
        }

        Vector3f p = ctx.viewCam.getLocation();
        Quaternion r = ctx.viewCam.getRotation();

        if (!camInitialized) {
            lastCamPos.set(p);
            lastCamRot.set(r);
            camInitialized = true;
            lastMoveWorld = 0f;
            lastRotateDeg = 0f;
            return;
        }

        lastMoveWorld = p.distance(lastCamPos);

        invPrev.set(lastCamRot).inverseLocal();
        delta.set(invPrev).multLocal(r);
        float angleRad = 2.0f * FastMath.acos(FastMath.clamp(delta.getW(), -1f, 1f));
        lastRotateDeg = angleRad * FastMath.RAD_TO_DEG;

        lastCamPos.set(p);
        lastCamRot.set(r);
    }

    private void updateRotation(ShadowFrameContext ctx) {
        if (!rotateKernel) return;

        long fid = ctx.frameId;

        boolean allow = true;

        if (rotateOnlyOnCameraEvent) {
            allow = (lastMoveWorld >= camMoveEventThreshold) || (lastRotateDeg >= camRotateEventThresholdDeg);
        }

        if (!allow) {
            if (lastRotFrameId == Long.MIN_VALUE || (fid - lastRotFrameId) >= (long) rotateEveryFrames * 4L) {
                allow = true;
            }
        }

        if (!allow) return;
        if (lastRotFrameId != Long.MIN_VALUE && (fid - lastRotFrameId) < rotateEveryFrames) return;

        int seed = murmurMix((int) (fid ^ (fid >>> 32)));
        float a = (seed & 0xffff) / 65535.0f;
        rotationRad = a * FastMath.TWO_PI;

        lastRotFrameId = fid;
        lastRotSeedFrameId = fid;
    }
}