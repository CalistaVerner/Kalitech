// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/pipeline/ShadowPipeline.java
package org.foxesworld.kalitech.engine.modules.render.shadows.pipeline;

import java.util.*;

/**
 * Deterministic shadow processing pipeline.
 * <p>
 * Filters are executed in the order they are added.
 * Adding/removing filters is the ONLY supported way to change behavior.
 */
public final class ShadowPipeline {

    private final List<ShadowFilter> filters = new ArrayList<>(16);

    public List<ShadowFilter> filters() {
        return Collections.unmodifiableList(filters);
    }

    public ShadowPipeline clear() {
        for (ShadowFilter f : filters) {
            try {
                f.onRemoved(this);
            } catch (Throwable ignored) {
            }
        }
        filters.clear();
        return this;
    }

    public ShadowPipeline add(ShadowFilter f) {
        Objects.requireNonNull(f, "filter");
        filters.add(f);
        f.onAdded(this);
        return this;
    }

    public ShadowPipeline addFirst(ShadowFilter f) {
        Objects.requireNonNull(f, "filter");
        filters.add(0, f);
        f.onAdded(this);
        return this;
    }

    public ShadowPipeline addAll(Iterable<? extends ShadowFilter> fs) {
        Objects.requireNonNull(fs, "filters");
        for (ShadowFilter f : fs) add(f);
        return this;
    }

    public boolean remove(Class<? extends ShadowFilter> type) {
        Objects.requireNonNull(type, "type");
        for (Iterator<ShadowFilter> it = filters.iterator(); it.hasNext(); ) {
            ShadowFilter f = it.next();
            if (type.isInstance(f)) {
                it.remove();
                try {
                    f.onRemoved(this);
                } catch (Throwable ignored) {
                }
                return true;
            }
        }
        return false;
    }

    public <T extends ShadowFilter> T find(Class<T> type) {
        Objects.requireNonNull(type, "type");
        for (ShadowFilter f : filters) {
            if (type.isInstance(f)) return type.cast(f);
        }
        return null;
    }

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
