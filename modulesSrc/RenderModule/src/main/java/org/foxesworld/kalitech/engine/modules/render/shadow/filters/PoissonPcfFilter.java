/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.material.Material
 *  com.jme3.math.FastMath
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector2f
 *  com.jme3.math.Vector3f
 *  com.jme3.shader.VarType
 */
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

public final class PoissonPcfFilter
implements ShadowFilter {
    private static final Vector2f[] POISSON_32 = new Vector2f[]{new Vector2f(-0.613392f, 0.617481f), new Vector2f(0.170019f, -0.040254f), new Vector2f(-0.299417f, 0.791925f), new Vector2f(0.64568f, 0.49321f), new Vector2f(-0.651784f, 0.717887f), new Vector2f(0.421003f, 0.02707f), new Vector2f(-0.817194f, -0.271096f), new Vector2f(-0.705374f, -0.668203f), new Vector2f(0.97705f, -0.108615f), new Vector2f(0.063326f, 0.142369f), new Vector2f(0.203528f, 0.214331f), new Vector2f(-0.667531f, 0.32609f), new Vector2f(-0.098422f, -0.295755f), new Vector2f(-0.885922f, 0.215369f), new Vector2f(0.566637f, 0.605213f), new Vector2f(0.039766f, -0.3961f), new Vector2f(0.751946f, 0.453352f), new Vector2f(0.078707f, -0.715323f), new Vector2f(-0.075838f, -0.529344f), new Vector2f(0.724479f, -0.580798f), new Vector2f(0.222999f, -0.215125f), new Vector2f(-0.467574f, -0.405438f), new Vector2f(-0.248268f, -0.814753f), new Vector2f(0.354411f, -0.88757f), new Vector2f(0.175817f, 0.382366f), new Vector2f(0.487472f, -0.063082f), new Vector2f(-0.084078f, 0.898312f), new Vector2f(0.488876f, -0.783441f), new Vector2f(0.470016f, 0.217933f), new Vector2f(-0.69689f, -0.549791f), new Vector2f(-0.149693f, 0.605762f), new Vector2f(0.034211f, 0.97998f)};
    private static final Vector2f[] POISSON_16 = new Vector2f[]{POISSON_32[0], POISSON_32[1], POISSON_32[2], POISSON_32[3], POISSON_32[4], POISSON_32[5], POISSON_32[6], POISSON_32[7], POISSON_32[8], POISSON_32[9], POISSON_32[10], POISSON_32[11], POISSON_32[12], POISSON_32[13], POISSON_32[14], POISSON_32[15]};
    private static final Vector2f[] POISSON_8 = new Vector2f[]{POISSON_32[0], POISSON_32[1], POISSON_32[2], POISSON_32[3], POISSON_32[4], POISSON_32[5], POISSON_32[6], POISSON_32[7]};
    private final float[] radiusBySplit = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
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
        if (s <= 8) {
            return 8;
        }
        if (s <= 12) {
            return 12;
        }
        if (s <= 16) {
            return 16;
        }
        if (s <= 24) {
            return 24;
        }
        return 32;
    }

    private static Vector2f[] pickKernel(int samples) {
        int s = PoissonPcfFilter.sanitizeSamples(samples);
        if (s == 8) {
            return POISSON_8;
        }
        if (s == 16) {
            return POISSON_16;
        }
        return POISSON_32;
    }

    private static float clampPos(float v, float def) {
        if (!(v > 0.0f)) {
            return def;
        }
        return v;
    }

    private static int murmurMix(int x) {
        x ^= x >>> 16;
        x *= 2146121005;
        x ^= x >>> 15;
        x *= -2073254261;
        x ^= x >>> 16;
        return x;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (!this.enabled) {
            return;
        }
        this.radiusBySplit[0] = PoissonPcfFilter.clampPos(this.split0, 1.0f);
        this.radiusBySplit[1] = PoissonPcfFilter.clampPos(this.split1, this.radiusBySplit[0]);
        this.radiusBySplit[2] = PoissonPcfFilter.clampPos(this.split2, this.radiusBySplit[1]);
        this.radiusBySplit[3] = PoissonPcfFilter.clampPos(this.split3, this.radiusBySplit[2]);
        if (ctx.numSplits < 4) {
            for (int i = ctx.numSplits; i < 4; ++i) {
                this.radiusBySplit[i] = this.radiusBySplit[Math.max(0, ctx.numSplits - 1)];
            }
        }
        this.updateCameraDeltas(ctx);
        this.updateRotation(ctx);
    }

    @Override
    public void setMaterialParameters(ShadowFrameContext ctx, Material material) {
        if (!this.enabled || material == null) {
            return;
        }
        material.setInt("ShadowPcfEnabled", 1);
        material.setInt("ShadowPcfSamples", PoissonPcfFilter.sanitizeSamples(this.samples));
        material.setFloat("ShadowPcfBaseRadius", Math.max(0.0f, this.baseRadiusTexels));
        material.setParam("ShadowPcfRadiusBySplit", VarType.FloatArray, (Object)this.radiusBySplit);
        Vector2f[] kernel = PoissonPcfFilter.pickKernel(this.samples);
        material.setParam("ShadowPoisson", VarType.Vector2Array, (Object)kernel);
        material.setFloat("ShadowPoissonRotation", this.rotationRad);
    }

    @Override
    public void clearMaterialParameters(ShadowFrameContext ctx, Material material) {
        if (material == null) {
            return;
        }
        material.setInt("ShadowPcfEnabled", 0);
    }

    private void updateCameraDeltas(ShadowFrameContext ctx) {
        Float move = ctx.ws.get(ShadowKeys.VIEW_CAM_MOVE_WORLD);
        Float rot = ctx.ws.get(ShadowKeys.VIEW_CAM_ROTATE_DEG);
        if (move != null && rot != null) {
            this.lastMoveWorld = move.floatValue();
            this.lastRotateDeg = rot.floatValue();
            return;
        }
        Vector3f p = ctx.viewCam.getLocation();
        Quaternion r = ctx.viewCam.getRotation();
        if (!this.camInitialized) {
            this.lastCamPos.set(p);
            this.lastCamRot.set(r);
            this.camInitialized = true;
            this.lastMoveWorld = 0.0f;
            this.lastRotateDeg = 0.0f;
            return;
        }
        this.lastMoveWorld = p.distance(this.lastCamPos);
        this.invPrev.set(this.lastCamRot).inverseLocal();
        this.delta.set(this.invPrev).multLocal(r);
        float angleRad = 2.0f * FastMath.acos((float)FastMath.clamp((float)this.delta.getW(), (float)-1.0f, (float)1.0f));
        this.lastRotateDeg = angleRad * 57.295776f;
        this.lastCamPos.set(p);
        this.lastCamRot.set(r);
    }

    private void updateRotation(ShadowFrameContext ctx) {
        if (!this.rotateKernel) {
            return;
        }
        long fid = ctx.frameId;
        boolean allow = true;
        if (this.rotateOnlyOnCameraEvent) {
            boolean bl = allow = this.lastMoveWorld >= this.camMoveEventThreshold || this.lastRotateDeg >= this.camRotateEventThresholdDeg;
        }
        if (!(allow || this.lastRotFrameId != Long.MIN_VALUE && fid - this.lastRotFrameId < (long)this.rotateEveryFrames * 4L)) {
            allow = true;
        }
        if (!allow) {
            return;
        }
        if (this.lastRotFrameId != Long.MIN_VALUE && fid - this.lastRotFrameId < (long)this.rotateEveryFrames) {
            return;
        }
        int seed = PoissonPcfFilter.murmurMix((int)(fid ^ fid >>> 32));
        float a = (float)(seed & 0xFFFF) / 65535.0f;
        this.rotationRad = a * ((float)Math.PI * 2);
        this.lastRotFrameId = fid;
        this.lastRotSeedFrameId = fid;
    }
}

