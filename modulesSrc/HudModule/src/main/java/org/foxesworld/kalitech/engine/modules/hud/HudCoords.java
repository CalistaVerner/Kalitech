/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.hud;

public final class HudCoords {
    private HudCoords() {
    }

    public static float toGuiYPoint(int vpH, float yTopLeft) {
        return (float)vpH - yTopLeft;
    }

    public static float toGuiYBox(int vpH, float yTopLeft, float elemH) {
        float H = vpH;
        float h = Float.isFinite(elemH) && elemH > 0.0f ? elemH : 0.0f;
        return H - yTopLeft - h;
    }

    public static float toLocalYPoint(float yTopLeft, float parentH) {
        if (Float.isFinite(parentH) && parentH > 0.0f) {
            return parentH - yTopLeft;
        }
        return -yTopLeft;
    }

    public static float toLocalYBox(float yTopLeft, float parentH, float childH) {
        float ch;
        float ph = Float.isFinite(parentH) && parentH > 0.0f ? parentH : 0.0f;
        float f = ch = Float.isFinite(childH) && childH > 0.0f ? childH : 0.0f;
        if (ph > 0.0f) {
            return ph - yTopLeft - ch;
        }
        return -yTopLeft;
    }
}

