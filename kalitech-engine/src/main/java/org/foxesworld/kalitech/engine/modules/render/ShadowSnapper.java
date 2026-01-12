// FILE: org/foxesworld/kalitech/engine/modules/render/ShadowSnapper.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

/**
 * Texel snapping + stable padded extents.
 * Also provides "reason logs" to distinguish texel drift vs bias/PCF shimmer.
 */
public final class ShadowSnapper {

    private boolean stabilizeExtents = true;
    private float extentsPadding = 1.12f;

    private int shadowMapSize = 2048;

    private Logger log;
    private boolean debugEnabled = false;
    private long debugIntervalNanos = 500_000_000L; // 500 ms
    private long lastDebugNanos = 0L;

    private float lastWidth = 0f;
    private float lastHeight = 0f;
    private float lastTexelWorld = 0f;
    private float lastDx = 0f;
    private float lastDy = 0f;

    // shimmer probe / stability
    private float lastTexelWorldLogged = Float.NaN;
    private int stableSnapFrames = 0;
    private int warnAfterStableFrames = 180;
    private boolean shimmerWarned = false;

    // texelWorld changes streak (breathing detector)
    private int texelChangedStreak = 0;

    private final Vector3f tmpLoc = new Vector3f();
    private final Vector3f tmpLeft = new Vector3f();
    private final Vector3f tmpUp = new Vector3f();
    private final Vector3f delta = new Vector3f();

    public ShadowSnapper() {
    }

    private static float stableEps(float texelWorld) {
        // eps is proportional to texel size to avoid float-noise false negatives
        // 1e-5*texelWorld ~ 1e-8 when texelWorld~1e-3 (your case) => sane.
        float t = (texelWorld > 0f ? texelWorld : 1f);
        return (1e-5f * t) + 1e-9f;
    }

    private static void ensureNamed(Camera cam) {
        try {
            String n = cam.getName();
            if (n == null || n.isBlank()) cam.setName("shadowCam");
        } catch (Throwable ignored) {
        }
    }

    private static float roundToStep(float v, float step) {
        if (!(step > 0f)) return v;
        return Math.round(v / step) * step;
    }

    private static boolean approx(float a, float b, float eps) {
        if (Float.isNaN(a) || Float.isNaN(b)) return false;
        return Math.abs(a - b) <= eps;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }

    public void setLogger(Logger log) {
        this.log = log;
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        if (!enabled) {
            stableSnapFrames = 0;
            shimmerWarned = false;
            texelChangedStreak = 0;
            lastTexelWorldLogged = Float.NaN;
        }
    }

    public void setDebugIntervalMs(int ms) {
        if (ms <= 0) ms = 250;
        this.debugIntervalNanos = (long) ms * 1_000_000L;
    }

    public void setWarnAfterStableFrames(int frames) {
        if (frames < 60) frames = 60;
        this.warnAfterStableFrames = frames;
    }

    public void setShadowMapSize(int mapSize) {
        if (mapSize < 256) mapSize = 256;
        this.shadowMapSize = mapSize;
    }

    public boolean isStabilizeExtents() {
        return stabilizeExtents;
    }

    public void setStabilizeExtents(boolean stabilizeExtents) {
        this.stabilizeExtents = stabilizeExtents;
    }

    public float getExtentsPadding() {
        return extentsPadding;
    }

    public void setExtentsPadding(float extentsPadding) {
        if (!(extentsPadding > 0f)) extentsPadding = 1.0f;
        this.extentsPadding = extentsPadding;
    }

    public float getLastTexelWorld() {
        return lastTexelWorld;
    }

    public float getLastWidth() {
        return lastWidth;
    }

    public float getLastHeight() {
        return lastHeight;
    }

    public float getLastDx() {
        return lastDx;
    }

    public float getLastDy() {
        return lastDy;
    }

