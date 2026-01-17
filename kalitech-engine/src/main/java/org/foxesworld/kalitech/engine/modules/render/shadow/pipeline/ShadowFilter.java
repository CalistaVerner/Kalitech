// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/pipeline/ShadowFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import com.jme3.material.Material;

import java.util.Set;

/**
 * Shadow pipeline filter hook.
 * <p>
 * The orchestrator renderer must not embed shadow quality logic. All behavior
 * changes must be implemented via filters.
 */
public interface ShadowFilter {

    /**
     * Declares keys required by this filter to operate correctly.
     * <p>
     * This is a pipeline contract used for validation and debugging.
     * Filters should include both frame-scope and split-scope keys.
     */
    default Set<ShadowKey<?>> requires() {
        return Set.of();
    }

    /**
     * Declares keys produced by this filter.
     * <p>
     * This is a pipeline contract used for validation and debugging.
     * Filters should include both frame-scope and split-scope keys.
     */
    default Set<ShadowKey<?>> provides() {
        return Set.of();
    }

    /**
     * Lower runs earlier.
     */
    default int order() {
        return 0;
    }

    default void beginFrame(ShadowFrameContext ctx) {
    }

    default void endFrame(ShadowFrameContext ctx) {
    }

    default void beginSplit(ShadowSplitContext ctx) {
    }

    default void endSplit(ShadowSplitContext ctx) {
    }

    /**
     * If returns true, the filter fully handled shadow camera update for this split
     * and the orchestrator must skip the default jME updateShadowCamera path.
     */
    default boolean updateShadowCam(ShadowSplitContext ctx) {
        return false;
    }

    /**
     * Called after the shadow camera is finalized (either by filters or default path),
     * before occluder gather/cull.
     * <p>
     * Typical usage: texel snapping, final basis enforcement, depth-range clamps.
     */
    default void afterShadowCam(ShadowSplitContext ctx) {
    }

    default void beforeGatherOccluders(ShadowSplitContext ctx) {
    }

    default void afterGatherOccluders(ShadowSplitContext ctx) {
    }

    default void setMaterialParameters(ShadowFrameContext ctx, Material material) {
    }

    default void clearMaterialParameters(ShadowFrameContext ctx, Material material) {
    }
}