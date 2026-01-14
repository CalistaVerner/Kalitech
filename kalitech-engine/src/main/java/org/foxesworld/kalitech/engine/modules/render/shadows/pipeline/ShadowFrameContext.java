// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/pipeline/ShadowFrameContext.java
package org.foxesworld.kalitech.engine.modules.render.shadows.pipeline;

import com.jme3.light.DirectionalLight;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

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
        // fit result (world space)
        public final Vector3f centerWS = new Vector3f();
        public float rangeNear, rangeFar;
        public float radius;

        // z fit relative
        public float zNearRel, zFarRel;

        // derived / debug
        public float texelWorldSize;
        public boolean quantized;

        public boolean snapped;
    }
}