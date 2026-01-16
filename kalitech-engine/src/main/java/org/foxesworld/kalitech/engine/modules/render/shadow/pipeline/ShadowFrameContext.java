// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/pipeline/ShadowFrameContext.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import com.jme3.light.DirectionalLight;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;

import java.util.Objects;

/**
 * Per-frame shadow pipeline context.
 */
public final class ShadowFrameContext {

    public final ViewPort viewPort;
    public final Camera viewCam;
    public final DirectionalLight light;

    public final int shadowMapSize;
    public final int numSplits;

    /**
     * Base renderer computed split distances (length = numSplits + 1).
     * Do not modify this array in filters unless you really know what you're doing.
     */
    public final float[] splits;

    public final long frameId;

    /**
     * Shared workspace for all filters. Provides immediate synchronization.
     */
    public final ShadowWorkspace ws;

    public ShadowFrameContext(ViewPort viewPort,
                              Camera viewCam,
                              DirectionalLight light,
                              int shadowMapSize,
                              int numSplits,
                              float[] splits,
                              long frameId) {
        this(viewPort, viewCam, light, shadowMapSize, numSplits, splits, frameId, null);
    }

    public ShadowFrameContext(ViewPort viewPort,
                              Camera viewCam,
                              DirectionalLight light,
                              int shadowMapSize,
                              int numSplits,
                              float[] splits,
                              long frameId,
                              ShadowWorkspace workspace) {
        this.viewPort = Objects.requireNonNull(viewPort, "viewPort");
        this.viewCam = Objects.requireNonNull(viewCam, "viewCam");
        this.light = Objects.requireNonNull(light, "light");
        this.shadowMapSize = shadowMapSize;
        this.numSplits = numSplits;
        this.splits = Objects.requireNonNull(splits, "splits");
        this.frameId = frameId;

        ShadowWorkspace w = workspace != null ? workspace : new ShadowWorkspace(numSplits);
        // AAA contract: prevent accidental multi-writes and hidden ordering dependencies.
        w.setStrictWrites(true);
        w.beginFrame(frameId);
        this.ws = w;
    }
}