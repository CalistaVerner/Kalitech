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

    // -----------------------------
    // Parsing (robust)
    // -----------------------------

    static Anchor anchorOf(String s) {
        if (s == null) return Anchor.TOP_LEFT;
        String t = norm(s);
        return switch (t) {
            case "topleft", "top-left", "top_left" -> Anchor.TOP_LEFT;
            case "top" -> Anchor.TOP;
            case "topright", "top-right", "top_right" -> Anchor.TOP_RIGHT;

            case "left" -> Anchor.LEFT;
            case "center", "middle" -> Anchor.CENTER;
            case "right" -> Anchor.RIGHT;

            case "bottomleft", "bottom-left", "bottom_left" -> Anchor.BOTTOM_LEFT;
            case "bottom" -> Anchor.BOTTOM;
            case "bottomright", "bottom-right", "bottom_right" -> Anchor.BOTTOM_RIGHT;

            default -> Anchor.TOP_LEFT;
        };
    }

    static Pivot pivotOf(String s) {
        if (s == null) return Pivot.TOP_LEFT;
        String t = norm(s);
        return switch (t) {
            case "topleft", "top-left", "top_left" -> Pivot.TOP_LEFT;
            case "top" -> Pivot.TOP;
            case "topright", "top-right", "top_right" -> Pivot.TOP_RIGHT;

            case "left" -> Pivot.LEFT;
            case "center", "middle" -> Pivot.CENTER;
            case "right" -> Pivot.RIGHT;

            case "bottomleft", "bottom-left", "bottom_left" -> Pivot.BOTTOM_LEFT;
            case "bottom" -> Pivot.BOTTOM;
            case "bottomright", "bottom-right", "bottom_right" -> Pivot.BOTTOM_RIGHT;

            default -> Pivot.TOP_LEFT;
        };
    }

    private static String norm(String s) {
        return s.trim().toLowerCase();
    }

    // -----------------------------
    // Public entrypoints
    // -----------------------------

    /**
     * Apply layout for a root anchored to viewport.
     * Root anchors against viewport (vw/vh). Children anchor against parent size.
     *
     * Coordinate system: ✅ y-down (screen/HTML style)
     *  - (0,0) is top-left
     *  - y grows down
     *
     * IMPORTANT: Camera must be configured to match this (Y-down ortho).
     */
    static void apply(Object hudElement, int vw, int vh) {
        if (hudElement == null) return;
        int w = Math.max(1, vw);
        int h = Math.max(1, vh);
        layoutRecursive(hudElement, w, h, w, h);
    }

    /**
     * Root layout entry:
     * - root element anchors against viewport (vw/vh)
     * - children anchor against parent size (parent.w/parent.h)
     *
     * IMPORTANT: we set LOCAL translation, so we must NOT accumulate absolute screen coords.
     */
    static void layoutRecursive(Object hudElement, int vw, int vh, float parentW, float parentH) {
        if (hudElement == null) return;

        HudElementLike el = safeCast(hudElement);
        if (el == null) return;

        // apply visibility first (it can cull subtree cheaply)
        el.applyVisibility();

        parentW = sanitize(parentW);
        parentH = sanitize(parentH);

        // Resolve "fill parent" sizing if supported
        if (el instanceof HudElementEx ex) {
            if (ex.fillParent()) {
                ex.setSize(Math.max(0f, parentW), Math.max(0f, parentH));
            }
        }

        float w = sanitize(el.w());
        float h = sanitize(el.h());

        // Anchor point inside parent coordinate space (✅ y-down)
        float ax = anchorX(el.anchor(), parentW);
        float ay = anchorY(el.anchor(), parentH);

        // Desired point inside parent + local offsets
        float x = sanitize(ax + el.offsetX());
        float y = sanitize(ay + el.offsetY());

        // Pivot shifts by element size (✅ y-down)
        float px = pivotShiftX(el.pivot(), w);
        float py = pivotShiftY(el.pivot(), h);

        // Final LOCAL position (✅ y-down)
        float nodeX = sanitize(x - px);
        float nodeY = sanitize(y - py);

        if (el instanceof HudElementEx ex) {
            el.setLocalTranslation(nodeX, nodeY, sanitize(ex.z()));
        } else {
            el.setLocalTranslation(nodeX, nodeY, 0f);
        }

        // Next level: children anchor against THIS element size
        float childParentW = w;
        float childParentH = h;

        Iterable<Object> kids = el.children();
        if (kids == null) return;

        for (Object child : kids) {
            if (child == null) continue;
            if (child == hudElement) continue; // guard accidental self-link
            layoutRecursive(child, vw, vh, childParentW, childParentH);
        }
    }

    // -----------------------------
    // Anchor positions in parent-space
    // -----------------------------

    static float anchorX(Anchor a, float parentW) {
        parentW = sanitize(parentW);
        return switch (a) {
            case TOP_LEFT, LEFT, BOTTOM_LEFT -> 0f;
            case TOP, CENTER, BOTTOM -> parentW * 0.5f;
            case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> parentW;
        };
    }

    static float anchorY(Anchor a, float parentH) {
        // ✅ y-down:
        // TOP = 0, BOTTOM = parentH
        parentH = sanitize(parentH);
        return switch (a) {
            case TOP_LEFT, TOP, TOP_RIGHT -> 0f;
            case LEFT, CENTER, RIGHT -> parentH * 0.5f;
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> parentH;
        };
    }

    static float pivotShiftX(Pivot p, float w) {
        w = sanitize(w);
        return switch (p) {
            case TOP_LEFT, LEFT, BOTTOM_LEFT -> 0f;
            case TOP, CENTER, BOTTOM -> w * 0.5f;
            case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> w;
        };
    }

    static float pivotShiftY(Pivot p, float h) {
        // ✅ y-down:
        // TOP pivot shift = 0, BOTTOM shift = full height
        h = sanitize(h);
        return switch (p) {
            case TOP_LEFT, TOP, TOP_RIGHT -> 0f;
            case LEFT, CENTER, RIGHT -> h * 0.5f;
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> h;
        };
    }

    // -----------------------------
    // Small safety helpers (AAA robustness)
    // -----------------------------

    private static HudElementLike safeCast(Object o) {
        if (o instanceof HudElementLike like) return like;
        return null;
    }

    private static float sanitize(float v) {
        if (!finite(v)) return 0f;
        if (v > 1_000_000f) return 1_000_000f;
        if (v < -1_000_000f) return -1_000_000f;
        return v;
    }

    private static boolean finite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    // -----------------------------
    // Contracts
    // -----------------------------

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

    interface HudElementEx {
        boolean fillParent();
        void setSize(float w, float h);
        float z();
    }
}