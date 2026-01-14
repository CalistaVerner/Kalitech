// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/ShadowSnapper.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows;

import com.jme3.math.FastMath;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

/**
 * Shadow camera snapping in stable light-basis space.
 * <p>
 * Key principle (CDPR-style):
 * - snap translation in LIGHT-SPACE on a texel grid
 * - use ORTHONORMAL basis -> inverse == transpose (fast + stable)
 * <p>
 * Works with orthographic (parallel projection) shadow cameras.
 */
public final class ShadowSnapper {

    private final int shadowMapSize;
    private final Config cfg;
    // per-cascade history
    private final Vector3f[] lastWorldPos;
    private final Quaternion[] lastRot;
    private final float[] motionWeight;
    // temps
    private final Matrix3f tBasisT = new Matrix3f();
    private final Vector3f tLightPos = new Vector3f();
    private final Vector3f tWorld = new Vector3f();
    public ShadowSnapper(int shadowMapSize) {
        this(shadowMapSize, null, 16);
    }
    public ShadowSnapper(int shadowMapSize, Config config, int maxCascades) {
        this.shadowMapSize = Math.max(64, shadowMapSize);
        this.cfg = (config != null) ? config : new Config();
        int n = Math.max(1, maxCascades);
        this.lastWorldPos = new Vector3f[n];
        this.lastRot = new Quaternion[n];
        this.motionWeight = new float[n];
        for (int i = 0; i < n; i++) {
            lastWorldPos[i] = new Vector3f();
            lastRot[i] = new Quaternion(0, 0, 0, 1);
            motionWeight[i] = 1f;
        }
    }

    public void updateConfig(Config c) {
        if (c == null) return;
        cfg.enablePositionSnap = c.enablePositionSnap;
        cfg.positionThreshold = Math.max(0.01f, c.positionThreshold);
        cfg.maxSnapDistanceTexels = Math.max(0.1f, c.maxSnapDistanceTexels);
        cfg.adaptiveSnapping = c.adaptiveSnapping;
        cfg.conservative = c.conservative;
    }

    public float getMotionWeight(int cascadeIdx) {
        if (cascadeIdx < 0 || cascadeIdx >= motionWeight.length) return 1f;
        return motionWeight[cascadeIdx];
    }

    /**
     * Snaps shadow camera translation to texel grid in stable light space.
     *
     * @param cascadeIdx cascade index for history/adaptive thresholds
     * @param shadowCam  orthographic shadow camera (mutated)
     * @param lightBasis orthonormal basis (columns: right, up, dir)
     * @param dtSeconds  delta time seconds
     */
    public boolean snap(int cascadeIdx, Camera shadowCam, Matrix3f lightBasis, float dtSeconds, SnapResult out) {
        if (shadowCam == null || lightBasis == null) return false;
        if (cascadeIdx < 0) cascadeIdx = 0;
        if (cascadeIdx >= lastWorldPos.length) cascadeIdx = lastWorldPos.length - 1;

        if (out == null) out = new SnapResult();
        out.reset();

        boolean snapped = false;

        if (cfg.enablePositionSnap) {
            snapped |= snapPosition(cascadeIdx, shadowCam, lightBasis, dtSeconds, out);
        }

        updateMotion(cascadeIdx, shadowCam, dtSeconds);
        return snapped;
    }

