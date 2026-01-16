// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/pipeline/ShadowPipeline.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import com.jme3.material.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Ordered filter pipeline.
 */
public final class ShadowPipeline {

    private final ArrayList<ShadowFilter> filters = new ArrayList<>();
    private boolean sorted = true;

    public ShadowPipeline add(ShadowFilter f) {
        filters.add(Objects.requireNonNull(f, "filter"));
        sorted = false;
        return this;
    }

    public void clear() {
        filters.clear();
        sorted = true;
    }

    public List<ShadowFilter> snapshot() {
        sortIfNeeded();
        return List.copyOf(filters);
    }

    public void beginFrame(ShadowFrameContext ctx) {
        sortIfNeeded();
        for (ShadowFilter f : filters) {
            ctx.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.beginFrame(ctx);
        }
        ctx.ws.clearCurrentWriter();
    }

    public void endFrame(ShadowFrameContext ctx) {
        sortIfNeeded();
        for (int i = filters.size() - 1; i >= 0; i--) {
            ShadowFilter f = filters.get(i);
            ctx.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.endFrame(ctx);
        }
        ctx.ws.clearCurrentWriter();
    }

    public void beginSplit(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (ShadowFilter f : filters) {
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.beginSplit(ctx);
        }
        ctx.frame.ws.clearCurrentWriter();
    }

    public boolean updateShadowCam(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (ShadowFilter f : filters) {
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            if (f.updateShadowCam(ctx)) {
                ctx.frame.ws.clearCurrentWriter();
                return true;
            }
        }
        ctx.frame.ws.clearCurrentWriter();
        return false;
    }

    public void afterShadowCam(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (ShadowFilter f : filters) {
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.afterShadowCam(ctx);
        }
        ctx.frame.ws.clearCurrentWriter();
    }

    public void beforeGatherOccluders(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (ShadowFilter f : filters) {
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.beforeGatherOccluders(ctx);
        }
        ctx.frame.ws.clearCurrentWriter();
    }

    public void afterGatherOccluders(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (ShadowFilter f : filters) {
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.afterGatherOccluders(ctx);
        }
        ctx.frame.ws.clearCurrentWriter();
    }

    public void endSplit(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (int i = filters.size() - 1; i >= 0; i--) {
            ShadowFilter f = filters.get(i);
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.endSplit(ctx);
        }
        ctx.frame.ws.clearCurrentWriter();
    }

    public void setMaterialParameters(ShadowFrameContext ctx, Material material) {
        sortIfNeeded();
        for (ShadowFilter f : filters) {
            ctx.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.setMaterialParameters(ctx, material);
        }
        ctx.ws.clearCurrentWriter();
    }

    public void clearMaterialParameters(ShadowFrameContext ctx, Material material) {
        sortIfNeeded();
        for (int i = filters.size() - 1; i >= 0; i--) {
            ShadowFilter f = filters.get(i);
            ctx.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.clearMaterialParameters(ctx, material);
        }
        ctx.ws.clearCurrentWriter();
    }

    private void sortIfNeeded() {
        if (!sorted) {
            filters.sort(Comparator.comparingInt(ShadowFilter::order));
            sorted = true;
        }
    }
}