// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/pipeline/ShadowFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.pipeline;

/**
 * A deterministic stage in the shadow pipeline.
 * <p>
 * IMPORTANT: Filters must be controlled ONLY by being present/absent in the pipeline.
 * Do not add "enabled" flags – create/remove the filter instead.
 */
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
     * Called once per frame before any processing.
     */
    default void beginFrame(ShadowFrameContext ctx) {
    }

    /**
     * Called before split distances are produced.
     */
    default void beforeSplits(ShadowFrameContext ctx) {
    }

    /**
     * Called after split distances are produced.
     */
    default void afterSplits(ShadowFrameContext ctx) {
    }

    /**
     * Called before cascade fitting/placement for the given cascade index.
     */
    default void beforeCascade(ShadowFrameContext ctx, int cascade) {
    }

    /**
     * Called after cascade fitting stage (sphere fit / ortho extents etc.).
     */
    default void afterFit(ShadowFrameContext ctx, int cascade) {
    }

    /**
     * Called after snap stage (texel grid snapping).
     */
    default void afterSnap(ShadowFrameContext ctx, int cascade) {
    }

    /**
     * Called after shadow maps were rendered (good for temporal accumulation).
     */
    default void afterRender(ShadowFrameContext ctx) {
    }

    /**
     * Called once per frame at the end.
     */
    default void endFrame(ShadowFrameContext ctx) {
    }
}