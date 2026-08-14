/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.material.Material
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import com.jme3.material.Material;
import java.util.Set;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKey;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public interface ShadowFilter {
    default public Set<ShadowKey<?>> requires() {
        return Set.of();
    }

    default public Set<ShadowKey<?>> provides() {
        return Set.of();
    }

    default public int order() {
        return 0;
    }

    default public void beginFrame(ShadowFrameContext ctx) {
    }

    default public void endFrame(ShadowFrameContext ctx) {
    }

    default public void beginSplit(ShadowSplitContext ctx) {
    }

    default public void endSplit(ShadowSplitContext ctx) {
    }

    default public boolean updateShadowCam(ShadowSplitContext ctx) {
        return false;
    }

    default public void afterShadowCam(ShadowSplitContext ctx) {
    }

    default public void beforeGatherOccluders(ShadowSplitContext ctx) {
    }

    default public void afterGatherOccluders(ShadowSplitContext ctx) {
    }

    default public void setMaterialParameters(ShadowFrameContext ctx, Material material) {
    }

    default public void clearMaterialParameters(ShadowFrameContext ctx, Material material) {
    }
}

