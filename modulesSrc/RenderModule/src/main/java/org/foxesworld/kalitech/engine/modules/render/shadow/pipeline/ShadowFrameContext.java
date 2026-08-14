/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.light.DirectionalLight
 *  com.jme3.renderer.Camera
 *  com.jme3.renderer.ViewPort
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import com.jme3.light.DirectionalLight;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import java.util.Objects;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowWorkspace;

public final class ShadowFrameContext {
    public final ViewPort viewPort;
    public final Camera viewCam;
    public final DirectionalLight light;
    public final int shadowMapSize;
    public final int numSplits;
    public final float[] splits;
    public final long frameId;
    public final ShadowWorkspace ws;

    public ShadowFrameContext(ViewPort viewPort, Camera viewCam, DirectionalLight light, int shadowMapSize, int numSplits, float[] splits, long frameId) {
        this(viewPort, viewCam, light, shadowMapSize, numSplits, splits, frameId, null);
    }

    public ShadowFrameContext(ViewPort viewPort, Camera viewCam, DirectionalLight light, int shadowMapSize, int numSplits, float[] splits, long frameId, ShadowWorkspace workspace) {
        this.viewPort = Objects.requireNonNull(viewPort, "viewPort");
        this.viewCam = Objects.requireNonNull(viewCam, "viewCam");
        this.light = Objects.requireNonNull(light, "light");
        this.shadowMapSize = shadowMapSize;
        this.numSplits = numSplits;
        this.splits = Objects.requireNonNull(splits, "splits");
        this.frameId = frameId;
        ShadowWorkspace w = workspace != null ? workspace : new ShadowWorkspace(numSplits);
        w.setStrictWrites(true);
        w.beginFrame(frameId);
        this.ws = w;
    }
}

