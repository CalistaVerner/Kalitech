// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/ShadowTunable.java
package org.foxesworld.kalitech.engine.modules.render.shadows;

import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowPipeline;

/**
 * Optional capabilities for advanced shadow renderers.
 * <p>
 * Design goals:
 * <ul>
 *   <li>No "manual effects": all quality/bias/PCF/PCSS/blend tweaks are applied via pipeline filters.</li>
 *   <li>The renderer exposes only operational controls and the pipeline access point.</li>
 * </ul>
 * <p>
 * Threading:
 * MUST be called on JME render thread.
 */
public interface ShadowTunable extends ShadowRenderer {

    /**
     * Returns the shadow pipeline used by this renderer.
     * Filters must be attached/removed only through this pipeline.
     */
    ShadowPipeline pipeline();

    /**
     * Enables lightweight debug logging (optional).
     */
    void setDebug(Logger log, boolean enabled, int everyFrames);
}
