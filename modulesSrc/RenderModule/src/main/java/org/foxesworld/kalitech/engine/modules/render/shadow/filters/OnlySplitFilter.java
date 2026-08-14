/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.material.Material
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.material.Material;
import java.util.Objects;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class OnlySplitFilter
implements ShadowFilter {
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
        return this.inner != null ? this.inner.order() : 0;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (this.inner != null) {
            this.inner.beginFrame(ctx);
        }
    }

    @Override
    public void endFrame(ShadowFrameContext ctx) {
        if (this.inner != null) {
            this.inner.endFrame(ctx);
        }
    }

    @Override
    public void beginSplit(ShadowSplitContext ctx) {
        if (this.inner != null && this.active(ctx)) {
            this.inner.beginSplit(ctx);
        }
    }

    @Override
    public boolean updateShadowCam(ShadowSplitContext ctx) {
        if (this.inner != null && this.active(ctx)) {
            return this.inner.updateShadowCam(ctx);
        }
        return false;
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (this.inner != null && this.active(ctx)) {
            this.inner.afterShadowCam(ctx);
        }
    }

    @Override
    public void beforeGatherOccluders(ShadowSplitContext ctx) {
        if (this.inner != null && this.active(ctx)) {
            this.inner.beforeGatherOccluders(ctx);
        }
    }

    @Override
    public void afterGatherOccluders(ShadowSplitContext ctx) {
        if (this.inner != null && this.active(ctx)) {
            this.inner.afterGatherOccluders(ctx);
        }
    }

    @Override
    public void endSplit(ShadowSplitContext ctx) {
        if (this.inner != null && this.active(ctx)) {
            this.inner.endSplit(ctx);
        }
    }

    @Override
    public void setMaterialParameters(ShadowFrameContext ctx, Material material) {
        if (this.inner != null) {
            this.inner.setMaterialParameters(ctx, material);
        }
    }

    @Override
    public void clearMaterialParameters(ShadowFrameContext ctx, Material material) {
        if (this.inner != null) {
            this.inner.clearMaterialParameters(ctx, material);
        }
    }

    private boolean active(ShadowSplitContext ctx) {
        int i = ctx.splitIndex;
        return i >= this.fromSplitInclusive && i < this.toSplitExclusive;
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