    private boolean snapPosition(int cascadeIdx, Camera cam, Matrix3f basis, float dt, SnapResult out) {
        // Orthonormal basis -> inverse = transpose
        tBasisT.set(basis).transposeLocal();

        Vector3f worldPos = cam.getLocation();
        tBasisT.mult(worldPos, tLightPos);

        float frustumWidth = cam.getFrustumRight() - cam.getFrustumLeft();
        float texel = frustumWidth / (float) shadowMapSize;
        if (!(texel > 0f)) return false;

        float threshold = cfg.positionThreshold * texel;
        if (cfg.adaptiveSnapping && dt > 0f) {
            float w = motionWeight[cascadeIdx];
            threshold *= (1f + w * 2f);
        }

        float x = tLightPos.x;
        float y = tLightPos.y;

        float sx = Math.round(x / texel) * texel;
        float sy = Math.round(y / texel) * texel;

        float dx = Math.abs(sx - x);
        float dy = Math.abs(sy - y);

        boolean ok = (dx <= threshold && dy <= threshold);

        float maxDelta = cfg.maxSnapDistanceTexels * texel;
        if (cfg.conservative) {
            ok &= (dx <= maxDelta && dy <= maxDelta);

            // protect against huge jumps (teleports)
            Vector3f last = lastWorldPos[cascadeIdx];
            if (last.lengthSquared() > 0f) {
                float moved = worldPos.distance(last);
                ok &= (moved <= maxDelta * 4f);
            }
        }

        if (!ok) return false;

        // compute snapped world position (basis * lightPos)
        tLightPos.set(sx, sy, tLightPos.z);
        basis.mult(tLightPos, tWorld);

        out.deltaWorld.set(tWorld).subtractLocal(worldPos);
        out.positionSnapped = true;

        // texel delta in integer texels (from original x,y!)
        out.texelDx = Math.round((sx - x) / texel);
        out.texelDy = Math.round((sy - y) / texel);

        // confidence
        float maxPossible = threshold * FastMath.sqrt(2f);
        float dist = FastMath.sqrt(dx * dx + dy * dy);
        out.confidence = 1f - (dist / Math.max(1e-6f, maxPossible));

        cam.setLocation(tWorld);
        cam.update();
        return true;
    }

    private void updateMotion(int cascadeIdx, Camera cam, float dt) {
        if (!(dt > 0f)) return;

        Vector3f pos = cam.getLocation();
        Quaternion rot = cam.getRotation();

        Vector3f lastP = lastWorldPos[cascadeIdx];
        Quaternion lastR = lastRot[cascadeIdx];

        float dp = pos.distance(lastP);
        float posSpeed = dp / dt;

        // angular delta: angle = 2*acos(|dot(q1,q2)|)
        float dot = Math.abs(rot.dot(lastR));
        dot = FastMath.clamp(dot, -1f, 1f);
        float ang = 2f * FastMath.acos(dot);
        float angSpeed = ang / dt;

        float posW = Math.min(1f, posSpeed / 10f);         // 10 units/sec
        float angW = Math.min(1f, angSpeed / (FastMath.HALF_PI)); // 90°/sec
        float w = Math.max(posW, angW);

        float a = Math.min(1f, dt * 5f);
        motionWeight[cascadeIdx] = motionWeight[cascadeIdx] * (1f - a) + w * a;

        lastP.set(pos);
        lastR.set(rot);
    }

    public void resetHistory(int cascadeIdx) {
        if (cascadeIdx < 0 || cascadeIdx >= lastWorldPos.length) return;
        lastWorldPos[cascadeIdx].set(0, 0, 0);
        lastRot[cascadeIdx].set(0, 0, 0, 1);
        motionWeight[cascadeIdx] = 1f;
    }

    public void resetAllHistory() {
        for (int i = 0; i < lastWorldPos.length; i++) resetHistory(i);
    }

    public int getShadowMapSize() {
        return shadowMapSize;
    }

    public static final class Config {
        public boolean enablePositionSnap = true;
        public float positionThreshold = 0.5f;     // fraction of texel
        public float maxSnapDistanceTexels = 2.0f; // safety clamp
        public boolean adaptiveSnapping = true;
        public boolean conservative = true;
    }

    public static final class SnapResult {
        public final Vector3f deltaWorld = new Vector3f();
        public boolean positionSnapped;
        public int texelDx, texelDy;
        public float confidence;

        public void reset() {
            positionSnapped = false;
            deltaWorld.set(0, 0, 0);
            texelDx = texelDy = 0;
            confidence = 1f;
        }
    }
}