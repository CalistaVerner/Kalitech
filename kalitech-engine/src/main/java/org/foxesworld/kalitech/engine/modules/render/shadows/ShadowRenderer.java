// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/ShadowRenderer.java
package org.foxesworld.kalitech.engine.modules.render.shadows;

import com.jme3.light.DirectionalLight;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

/**
 * Unified contract for all shadow renderers (stable / PCSS / future ones).
 * <p>
 * Goals:
 * - ShadowModule holds ONE field (ShadowRenderer) and never uses instanceof.
 * - clearShadows() must be safe/idempotent and must actually clear GPU shadow maps.
 * <p>
 * Threading:
 * - MUST be called on JME render thread.
 */
public interface ShadowRenderer {

    /**
     * Clears internal shadow maps / cached state (including GPU FBO clear).
     * After this call next render pass must rebuild shadows from scratch.
     */
    void clearShadows(RenderManager rm, ViewPort vp);

    void setLight(DirectionalLight light);

    void setLambda(float lambda);

    void setShadowIntensity(float shadowIntensity);
}