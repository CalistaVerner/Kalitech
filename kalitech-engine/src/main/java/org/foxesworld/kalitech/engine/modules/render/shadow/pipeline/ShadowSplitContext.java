// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/pipeline/ShadowSplitContext.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import com.jme3.light.DirectionalLight;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryList;

import java.util.Objects;

/**
 * Per-split shadow pipeline context.
 */
public final class ShadowSplitContext {

    public final ShadowFrameContext frame;

    public final int splitIndex;
    public final float splitNear;
    public final float splitFar;

    public final ViewPort viewPort;
    public final Camera viewCam;
    public final DirectionalLight light;

    public final Camera shadowCam;
    public final Vector3f[] frustumPoints;

    public final GeometryList receivers;
    public final GeometryList occluders;

    public final Vector3f lightDir = new Vector3f();
    public final Vector3f lightLeft = new Vector3f();
    public final Vector3f lightUp = new Vector3f();

    /**
     * Shared workspace view for this split (split-scope data).
     */
    public final ShadowWorkspace.SplitView ws;

    /**
     * If no filter handles camera update, orchestrator will call jME ShadowUtil.updateShadowCamera
     * with this stabilization size (0 disables).
     */
    public int stabilizationTexelSize = 0;

    /**
     * True if the shadow camera was fully handled by filters.
     */
    public boolean shadowCamHandled = false;

    /**
     * World-space size of one texel for this split.
     */
    public float texelWorld = 0f;

    /**
     * True if pipeline.updateShadowCam(...) returned true for this split.
     */
    public boolean handledCam = false;

    /**
     * True if TexelSnapFilter applied snap or hold-last-snap correction.
     */
    public boolean snapped = false;

    /**
     * True if texel snapping actually changed the shadow camera.
     */
    public boolean texelSnapped = false;

    public ShadowSplitContext(ShadowFrameContext frame,
                              int splitIndex,
                              float splitNear,
                              float splitFar,
                              Camera shadowCam,
                              Vector3f[] frustumPoints,
                              GeometryList receivers,
                              GeometryList occluders) {
        this.frame = Objects.requireNonNull(frame, "frame");
        this.splitIndex = splitIndex;
        this.splitNear = splitNear;
        this.splitFar = splitFar;
        this.viewPort = frame.viewPort;
        this.viewCam = frame.viewCam;
        this.light = frame.light;
        this.shadowCam = Objects.requireNonNull(shadowCam, "shadowCam");
        this.frustumPoints = Objects.requireNonNull(frustumPoints, "frustumPoints");
        this.receivers = Objects.requireNonNull(receivers, "receivers");
        this.occluders = Objects.requireNonNull(occluders, "occluders");
        this.ws = frame.ws.split(splitIndex);
    }
}