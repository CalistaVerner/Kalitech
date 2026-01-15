// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/CascadeStabilityTelemetryFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipelineRegistry.ShadowOption;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

import java.util.Locale;

/**
 * AAA telemetry / diagnostics filter for cascaded shadow stability.
 */
public final class CascadeStabilityTelemetryFilter implements ShadowFilter {

    private static final Logger LOG = LogManager.getLogger(CascadeStabilityTelemetryFilter.class);

    private final Vector3f lastCamPos = new Vector3f();
    private final Quaternion lastCamRot = new Quaternion();

    private final float[] lastOrtho = new float[8];
    private final float[] lastTexel = new float[8];
    private final Vector3f[] lastScPos = new Vector3f[8];

    @ShadowOption(doc = "Enable telemetry output.")
    public boolean enabled = true;

    @ShadowOption(doc = "Log every N frames (frameId based).", min = 1, max = 100000)
    public int everyFrames = 60;

    @ShadowOption(doc = "If true, logs all splits; otherwise only split0.")
    public boolean allSplits = false;

    @ShadowOption(doc = "If true, use Logger DEBUG; otherwise Logger INFO.")
    public boolean useLogger = true;

    @ShadowOption(doc = "Ortho change ratio considered a resize event.", min = 0.0, max = 1.0)
    public float resizeRatio = 0.02f;

    @ShadowOption(doc = "Position drift (in texels) considered a meaningful movement.", min = 0.0, max = 64.0)
    public float driftTexelsThreshold = 0.35f;

    @ShadowOption(doc = "Rotation threshold (degrees) for 'camRot' field.", min = 0.0, max = 45.0)
    public float camRotateDegThreshold = 0.05f;

    @ShadowOption(doc = "Move threshold (world units) for 'camMove' field.", min = 0.0, max = 10.0)
    public float camMoveWorldThreshold = 0.005f;

    private boolean camInitialized = false;
    private float camMoveWorld = 0f;
    private float camRotateDeg = 0f;

    public CascadeStabilityTelemetryFilter() {
        for (int i = 0; i < lastScPos.length; i++) {
            lastScPos[i] = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
        }
        for (int i = 0; i < lastOrtho.length; i++) {
            lastOrtho[i] = Float.NaN;
            lastTexel[i] = Float.NaN;
        }
    }

    private static int clampSplit(int i) {
        if (i < 0) return 0;
        return Math.min(i, 7);
    }

