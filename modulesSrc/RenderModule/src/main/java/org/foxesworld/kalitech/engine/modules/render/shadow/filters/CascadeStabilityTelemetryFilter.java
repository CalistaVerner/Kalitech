/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.FastMath
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector3f
 *  com.jme3.renderer.Camera
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipelineRegistry;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class CascadeStabilityTelemetryFilter
implements ShadowFilter {
    private static final Logger LOG = LogManager.getLogger(CascadeStabilityTelemetryFilter.class);
    private final Vector3f lastCamPos = new Vector3f();
    private final Quaternion lastCamRot = new Quaternion();
    private final float[] lastOrtho = new float[8];
    private final float[] lastTexel = new float[8];
    private final Vector3f[] lastScPos = new Vector3f[8];
    @ShadowPipelineRegistry.ShadowOption(doc="Enable telemetry output.")
    public boolean enabled = true;
    @ShadowPipelineRegistry.ShadowOption(doc="Log every N frames (frameId based).", min=1.0, max=100000.0)
    public int everyFrames = 60;
    @ShadowPipelineRegistry.ShadowOption(doc="If true, logs all splits; otherwise only split0.")
    public boolean allSplits = false;
    @ShadowPipelineRegistry.ShadowOption(doc="If true, use Logger DEBUG; otherwise Logger INFO.")
    public boolean useLogger = true;
    @ShadowPipelineRegistry.ShadowOption(doc="Ortho change ratio considered a resize event.", min=0.0, max=1.0)
    public float resizeRatio = 0.02f;
    @ShadowPipelineRegistry.ShadowOption(doc="Position drift (in texels) considered a meaningful movement.", min=0.0, max=64.0)
    public float driftTexelsThreshold = 0.35f;
    @ShadowPipelineRegistry.ShadowOption(doc="Rotation threshold (degrees) for 'camRot' field.", min=0.0, max=45.0)
    public float camRotateDegThreshold = 0.05f;
    @ShadowPipelineRegistry.ShadowOption(doc="Move threshold (world units) for 'camMove' field.", min=0.0, max=10.0)
    public float camMoveWorldThreshold = 0.005f;
    private boolean camInitialized = false;
    private float camMoveWorld = 0.0f;
    private float camRotateDeg = 0.0f;

    public CascadeStabilityTelemetryFilter() {
        int i;
        for (i = 0; i < this.lastScPos.length; ++i) {
            this.lastScPos[i] = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
        }
        for (i = 0; i < this.lastOrtho.length; ++i) {
            this.lastOrtho[i] = Float.NaN;
            this.lastTexel[i] = Float.NaN;
        }
    }

    private static int clampSplit(int i) {
        if (i < 0) {
            return 0;
        }
        return Math.min(i, 7);
    }

    private static boolean isFiniteVec(Vector3f v) {
        return v != null && Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static String fmt(float v) {
        if (!Float.isFinite(v)) {
            return "nan";
        }
        return String.format(Locale.ROOT, "%.6f", Float.valueOf(v));
    }

    private static String fmtVec(Vector3f v) {
        if (v == null) {
            return "(null)";
        }
        return "(" + CascadeStabilityTelemetryFilter.fmt(v.x) + ", " + CascadeStabilityTelemetryFilter.fmt(v.y) + ", " + CascadeStabilityTelemetryFilter.fmt(v.z) + ")";
    }

    @Override
    public int order() {
        return 9000;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (!this.enabled) {
            return;
        }
        this.updateCameraDeltas(ctx);
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (!this.enabled) {
            return;
        }
        if (!this.shouldLog(ctx.frame.frameId)) {
            return;
        }
        if (!this.allSplits && ctx.splitIndex != 0) {
            return;
        }
        Camera sc = ctx.shadowCam;
        float orthoW = sc.getFrustumRight() - sc.getFrustumLeft();
        float orthoH = sc.getFrustumTop() - sc.getFrustumBottom();
        float ortho = Math.max(orthoW, orthoH);
        float texel = sc.getWidth() > 0 ? ortho / (float)sc.getWidth() : -1.0f;
        Vector3f pos = sc.getLocation();
        float prevOrtho = this.lastOrtho[CascadeStabilityTelemetryFilter.clampSplit(ctx.splitIndex)];
        float prevTexel = this.lastTexel[CascadeStabilityTelemetryFilter.clampSplit(ctx.splitIndex)];
        Vector3f prevPos = this.lastScPos[CascadeStabilityTelemetryFilter.clampSplit(ctx.splitIndex)];
        float orthoDelta = Float.isFinite(prevOrtho) ? ortho - prevOrtho : 0.0f;
        float orthoRatio = Float.isFinite(prevOrtho) && prevOrtho > 0.0f ? FastMath.abs((float)orthoDelta) / prevOrtho : 0.0f;
        float texelDelta = Float.isFinite(prevTexel) ? texel - prevTexel : 0.0f;
        float texelRatio = Float.isFinite(prevTexel) && prevTexel > 0.0f ? FastMath.abs((float)texelDelta) / prevTexel : 0.0f;
        float driftWorld = CascadeStabilityTelemetryFilter.isFiniteVec(prevPos) ? pos.distance(prevPos) : 0.0f;
        float driftTexels = texel > 0.0f ? driftWorld / texel : 0.0f;
        String reason = this.buildReason(ctx, orthoRatio, texelRatio, driftTexels);
        String msg = "[shadow][trace] frame=" + ctx.frame.frameId + " split" + ctx.splitIndex + " range=[" + CascadeStabilityTelemetryFilter.fmt(ctx.splitNear) + ".." + CascadeStabilityTelemetryFilter.fmt(ctx.splitFar) + "] ortho=" + CascadeStabilityTelemetryFilter.fmt(ortho) + " texel=" + CascadeStabilityTelemetryFilter.fmt(texel) + " scPos=" + CascadeStabilityTelemetryFilter.fmtVec(pos) + " handledCam=" + ctx.handledCam + " snapped=" + ctx.snapped + " texelWorld=" + CascadeStabilityTelemetryFilter.fmt(ctx.texelWorld) + " driftTexels=" + CascadeStabilityTelemetryFilter.fmt(driftTexels) + " camMove=" + CascadeStabilityTelemetryFilter.fmt(this.camMoveWorld) + " camRotDeg=" + CascadeStabilityTelemetryFilter.fmt(this.camRotateDeg) + " reason=" + reason;
        this.emit(msg);
        this.lastOrtho[CascadeStabilityTelemetryFilter.clampSplit((int)ctx.splitIndex)] = ortho;
        this.lastTexel[CascadeStabilityTelemetryFilter.clampSplit((int)ctx.splitIndex)] = texel;
        this.lastScPos[CascadeStabilityTelemetryFilter.clampSplit(ctx.splitIndex)].set(pos);
    }

    private boolean shouldLog(long frameId) {
        int n = this.everyFrames <= 0 ? 60 : this.everyFrames;
        return frameId % (long)n == 0L;
    }

    private void updateCameraDeltas(ShadowFrameContext ctx) {
        Vector3f p = ctx.viewCam.getLocation();
        Quaternion r = ctx.viewCam.getRotation();
        if (!this.camInitialized) {
            this.lastCamPos.set(p);
            this.lastCamRot.set(r);
            this.camInitialized = true;
            this.camMoveWorld = 0.0f;
            this.camRotateDeg = 0.0f;
            return;
        }
        this.camMoveWorld = p.distance(this.lastCamPos);
        Quaternion invPrev = this.lastCamRot.inverse();
        Quaternion delta = invPrev.mult(r);
        float w = CascadeStabilityTelemetryFilter.clamp(delta.getW(), -1.0f, 1.0f);
        float angleRad = 2.0f * FastMath.acos((float)w);
        this.camRotateDeg = angleRad * 57.295776f;
        this.lastCamPos.set(p);
        this.lastCamRot.set(r);
        if (this.camMoveWorld < this.camMoveWorldThreshold) {
            this.camMoveWorld = 0.0f;
        }
        if (this.camRotateDeg < this.camRotateDegThreshold) {
            this.camRotateDeg = 0.0f;
        }
    }

    private String buildReason(ShadowSplitContext ctx, float orthoRatio, float texelRatio, float driftTexels) {
        boolean resized = orthoRatio >= Math.max(0.0f, this.resizeRatio) || texelRatio >= Math.max(0.0f, this.resizeRatio);
        boolean drifted = driftTexels >= Math.max(0.0f, this.driftTexelsThreshold);
        StringBuilder sb = new StringBuilder(64);
        if (ctx.snapped) {
            if (ctx.texelSnapped) {
                sb.append("snap-applied");
            } else {
                sb.append("snap-hold");
            }
        } else {
            sb.append("no-snap");
        }
        if (ctx.handledCam) {
            sb.append("|cam-handled");
        } else {
            sb.append("|cam-default");
        }
        if (resized) {
            sb.append("|resize");
        }
        if (drifted) {
            sb.append("|drift");
        }
        if (this.camMoveWorld > 0.0f) {
            sb.append("|cam-move");
        }
        if (this.camRotateDeg > 0.0f) {
            sb.append("|cam-rot");
        }
        return sb.toString();
    }

    private void emit(String msg) {
        if (this.useLogger) {
            LOG.debug(msg);
            return;
        }
        LOG.info(msg);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setEveryFrames(int everyFrames) {
        this.everyFrames = everyFrames;
    }

    public void setAllSplits(boolean allSplits) {
        this.allSplits = allSplits;
    }

    public void setUseLogger(boolean useLogger) {
        this.useLogger = useLogger;
    }

    public void setResizeRatio(float resizeRatio) {
        this.resizeRatio = resizeRatio;
    }

    public void setDriftTexelsThreshold(float driftTexelsThreshold) {
        this.driftTexelsThreshold = driftTexelsThreshold;
    }

    public void setCamRotateDegThreshold(float camRotateDegThreshold) {
        this.camRotateDegThreshold = camRotateDegThreshold;
    }

    public void setCamMoveWorldThreshold(float camMoveWorldThreshold) {
        this.camMoveWorldThreshold = camMoveWorldThreshold;
    }
}

