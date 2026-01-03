package org.foxesworld.kalitech.engine.api.impl.hud;

import java.util.List;

/**
 * Minimal, allocation-light Flex layout pass for Kalitech HUD.
 *
 * Rules:
 *  - Only affects containers whose root.layoutKind == FLEX
 *  - ABSOLUTE children are NOT affected by flex, keep anchors/pivots
 *  - Flex-items are positioned inside parent by setting their anchor/pivot to TOP_LEFT
 */
final class HudFlexLayout {

    HudFlexLayout() {}

    /** Contract that HudApiImpl expects: flex.layout(roots, w, h) */
    void layout(List<HudElement> roots, int vw, int vh) {
        if (roots == null || roots.isEmpty()) return;
        for (HudElement r : roots) {
            if (r == null) continue;
            layoutTree(r);
        }
    }

    static void layoutTree(HudElement root) {
        if (root == null) return;

        // Post-order: children first so text/image sizes are already known
        for (HudElement c : root.children) layoutTree(c);

        if (root.layoutKind == HudElement.LayoutKind.FLEX) layoutFlexContainer(root);
    }

    private static void layoutFlexContainer(HudElement parent) {
        final List<HudElement> kids = parent.children;
        if (kids.isEmpty()) return;

        final float padL = parent.padL;
        final float padR = parent.padR;
        final float padT = parent.padT;
        final float padB = parent.padB;
        final float gap  = Math.max(0f, parent.gap);

        // Content coords: top-left origin, x right, y down.
        float cursorX = 0f;
        float cursorY = 0f;

        float maxX = 0f;
        float maxY = 0f;

        final boolean row = parent.flexDirection == HudElement.FlexDirection.ROW;

        for (HudElement child : kids) {
            if (child == null) continue;
            if (!child.visible) continue;

            // ✅ ABSOLUTE items are ignored by flex
            if (child.layoutKind == HudElement.LayoutKind.ABSOLUTE) continue;

            child.anchor = HudLayout.Anchor.TOP_LEFT;
            child.pivot  = HudLayout.Pivot.TOP_LEFT;

            if (row) {
                child.offsetX = padL + cursorX;
                child.offsetY = -(padT + 0f);

                cursorX += child.w + gap;
                maxX = Math.max(maxX, cursorX);
                maxY = Math.max(maxY, child.h);
            } else {
                child.offsetX = padL + 0f;
                child.offsetY = -(padT + cursorY);

                cursorY += child.h + gap;
                maxY = Math.max(maxY, cursorY);
                maxX = Math.max(maxX, child.w);
            }
        }

        // remove trailing gap from extents
        if (gap > 0f) {
            if (row && maxX > 0f) maxX = Math.max(0f, maxX - gap);
            if (!row && maxY > 0f) maxY = Math.max(0f, maxY - gap);
        }

        // Auto-size container if not explicitly sized
        if (parent.w <= 0f) parent.w = padL + maxX + padR;
        if (parent.h <= 0f) parent.h = padT + maxY + padB;
    }
}