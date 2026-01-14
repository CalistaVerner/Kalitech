// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/pipeline/ShadowFrameContext.java
package org.foxesworld.kalitech.engine.modules.render.shadows.pipeline;

import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.renderer.Camera;
import com.jme3.shadow.DirectionalLightShadowRenderer;

public final class ShadowFrameContext {

    // shared temps / basis
    public final Vector3f lightDir = new Vector3f();
    public final Matrix3f basis = new Matrix3f();

    // inputs
    public Camera viewCam;
    public DirectionalLight light;
    public float dt;
    public float cameraSpeed;

    public int cascades;
    public int mapSize;

    // split distances
    public float viewNear, viewFar;
    public float[] splitFarsWanted; // length=cascades
    public float[] splitFarsFinal;  // length=cascades

    // per-cascade data
    public CascadeData[] c;

    // material state for shadow receivers (filled by filters, applied by renderer)
    public final MaterialState material = new MaterialState();
    // per-cascade "current" pointers
    public int cascadeIndex = -1;
    public Camera shadowCam;
    // owner pointer (optional, allows filters to apply renderer-level settings)
    public DirectionalLightShadowRenderer renderer;

    public void ensure(int cascades) {
        this.cascades = cascades;

        if (splitFarsWanted == null || splitFarsWanted.length != cascades) splitFarsWanted = new float[cascades];
        if (splitFarsFinal == null || splitFarsFinal.length != cascades) splitFarsFinal = new float[cascades];

        if (c == null || c.length != cascades) {
            c = new CascadeData[cascades];
            for (int i = 0; i < cascades; i++) c[i] = new CascadeData();
        }
    }

    public static final class CascadeData {
        public final Vector3f centerWS = new Vector3f();
        public float rangeNear, rangeFar;
        public float radius;

        public float zNearRel, zFarRel;

        public float texelWorldSize;
        public boolean quantized;

        public boolean snapped;
    }

    public static final class MaterialState {
        public final Vector4f splitFars4 = new Vector4f();
        public float shadowBias = 0.0008f;
        public float shadowSlopeBias = 2.0f;
        public float shadowNormalOffset = 0.0f;
        public boolean cascadeBlendEnabled = true;
        public float cascadeBlendLen = 1.5f;

        /**
         * Applies state to a given material, only if params exist.
         */
        public void applyTo(Material material) {
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

        public void clearFrom(Material material) {
            if (material == null) return;

            if (material.getParam("ShadowBias") != null) material.clearParam("ShadowBias");
            if (material.getParam("ShadowSlopeBias") != null) material.clearParam("ShadowSlopeBias");
            if (material.getParam("ShadowNormalOffset") != null) material.clearParam("ShadowNormalOffset");

            if (material.getParam("CascadeBlendEnabled") != null) material.clearParam("CascadeBlendEnabled");
            if (material.getParam("CascadeBlendLen") != null) material.clearParam("CascadeBlendLen");

            if (material.getParam("ShadowSplitFars") != null) material.clearParam("ShadowSplitFars");
        }
    }
}