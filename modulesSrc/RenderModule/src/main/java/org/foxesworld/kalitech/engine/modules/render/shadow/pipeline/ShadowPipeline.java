/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.material.Material
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import com.jme3.material.Material;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKey;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class ShadowPipeline {
    private static final Logger log = LogManager.getLogger(ShadowPipeline.class);
    private final ArrayList<ShadowFilter> filters = new ArrayList();
    private boolean sorted = true;

    public void validate(ValidationMode mode, Set<ShadowKey<?>> externallyProvided) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(externallyProvided, "externallyProvided");
        if (mode == ValidationMode.NONE) {
            return;
        }
        this.sortIfNeeded();
        HashSet provided = new HashSet(externallyProvided);
        for (ShadowFilter f : this.filters) {
            for (ShadowKey<?> req : f.requires()) {
                if (req == null || provided.contains(req)) continue;
                String msg = "ShadowPipeline contract violation: missing required key=" + String.valueOf(req) + " requiredBy=" + f.getClass().getName();
                if (mode == ValidationMode.STRICT) {
                    throw new IllegalStateException(msg);
                }
                log.warn("[shadow][pipe] {}", (Object)msg);
            }
            for (ShadowKey<?> prov : f.provides()) {
                if (prov == null) continue;
                provided.add(prov);
            }
        }
    }

    public ShadowPipeline add(ShadowFilter f) {
        this.filters.add(Objects.requireNonNull(f, "filter"));
        this.sorted = false;
        return this;
    }

    public void clear() {
        this.filters.clear();
        this.sorted = true;
    }

    public List<ShadowFilter> snapshot() {
        this.sortIfNeeded();
        return List.copyOf(this.filters);
    }

    public void validate(ValidationMode mode) {
        this.validate(mode, Set.of());
    }

    public void beginFrame(ShadowFrameContext ctx) {
        this.sortIfNeeded();
        for (ShadowFilter f : this.filters) {
            ctx.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.beginFrame(ctx);
        }
        ctx.ws.clearCurrentWriter();
    }

    public void endFrame(ShadowFrameContext ctx) {
        this.sortIfNeeded();
        for (int i = this.filters.size() - 1; i >= 0; --i) {
            ShadowFilter f = this.filters.get(i);
            ctx.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.endFrame(ctx);
        }
        ctx.ws.clearCurrentWriter();
    }

    public void beginSplit(ShadowSplitContext ctx) {
        this.sortIfNeeded();
        for (ShadowFilter f : this.filters) {
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.beginSplit(ctx);
        }
        ctx.frame.ws.clearCurrentWriter();
    }

    public boolean updateShadowCam(ShadowSplitContext ctx) {
        this.sortIfNeeded();
        for (ShadowFilter f : this.filters) {
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            if (!f.updateShadowCam(ctx)) continue;
            ctx.frame.ws.clearCurrentWriter();
            return true;
        }
        ctx.frame.ws.clearCurrentWriter();
        return false;
    }

    public void afterShadowCam(ShadowSplitContext ctx) {
        this.sortIfNeeded();
        for (ShadowFilter f : this.filters) {
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.afterShadowCam(ctx);
        }
        ctx.frame.ws.clearCurrentWriter();
    }

    public void beforeGatherOccluders(ShadowSplitContext ctx) {
        this.sortIfNeeded();
        for (ShadowFilter f : this.filters) {
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.beforeGatherOccluders(ctx);
        }
        ctx.frame.ws.clearCurrentWriter();
    }

    public void afterGatherOccluders(ShadowSplitContext ctx) {
        this.sortIfNeeded();
        for (ShadowFilter f : this.filters) {
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.afterGatherOccluders(ctx);
        }
        ctx.frame.ws.clearCurrentWriter();
    }

    public void endSplit(ShadowSplitContext ctx) {
        this.sortIfNeeded();
        for (int i = this.filters.size() - 1; i >= 0; --i) {
            ShadowFilter f = this.filters.get(i);
            ctx.frame.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.endSplit(ctx);
        }
        ctx.frame.ws.clearCurrentWriter();
    }

    public void setMaterialParameters(ShadowFrameContext ctx, Material material) {
        this.sortIfNeeded();
        for (ShadowFilter f : this.filters) {
            ctx.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.setMaterialParameters(ctx, material);
        }
        ctx.ws.clearCurrentWriter();
    }

    public void clearMaterialParameters(ShadowFrameContext ctx, Material material) {
        this.sortIfNeeded();
        for (int i = this.filters.size() - 1; i >= 0; --i) {
            ShadowFilter f = this.filters.get(i);
            ctx.ws.setCurrentWriter(f.getClass().getName().hashCode());
            f.clearMaterialParameters(ctx, material);
        }
        ctx.ws.clearCurrentWriter();
    }

    private void sortIfNeeded() {
        if (!this.sorted) {
            this.filters.sort(Comparator.comparingInt(ShadowFilter::order));
            this.sorted = true;
        }
    }

    public static enum ValidationMode {
        NONE,
        WARN,
        STRICT;

    }
}

