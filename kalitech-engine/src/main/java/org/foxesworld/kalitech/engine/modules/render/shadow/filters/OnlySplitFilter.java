// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/OnlySplitFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.material.Material;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

import java.util.Objects;

/**
 * Wrapper filter that applies an inner filter only for selected cascades (splits).
 */
public final class OnlySplitFilter implements ShadowFilter {

    public ShadowFilter inner;
    public int fromSplitInclusive = 0;
    public int toSplitExclusive = Integer.MAX_VALUE;

    public OnlySplitFilter() {
    }

    public OnlySplitFilter(ShadowFilter inner, int fromSplitInclusive, int toSplitExclusive) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.fromSplitInclusive = fromSplitInclusive;
        this.toSplitExclusive = toSplitExclusive;
    }

    @Override
    public int order() {
        return inner != null ? inner.order() : 0;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (inner != null) inner.beginFrame(ctx);
    }

    @Override
    public void endFrame(ShadowFrameContext ctx) {
        if (inner != null) inner.endFrame(ctx);
    }

    @Override
    public void beginSplit(ShadowSplitContext ctx) {
        if (inner != null && active(ctx)) inner.beginSplit(ctx);
    }

    @Override
    public boolean updateShadowCam(ShadowSplitContext ctx) {
        if (inner != null && active(ctx)) return inner.updateShadowCam(ctx);
        return false;
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (inner != null && active(ctx)) inner.afterShadowCam(ctx);
    }

    @Override
    public void beforeGatherOccluders(ShadowSplitContext ctx) {
        if (inner != null && active(ctx)) inner.beforeGatherOccluders(ctx);
    }

    @Override
    public void afterGatherOccluders(ShadowSplitContext ctx) {
        if (inner != null && active(ctx)) inner.afterGatherOccluders(ctx);
    }

    @Override
    public void endSplit(ShadowSplitContext ctx) {
        if (inner != null && active(ctx)) inner.endSplit(ctx);
    }

    @Override
    public void setMaterialParameters(ShadowFrameContext ctx, Material material) {
        if (inner != null) inner.setMaterialParameters(ctx, material);
    }

    @Override
    public void clearMaterialParameters(ShadowFrameContext ctx, Material material) {
        if (inner != null) inner.clearMaterialParameters(ctx, material);
    }

    private boolean active(ShadowSplitContext ctx) {
        int i = ctx.splitIndex;
        return i >= fromSplitInclusive && i < toSplitExclusive;
    }

    public void setInner(ShadowFilter inner) {
        this.inner = inner;
    }

    public void setFromSplitInclusive(int fromSplitInclusive) {
        this.fromSplitInclusive = fromSplitInclusive;
    }

    public void setToSplitExclusive(int toSplitExclusive) {
        this.toSplitExclusive = toSplitExclusive;
    }
}