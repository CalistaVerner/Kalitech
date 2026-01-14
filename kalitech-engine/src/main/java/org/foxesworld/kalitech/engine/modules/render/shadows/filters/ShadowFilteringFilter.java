// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/ShadowFilteringFilter.java
// Author: Calista Verner (K\u039bYL\u039b)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import org.foxesworld.kalitech.engine.modules.render.shadows.ShadowFilteringSystem;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

/**
 * Updates a {@link ShadowFilteringSystem} from the current frame state.
 * <p>
 * The filter itself is renderer-agnostic: it computes per-cascade filtering parameters
 * but does not push them into shader uniforms.
 * <p>
 * Presence in pipeline == enabled.
 */
public final class ShadowFilteringFilter implements ShadowFilter {

    private final ShadowFilteringSystem system;

    public ShadowFilteringFilter(int cascades) {
        this(cascades, null);
    }

    public ShadowFilteringFilter(int cascades, ShadowFilteringSystem.FilteringConfig cfg) {
        this.system = new ShadowFilteringSystem(
                Math.max(1, cascades),
                (cfg == null) ? new ShadowFilteringSystem.FilteringConfig() : cfg
        );
    }

    public ShadowFilteringSystem system() {
        return system;
    }

    @Override
    public String id() {
        return "ShadowFiltering";
    }

    @Override
    public void endFrame(ShadowFrameContext ctx) {
        system.update(ctx.dt, ctx.splitFarsFinal, ctx.cameraSpeed);
    }
}