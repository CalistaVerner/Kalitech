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
        for (ShadowFilter f : filters) f.beginFrame(ctx);
    }

    public void endFrame(ShadowFrameContext ctx) {
        sortIfNeeded();
        for (int i = filters.size() - 1; i >= 0; i--) filters.get(i).endFrame(ctx);
    }

    public void beginSplit(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (ShadowFilter f : filters) f.beginSplit(ctx);
    }

    public boolean updateShadowCam(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (ShadowFilter f : filters) {
            if (f.updateShadowCam(ctx)) return true;
        }
        return false;
    }

    public void afterShadowCam(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (ShadowFilter f : filters) f.afterShadowCam(ctx);
    }

    public void beforeGatherOccluders(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (ShadowFilter f : filters) f.beforeGatherOccluders(ctx);
    }

    public void afterGatherOccluders(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (ShadowFilter f : filters) f.afterGatherOccluders(ctx);
    }

    public void endSplit(ShadowSplitContext ctx) {
        sortIfNeeded();
        for (int i = filters.size() - 1; i >= 0; i--) filters.get(i).endSplit(ctx);
    }

    public void setMaterialParameters(ShadowFrameContext ctx, Material material) {
        sortIfNeeded();
        for (ShadowFilter f : filters) f.setMaterialParameters(ctx, material);
    }

    public void clearMaterialParameters(ShadowFrameContext ctx, Material material) {
        sortIfNeeded();
        for (int i = filters.size() - 1; i >= 0; i--) filters.get(i).clearMaterialParameters(ctx, material);
    }

    private void sortIfNeeded() {
        if (!sorted) {
            filters.sort(Comparator.comparingInt(ShadowFilter::order));
            sorted = true;
        }
    }
}