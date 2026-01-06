// FILE: org/foxesworld/kalitech/engine/modules/hud/HudCoords.java
package org.foxesworld.kalitech.engine.modules.hud;

/**
 * Coordinate conversions for HUD.
 *
 * Script contract:
 * - TOP-LEFT origin
 * - y grows DOWN
 *
 * Lemur/JME GUI:
 * - BOTTOM-LEFT origin
 * - y grows UP
 *
 * Important:
 * - "Box-like" elements (Panel/Container) in Lemur are positioned by BOTTOM-LEFT.
 * To place them by TOP-LEFT we must subtract their height.
 */
public final class HudCoords {
    private HudCoords() {
    }

    // viewport space (rooted)

    /**
     * Point-like element (Label): yTopLeft -> guiY
     */
    public static float toGuiYPoint(int vpH, float yTopLeft) {
        return (float) vpH - yTopLeft;
    }

    /**
     * Box-like element (Panel/Container): yTopLeft -> guiY with top pin (subtract height)
     */
    public static float toGuiYBox(int vpH, float yTopLeft, float elemH) {
        float H = (float) vpH;
        float h = (Float.isFinite(elemH) && elemH > 0f) ? elemH : 0f;
        return H - yTopLeft - h;
    }

    // parent-local space (child positioning)

    /**
     * Point-like child inside parent: localY = parentH - yTopLeft
     */
    public static float toLocalYPoint(float yTopLeft, float parentH) {
        if (Float.isFinite(parentH) && parentH > 0f) return parentH - yTopLeft;
        return -yTopLeft; // fallback if parent height unknown
    }

    /**
     * Box-like child inside parent: localY = parentH - yTopLeft - childH
     */
    public static float toLocalYBox(float yTopLeft, float parentH, float childH) {
        float ph = (Float.isFinite(parentH) && parentH > 0f) ? parentH : 0f;
        float ch = (Float.isFinite(childH) && childH > 0f) ? childH : 0f;
        if (ph > 0f) return ph - yTopLeft - ch;
        return -yTopLeft;
    }
}