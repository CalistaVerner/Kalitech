// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudLayout.java
package org.foxesworld.kalitech.engine.api.impl.hud;

final class HudLayout {

    enum Anchor {
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, CENTER, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }

    enum Pivot {
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, CENTER, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }

    static Anchor anchorOf(String s) {
        if (s == null) return Anchor.TOP_LEFT;
        return switch (s) {
            case "topLeft" -> Anchor.TOP_LEFT;
            case "top" -> Anchor.TOP;
            case "topRight" -> Anchor.TOP_RIGHT;
            case "left" -> Anchor.LEFT;
            case "center" -> Anchor.CENTER;
            case "right" -> Anchor.RIGHT;
            case "bottomLeft" -> Anchor.BOTTOM_LEFT;
            case "bottom" -> Anchor.BOTTOM;
            case "bottomRight" -> Anchor.BOTTOM_RIGHT;
            default -> Anchor.TOP_LEFT;
        };
    }

    static Pivot pivotOf(String s) {
        if (s == null) return Pivot.TOP_LEFT;
        return switch (s) {
            case "topLeft" -> Pivot.TOP_LEFT;
            case "top" -> Pivot.TOP;
            case "topRight" -> Pivot.TOP_RIGHT;
            case "left" -> Pivot.LEFT;
            case "center" -> Pivot.CENTER;
            case "right" -> Pivot.RIGHT;
            case "bottomLeft" -> Pivot.BOTTOM_LEFT;
            case "bottom" -> Pivot.BOTTOM;
            case "bottomRight" -> Pivot.BOTTOM_RIGHT;
            default -> Pivot.TOP_LEFT;
        };
    }

    /**
     * ✅ Root layout entry:
     * - root element anchors against viewport (vw/vh)
     * - children anchor against parent size (parent.w/parent.h)
     *
     * IMPORTANT: we set LOCAL translation, so we must NOT accumulate absolute screen coords.
     */
    static void layoutRecursive(Object hudElement, int vw, int vh, float parentW, float parentH) {
        HudElementLike el = (HudElementLike) hudElement;

        el.applyVisibility();

        // Anchor point INSIDE parent coordinate space
        float ax = anchorX(el.anchor(), parentW);
        float ay = anchorY(el.anchor(), parentH);

        // Desired point inside parent + local offsets
        float x = ax + el.offsetX();
        float y = ay + el.offsetY();

        // Pivot shifts by element size
        float px = pivotShiftX(el.pivot(), el.w());
        float py = pivotShiftY(el.pivot(), el.h());

        // Final LOCAL position
        float nodeX = x - px;
        float nodeY = y - py;

        el.setLocalTranslation(nodeX, nodeY, 0f);

        // Next level: children anchor against THIS element size
        float childParentW = el.w();
        float childParentH = el.h();

        // If parent has no size (group), it's fine — anchor will resolve to 0/center(0)
        for (Object child : el.children()) {
            layoutRecursive(child, vw, vh, childParentW, childParentH);
        }
    }

    // Anchor positions in parent-space
    static float anchorX(Anchor a, float parentW) {
        return switch (a) {
            case TOP_LEFT, LEFT, BOTTOM_LEFT -> 0f;
            case TOP, CENTER, BOTTOM -> parentW * 0.5f;
            case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> parentW;
        };
    }

    static float anchorY(Anchor a, float parentH) {
        return switch (a) {
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> 0f;
            case LEFT, CENTER, RIGHT -> parentH * 0.5f;
            case TOP_LEFT, TOP, TOP_RIGHT -> parentH;
        };
    }

    static float pivotShiftX(Pivot p, float w) {
        return switch (p) {
            case TOP_LEFT, LEFT, BOTTOM_LEFT -> 0f;
            case TOP, CENTER, BOTTOM -> w * 0.5f;
            case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> w;
        };
    }

    static float pivotShiftY(Pivot p, float h) {
        return switch (p) {
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> 0f;
            case LEFT, CENTER, RIGHT -> h * 0.5f;
            case TOP_LEFT, TOP, TOP_RIGHT -> h;
        };
    }

    interface HudElementLike {
        Anchor anchor();
        Pivot pivot();
        float offsetX();
        float offsetY();
        float w();
        float h();
        void setLocalTranslation(float x, float y, float z);
        void applyVisibility();
        Iterable<Object> children();
    }
}