    public void snap(Camera shadowCam) {
        if (shadowCam == null) return;
        if (!shadowCam.isParallelProjection()) return;

        ensureNamed(shadowCam);

        // 1) Stabilize extents (avoid breathing)
        if (stabilizeExtents) {
            float l = shadowCam.getFrustumLeft();
            float r = shadowCam.getFrustumRight();
            float b = shadowCam.getFrustumBottom();
            float t = shadowCam.getFrustumTop();

            float w0 = Math.abs(r - l);
            float h0 = Math.abs(t - b);
            float s = Math.max(w0, h0) * extentsPadding;
            float half = 0.5f * s;

            shadowCam.setFrustum(
                    shadowCam.getFrustumNear(), shadowCam.getFrustumFar(),
                    -half, +half, +half, -half
            );
        }

        // 2) World units per texel
        float width = Math.abs(shadowCam.getFrustumRight() - shadowCam.getFrustumLeft());
        float height = Math.abs(shadowCam.getFrustumTop() - shadowCam.getFrustumBottom());

        int ms = Math.max(1, shadowMapSize);
        float texelWorld = width / (float) ms;

        lastWidth = width;
        lastHeight = height;
        lastTexelWorld = texelWorld;

        // 3) Copy vectors safely
        tmpLoc.set(shadowCam.getLocation());
        tmpLeft.set(shadowCam.getLeft()).normalizeLocal();
        tmpUp.set(shadowCam.getUp()).normalizeLocal();

        float projL = tmpLoc.dot(tmpLeft);
        float projU = tmpLoc.dot(tmpUp);

        float snappedL = roundToStep(projL, texelWorld);
        float snappedU = roundToStep(projU, texelWorld);

        float dL = snappedL - projL;
        float dU = snappedU - projU;

        lastDx = dL;
        lastDy = dU;

        // apply if meaningfully off-grid (avoid float-noise toggling)
        final float eps = stableEps(texelWorld);
        if (Math.abs(dL) > eps || Math.abs(dU) > eps) {
            delta.set(tmpLeft).multLocal(dL);
            delta.addLocal(tmpUp.x * dU, tmpUp.y * dU, tmpUp.z * dU);
            tmpLoc.addLocal(delta);
            shadowCam.setLocation(tmpLoc);
        }

        // Stability: tie epsilon to texelWorld (NOT 1e-9 absolute!)
        if (Math.abs(dL) <= eps && Math.abs(dU) <= eps) stableSnapFrames++;
        else stableSnapFrames = 0;

        debugMaybe(shadowCam);

        if (debugEnabled && log != null && log.isWarnEnabled() && !shimmerWarned) {
            if (stableSnapFrames >= warnAfterStableFrames) {
                shimmerWarned = true;
                log.warn("[shadow][shimmer] snapping is stable (|dx|,|dy| <= eps for {} frames). If shimmer persists, root cause is likely: bias/PCF + subpixel geometry OR cascade transition. Next step: add slope-scaled bias + normal-offset + cascade blending.",
                        stableSnapFrames);
            }
        }
    }

    private void debugMaybe(Camera cam) {
        if (!debugEnabled) return;
        if (log == null || !log.isDebugEnabled()) return;

        long now = System.nanoTime();
        if (now - lastDebugNanos < debugIntervalNanos) return;
        lastDebugNanos = now;

        boolean texelChanged = !(approx(lastTexelWorld, lastTexelWorldLogged, stableEps(lastTexelWorld)));
        if (texelChanged) texelChangedStreak++;
        else texelChangedStreak = 0;

        lastTexelWorldLogged = lastTexelWorld;

        log.debug("[shadow][snap] cam={} mapSize={} w={} h={} texelWorld={} dx={} dy={} stabilizeExtents={} pad={} texelChanged={} stableSnapFrames={} texelChangedStreak={}",
                cam.getName(), shadowMapSize,
                fmt(lastWidth), fmt(lastHeight), fmt(lastTexelWorld),
                fmt(lastDx), fmt(lastDy),
                stabilizeExtents, fmt(extentsPadding),
                texelChanged, stableSnapFrames, texelChangedStreak);
    }
}