// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/ShadowTunable.java
package org.foxesworld.kalitech.engine.modules.render.shadows;

import org.apache.logging.log4j.Logger;

/**
 * Optional capabilities for advanced shadow renderers.
 * ShadowModule talks ONLY to this interface (no instanceof, no casts to concrete classes).
 */
public interface ShadowTunable extends ShadowRenderer {

    void setSnapEnabled(boolean enabled);

    void setExtentsPadding(float padding);

    void setSplitDistances(float... distances);

    void setShadowBias(float bias);

    void setShadowSlopeBias(float slopeBias);

    void setShadowNormalOffset(float normalOffset);

    void setCascadeBlendEnabled(boolean enabled);

    void setCascadeBlendLength(float worldUnits);

    void setDebug(Logger log, boolean enabled, int everyFrames);
}