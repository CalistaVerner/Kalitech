/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.light.DirectionalLight
 *  com.jme3.math.Vector3f
 *  com.jme3.renderer.Camera
 *  com.jme3.renderer.ViewPort
 *  com.jme3.renderer.queue.GeometryList
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import com.jme3.light.DirectionalLight;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryList;
import java.util.Objects;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowWorkspace;

public final class ShadowSplitContext {
    public final ShadowFrameContext frame;
    public final ShadowWorkspace.SplitView ws;
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
    public float stabilizationTexelSize = 0.0f;
    public boolean shadowCamHandled = false;
    public float texelWorld = 0.0f;
    public boolean handledCam = false;
    public boolean snapped = false;
    public boolean texelSnapped = false;

    public ShadowSplitContext(ShadowFrameContext frame, int splitIndex, float splitNear, float splitFar, Camera shadowCam, Vector3f[] frustumPoints, GeometryList receivers, GeometryList occluders) {
        this.frame = Objects.requireNonNull(frame, "frame");
        this.ws = frame.ws.split(splitIndex);
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
    }
}

