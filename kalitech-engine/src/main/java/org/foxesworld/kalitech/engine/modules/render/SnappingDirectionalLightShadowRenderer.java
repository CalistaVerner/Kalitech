// FILE: org/foxesworld/kalitech/engine/modules/render/SnappingDirectionalLightShadowRenderer.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.asset.AssetManager;
import com.jme3.renderer.Camera;
import com.jme3.shadow.DirectionalLightShadowRenderer;

/**
 * DirectionalLightShadowRenderer with texel snapping hook.
 *
 * Goal: stabilize shadow projection against camera sub-texel movement.
 * Also emits reason logs: if snapping stable but shimmer persists -> bias/PCF/cascade transitions.
 */
public final class SnappingDirectionalLightShadowRenderer extends DirectionalLightShadowRenderer {

    private ShadowSnapper snapper;
    private boolean snapEnabled = true;

    private boolean debugEnabled = false;
    private int debugEveryFrames = 120;
    private int debugSnapIntervalMs = 500;

    private int frame = 0;

    public SnappingDirectionalLightShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
        super(assets, shadowMapSize, nbSplits);
    }

    public void setSnapper(ShadowSnapper snapper) {
        this.snapper = snapper;
        if (this.snapper != null) this.snapper.setShadowMapSize(getShadowMapSizeSafe());
    }

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
    }

    public void setStabilizeExtents(boolean stabilize) {
        if (snapper != null) snapper.setStabilizeExtents(stabilize);
    }

    public void setExtentsPadding(float padding) {
        if (snapper != null) snapper.setExtentsPadding(padding);
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        if (snapper != null) snapper.setDebugEnabled(enabled);
    }

    public void setDebugEveryFrames(int frames) {
        if (frames < 1) frames = 1;
        this.debugEveryFrames = frames;
    }

    public void setDebugSnapIntervalMs(int ms) {
        if (ms < 50) ms = 50;
        this.debugSnapIntervalMs = ms;
        if (snapper != null) snapper.setDebugIntervalMs(ms);
    }


    @Override
    public void preFrame(float tpf) {
        // IMPORTANT: snapping must be applied before shadows are rendered
        // so we do it here, after JME updated shadowCam during last frame.
        // DirectionalLightShadowRenderer will update shadow cams later in the frame as needed,
        // but in practice preFrame is the safest hook point we control per-frame.
        // (If you notice it’s still late, we can move it to a deeper override in your fork.)

        if (snapEnabled && snapper != null) {
            final Camera shadowCam = getShadowCamSafe();
            if (shadowCam != null) {
                snapper.setShadowMapSize(getShadowMapSizeSafe());
                snapper.setDebugEnabled(debugEnabled);
                snapper.setDebugIntervalMs(debugSnapIntervalMs);
                snapper.snap(shadowCam);
            }
        }

        frame++;
        super.preFrame(tpf);

        // Optional cadence probe (kept light)
        if (debugEnabled && (frame % debugEveryFrames == 0)) {
            // snapper already logs at its own interval; nothing else needed here
        }
    }

    /**
     * JME internals differ across versions; keep safe accessors.
     */
    private Camera getShadowCamSafe() {
        try {
            // DirectionalLightShadowRenderer exposes shadowCam in many JME versions via protected field.
            // If your version differs, you can replace this with an explicit accessor in your fork.
            return this.shadowCam;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int getShadowMapSizeSafe() {
        try {
            return getShadowMapSize();
        } catch (Throwable ignored) {
            return 2048;
        }
    }
}