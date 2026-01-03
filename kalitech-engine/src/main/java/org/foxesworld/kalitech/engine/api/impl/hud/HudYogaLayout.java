package org.foxesworld.kalitech.engine.api.impl.hud;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.util.yoga.Yoga.*;

/**
 * Flexbox layout pass backed by Yoga (LWJGL bindings).
 *
 * Rules:
 *  - Only affects containers whose el.layoutKind == FLEX
 *  - ABSOLUTE children are ignored by Yoga
 *  - Output is written into child.offsetX/offsetY and child.w/h if it was auto
 *
 * Coordinate bridging:
 *  - Yoga uses top-left origin with +y down
 *  - Kalitech HUD layout uses y-up; we write offsetY negative for downward.
 */
final class HudYogaLayout {

    HudYogaLayout() {}

    /** Contract that HudApiImpl expects: yoga.layout(roots, w, h) */
    void layout(List<HudElement> roots, int viewportW, int viewportH) {
        if (roots == null || roots.isEmpty()) return;
        for (HudElement r : roots) {
            if (r == null) continue;
            layoutRoot(r, viewportW, viewportH);
        }
    }

    void layoutRoot(HudElement root, int viewportW, int viewportH) {
        if (root == null) return;

        float availableW = (root.w > 0f) ? root.w : viewportW;
        float availableH = (root.h > 0f) ? root.h : viewportH;

        layoutFlexSubtree(root, availableW, availableH);
    }

    private static void layoutFlexSubtree(HudElement el, float parentW, float parentH) {
        if (el == null) return;

        // Recurse first so leaf sizes (text/image) are already known.
        float thisW = (el.w > 0f) ? el.w : parentW;
        float thisH = (el.h > 0f) ? el.h : parentH;

        for (HudElement c : el.children) {
            if (c == null) continue;
            layoutFlexSubtree(c, thisW, thisH);
        }

        if (el.layoutKind != HudElement.LayoutKind.FLEX) return;

        long yParent = YGNodeNew();
        try {
            // Direction
            YGNodeStyleSetFlexDirection(
                    yParent,
                    el.flexDirection == HudElement.FlexDirection.COLUMN
                            ? YGFlexDirectionColumn
                            : YGFlexDirectionRow
            );

            // Defaults
            YGNodeStyleSetAlignItems(yParent, YGAlignFlexStart);
            YGNodeStyleSetJustifyContent(yParent, YGJustifyFlexStart);

            // Padding
            if (el.padL != 0f) YGNodeStyleSetPadding(yParent, YGEdgeLeft, el.padL);
            if (el.padT != 0f) YGNodeStyleSetPadding(yParent, YGEdgeTop, el.padT);
            if (el.padR != 0f) YGNodeStyleSetPadding(yParent, YGEdgeRight, el.padR);
            if (el.padB != 0f) YGNodeStyleSetPadding(yParent, YGEdgeBottom, el.padB);

            // Container size: explicit -> fixed; else constrain by parent
            float containerW = (el.w > 0f) ? el.w : parentW;
            float containerH = (el.h > 0f) ? el.h : parentH;
            YGNodeStyleSetWidth(yParent, containerW);
            YGNodeStyleSetHeight(yParent, containerH);

            List<Long> yChildren = new ArrayList<>();
            List<HudElement> flexKids = new ArrayList<>();

            float g = Math.max(0f, el.gap);

            for (HudElement child : el.children) {
                if (child == null) continue;
                if (!child.visible) continue;

                // ✅ ABSOLUTE items are ignored by flex
                if (child.layoutKind == HudElement.LayoutKind.ABSOLUTE) continue;

                long yChild = YGNodeNew();

                // if child has measured size already, pin it
                if (child.w > 0f) YGNodeStyleSetWidth(yChild, child.w);
                if (child.h > 0f) YGNodeStyleSetHeight(yChild, child.h);

                // gap via margin in main axis (except last)
                if (g > 0f) {
                    if (el.flexDirection == HudElement.FlexDirection.COLUMN) {
                        YGNodeStyleSetMargin(yChild, YGEdgeBottom, g);
                    } else {
                        YGNodeStyleSetMargin(yChild, YGEdgeRight, g);
                    }
                }

                YGNodeInsertChild(yParent, yChild, YGNodeGetChildCount(yParent));
                yChildren.add(yChild);
                flexKids.add(child);
            }

            // Remove trailing gap
            if (g > 0f && !yChildren.isEmpty()) {
                long last = yChildren.get(yChildren.size() - 1);
                if (el.flexDirection == HudElement.FlexDirection.COLUMN) {
                    YGNodeStyleSetMargin(last, YGEdgeBottom, 0f);
                } else {
                    YGNodeStyleSetMargin(last, YGEdgeRight, 0f);
                }
            }

            // Run layout
            YGNodeCalculateLayout(yParent, containerW, containerH, YGDirectionLTR);

            // Apply results back to children
            for (int i = 0; i < flexKids.size(); i++) {
                HudElement child = flexKids.get(i);
                long yChild = yChildren.get(i);

                child.anchor = HudLayout.Anchor.TOP_LEFT;
                child.pivot  = HudLayout.Pivot.TOP_LEFT;

                float left = YGNodeLayoutGetLeft(yChild);
                float top  = YGNodeLayoutGetTop(yChild);

                child.offsetX = left;
                child.offsetY = -top; // +down -> negative

                float cw = YGNodeLayoutGetWidth(yChild);
                float ch = YGNodeLayoutGetHeight(yChild);

                if (child.w <= 0f && cw > 0f) child.w = cw;
                if (child.h <= 0f && ch > 0f) child.h = ch;
            }

            // Optional: autosize container if it has no explicit size
            float pw = YGNodeLayoutGetWidth(yParent);
            float ph = YGNodeLayoutGetHeight(yParent);
            if (el.w <= 0f && pw > 0f) el.w = pw;
            if (el.h <= 0f && ph > 0f) el.h = ph;

        } finally {
            try { YGNodeFreeRecursive(yParent); } catch (Throwable ignored) {}
        }
    }
}