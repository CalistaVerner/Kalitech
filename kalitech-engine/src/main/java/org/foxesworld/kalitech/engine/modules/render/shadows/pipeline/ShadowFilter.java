// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/pipeline/ShadowFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.pipeline;

public interface ShadowFilter {

    default String id() {
        return getClass().getSimpleName();
    }

    /**
     * Called once when filter is added to pipeline.
     */
    default void onAdded(ShadowPipeline pipeline) {
    }

    /**
     * Called once when filter is removed from pipeline.
     */
    default void onRemoved(ShadowPipeline pipeline) {
    }

    /**
     * Called once per frame at the start of shadow update.
     */
    default void beginFrame(ShadowFrameContext ctx) {
    }

    /**
     * Called before split computation is finalized. You may write ctx.splitFarsFinal.
     */
    default void beforeSplits(ShadowFrameContext ctx) {
    }

    /**
     * Called after split fars are finalized.
     */
    default void afterSplits(ShadowFrameContext ctx) {
    }

    /**
     * Per-cascade hook (before fitting / before camera placement).
     */
    default void beforeCascade(ShadowFrameContext ctx, int cascade) {
    }

    /**
     * Per-cascade hook after raw fit (sphere/frustum fit is known) – can modify radius/center/z.
     */
    default void afterFit(ShadowFrameContext ctx, int cascade) {
    }

    /**
     * Per-cascade hook after shadow camera was placed and snapped (if used).
     */
    default void afterSnap(ShadowFrameContext ctx, int cascade) {
    }

    /**
     * Called after shadow maps were rendered (good for temporal accumulation).
     */
    default void afterRender(ShadowFrameContext ctx) {
    }

    /**
     * Called once per frame.
     */
    default void endFrame(ShadowFrameContext ctx) {
    }
}