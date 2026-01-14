// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/StableLightBasis.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows;

import com.jme3.math.FastMath;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

public final class StableLightBasis {

    private static final Vector3f WORLD_UP = new Vector3f(0, 1, 0);
    private static final Vector3f WORLD_RIGHT = new Vector3f(1, 0, 0);
    private static final Vector3f WORLD_FORWARD = new Vector3f(0, 0, 1);
    private static final float EPS = 1e-6f;
    private final Config cfg;
    private final CascadeState[] states;
    private final Vector3f tRight = new Vector3f();
    private final Vector3f tUp = new Vector3f();
    private final Vector3f tDir = new Vector3f();
    private final Quaternion qTarget = new Quaternion();
    private final Quaternion qSmooth = new Quaternion();
    private final Vector3f[] axesTmp = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f()};
    public StableLightBasis(int cascades, Config config) {
        this.cfg = (config != null) ? config : new Config();
        int n = Math.max(1, cascades);
        this.states = new CascadeState[n];
        for (int i = 0; i < n; i++) this.states[i] = new CascadeState();
    }

    private static Vector3f projectOntoPlane(Vector3f v, Vector3f n, Vector3f out) {
        float d = v.dot(n);
        out.set(v.x - n.x * d, v.y - n.y * d, v.z - n.z * d);
        return out;
    }

    private static void negLocal(Vector3f v) {
        v.x = -v.x;
        v.y = -v.y;
        v.z = -v.z;
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

    public Config cfg() {
        return cfg;
    }

    public void computeBasis(int cascadeIdx, Vector3f dirNormalized, float dt, Matrix3f outBasis) {
        if (outBasis == null) return;

        if (cascadeIdx < 0) cascadeIdx = 0;
        if (cascadeIdx >= states.length) cascadeIdx = states.length - 1;

        CascadeState st = states[cascadeIdx];

        tDir.set(dirNormalized);
        normLocal(tDir);

        chooseDeterministicUp(tDir, tUp);
        tRight.set(tUp).crossLocal(tDir);
        if (tRight.lengthSquared() < EPS) {
            tRight.set(1, 0, 0);
        } else {
            normLocal(tRight);
        }
        tUp.set(tDir).crossLocal(tRight);
        normLocal(tUp);

        stabilize(st, tDir, tRight, tUp, dt);

        outBasis.setColumn(0, tRight);
        outBasis.setColumn(1, tUp);
        outBasis.setColumn(2, tDir);

        st.lastRight.set(tRight);
        st.lastUp.set(tUp);
        st.lastDir.set(tDir);
        st.lastRot.fromAxes(tRight, tUp, tDir);
    }

    private void stabilize(CascadeState st, Vector3f dir, Vector3f right, Vector3f up, float dt) {
        float rDot = right.dot(st.lastRight);
        float uDot = up.dot(st.lastUp);

        boolean rFlip = rDot < -cfg.flipThreshold;
        boolean uFlip = uDot < -cfg.flipThreshold;

        if (rFlip) negLocal(right);
        if (uFlip) negLocal(up);

        if (cfg.enableRollSnap) {
            applyRollSnap(dir, right, up, dt);
        }

        if (cfg.smoothingFactor > 0f && dt > 0f) {
            float a = cfg.smoothingFactor * Math.min(1f, dt * 60f);
            qTarget.fromAxes(right, up, dir);
            qSmooth.slerp(st.lastRot, qTarget, a);

            axesTmp[0].set(right);
            axesTmp[1].set(up);
            axesTmp[2].set(dir);
            qSmooth.toAxes(axesTmp);

            right.set(axesTmp[0]);
            up.set(axesTmp[1]);
            dir.set(axesTmp[2]);
        }
    }

    private void applyRollSnap(Vector3f dir, Vector3f right, Vector3f up, float dt) {
        float a = FastMath.clamp(cfg.rollSnapRate * dt, 0f, 1f);

        Vector3f refUp = cfg.useWorldUp ? WORLD_UP : cfg.customUp;
        Vector3f proj = projectOntoPlane(refUp, dir, new Vector3f());
        if (proj.lengthSquared() < EPS) return;
        normLocal(proj);

        Vector3f r2 = new Vector3f(proj).crossLocal(dir);
        if (r2.lengthSquared() < EPS) return;
        normLocal(r2);

        Vector3f u2 = new Vector3f(dir).crossLocal(r2);
        if (u2.lengthSquared() < EPS) return;
        normLocal(u2);

        right.interpolateLocal(r2, a);
        up.interpolateLocal(u2, a);
        normLocal(right);
        normLocal(up);
    }

    private void chooseDeterministicUp(Vector3f dir, Vector3f outUp) {
        final Vector3f refUp = cfg.useWorldUp ? WORLD_UP : cfg.customUp;

        float dot = Math.abs(dir.dot(refUp));
        if (dot > 0.9999f) {
            Vector3f alt = (Math.abs(dir.dot(WORLD_RIGHT)) < 0.9999f) ? WORLD_RIGHT : WORLD_FORWARD;
            projectOntoPlane(alt, dir, outUp);
            normLocal(outUp);
            return;
        }
        projectOntoPlane(refUp, dir, outUp);
        normLocal(outUp);
    }

    public static final class Config {
        public final Vector3f customUp = new Vector3f(0, 1, 0);
        public float flipThreshold = 0.98f;
        public boolean enableRollSnap = true;
        public float rollSnapRate = 16f;
        public float smoothingFactor = 0.0f;
        public boolean useWorldUp = true;
    }

    private static final class CascadeState {
        final Vector3f lastRight = new Vector3f(1, 0, 0);
        final Vector3f lastUp = new Vector3f(0, 1, 0);
        final Vector3f lastDir = new Vector3f(0, 0, 1);
        final Quaternion lastRot = new Quaternion();
    }
}