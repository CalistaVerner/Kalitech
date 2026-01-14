// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/pipeline/ShadowPipeline.java
package org.foxesworld.kalitech.engine.modules.render.shadows.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShadowPipeline {

    private final List<ShadowFilter> filters = new ArrayList<>();

    public List<ShadowFilter> filters() {
        return Collections.unmodifiableList(filters);
    }

    public ShadowPipeline add(ShadowFilter f) {
        if (f == null) return this;
        filters.add(f);
        f.onAdded(this);
        return this;
    }

    public ShadowPipeline remove(ShadowFilter f) {
        if (f == null) return this;
        if (filters.remove(f)) f.onRemoved(this);
        return this;
    }

    public void clear() {
        for (ShadowFilter f : filters) f.onRemoved(this);
        filters.clear();
    }

    // stage dispatch
    public void beginFrame(ShadowFrameContext ctx) {
        for (ShadowFilter f : filters) f.beginFrame(ctx);
    }

    public void beforeSplits(ShadowFrameContext ctx) {
        for (ShadowFilter f : filters) f.beforeSplits(ctx);
    }

    public void afterSplits(ShadowFrameContext ctx) {
        for (ShadowFilter f : filters) f.afterSplits(ctx);
    }

    public void beforeCascade(ShadowFrameContext ctx, int i) {
        for (ShadowFilter f : filters) f.beforeCascade(ctx, i);
    }

    public void afterFit(ShadowFrameContext ctx, int i) {
        for (ShadowFilter f : filters) f.afterFit(ctx, i);
    }

    public void afterSnap(ShadowFrameContext ctx, int i) {
        for (ShadowFilter f : filters) f.afterSnap(ctx, i);
    }

    public void afterRender(ShadowFrameContext ctx) {
        for (ShadowFilter f : filters) f.afterRender(ctx);
    }

    public void endFrame(ShadowFrameContext ctx) {
        for (ShadowFilter f : filters) f.endFrame(ctx);
    }
}