    private static boolean isFiniteVec(Vector3f v) {
        return v != null && Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static String fmt(float v) {
        if (!Float.isFinite(v)) return "nan";
        return String.format(Locale.ROOT, "%.6f", v);
    }

    private static String fmtVec(Vector3f v) {
        if (v == null) return "(null)";
        return "(" + fmt(v.x) + ", " + fmt(v.y) + ", " + fmt(v.z) + ")";
    }

    @Override
    public int order() {
        return 9000;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (!enabled) return;
        updateCameraDeltas(ctx);
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (!enabled) return;
        if (!shouldLog(ctx.frame.frameId)) return;
        if (!allSplits && ctx.splitIndex != 0) return;

        Camera sc = ctx.shadowCam;

        float orthoW = sc.getFrustumRight() - sc.getFrustumLeft();
        float orthoH = sc.getFrustumTop() - sc.getFrustumBottom();
        float ortho = Math.max(orthoW, orthoH);

        float texel = (sc.getWidth() > 0) ? (ortho / (float) sc.getWidth()) : -1f;
        Vector3f pos = sc.getLocation();

        float prevOrtho = lastOrtho[clampSplit(ctx.splitIndex)];
        float prevTexel = lastTexel[clampSplit(ctx.splitIndex)];
        Vector3f prevPos = lastScPos[clampSplit(ctx.splitIndex)];

        float orthoDelta = Float.isFinite(prevOrtho) ? (ortho - prevOrtho) : 0f;
        float orthoRatio = (Float.isFinite(prevOrtho) && prevOrtho > 0f) ? (FastMath.abs(orthoDelta) / prevOrtho) : 0f;

        float texelDelta = Float.isFinite(prevTexel) ? (texel - prevTexel) : 0f;
        float texelRatio = (Float.isFinite(prevTexel) && prevTexel > 0f) ? (FastMath.abs(texelDelta) / prevTexel) : 0f;

        float driftWorld = (isFiniteVec(prevPos)) ? pos.distance(prevPos) : 0f;
        float driftTexels = (texel > 0f) ? (driftWorld / texel) : 0f;

        String reason = buildReason(ctx, orthoRatio, texelRatio, driftTexels);

        String msg =
                "[shadow][trace] frame=" + ctx.frame.frameId
                        + " split" + ctx.splitIndex
                        + " range=[" + fmt(ctx.splitNear) + ".." + fmt(ctx.splitFar) + "]"
                        + " ortho=" + fmt(ortho)
                        + " texel=" + fmt(texel)
                        + " scPos=" + fmtVec(pos)
                        + " handledCam=" + ctx.handledCam
                        + " snapped=" + ctx.snapped
                        + " texelWorld=" + fmt(ctx.texelWorld)
                        + " driftTexels=" + fmt(driftTexels)
                        + " camMove=" + fmt(camMoveWorld)
                        + " camRotDeg=" + fmt(camRotateDeg)
                        + " reason=" + reason;

        emit(msg);

        lastOrtho[clampSplit(ctx.splitIndex)] = ortho;
        lastTexel[clampSplit(ctx.splitIndex)] = texel;
        lastScPos[clampSplit(ctx.splitIndex)].set(pos);
    }

    private boolean shouldLog(long frameId) {
        int n = everyFrames <= 0 ? 60 : everyFrames;
        return (frameId % (long) n) == 0L;
    }

    private void updateCameraDeltas(ShadowFrameContext ctx) {
        Vector3f p = ctx.viewCam.getLocation();
        Quaternion r = ctx.viewCam.getRotation();

        if (!camInitialized) {
            lastCamPos.set(p);
            lastCamRot.set(r);
            camInitialized = true;
            camMoveWorld = 0f;
            camRotateDeg = 0f;
            return;
        }

        camMoveWorld = p.distance(lastCamPos);

        Quaternion invPrev = lastCamRot.inverse();
        Quaternion delta = invPrev.mult(r);
        float w = clamp(delta.getW(), -1f, 1f);
        float angleRad = 2f * FastMath.acos(w);
        camRotateDeg = angleRad * FastMath.RAD_TO_DEG;

        lastCamPos.set(p);
        lastCamRot.set(r);

        if (camMoveWorld < camMoveWorldThreshold) camMoveWorld = 0f;
        if (camRotateDeg < camRotateDegThreshold) camRotateDeg = 0f;
    }

    private String buildReason(ShadowSplitContext ctx, float orthoRatio, float texelRatio, float driftTexels) {
        boolean resized = orthoRatio >= Math.max(0f, resizeRatio) || texelRatio >= Math.max(0f, resizeRatio);
        boolean drifted = driftTexels >= Math.max(0f, driftTexelsThreshold);

        StringBuilder sb = new StringBuilder(64);

        if (ctx.snapped) {
            if (ctx.texelSnapped) sb.append("snap-applied");
            else sb.append("snap-hold");
        } else {
            sb.append("no-snap");
        }

        if (ctx.handledCam) sb.append("|cam-handled");
        else sb.append("|cam-default");

        if (resized) sb.append("|resize");
        if (drifted) sb.append("|drift");

        if (camMoveWorld > 0f) sb.append("|cam-move");
        if (camRotateDeg > 0f) sb.append("|cam-rot");

        return sb.toString();
    }

    private void emit(String msg) {
        if (useLogger) {
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