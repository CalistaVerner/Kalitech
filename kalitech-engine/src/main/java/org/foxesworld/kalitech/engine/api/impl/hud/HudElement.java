package org.foxesworld.kalitech.engine.api.impl.hud;

import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.ArrayList;
import java.util.List;

abstract class HudElement implements HudLayout.HudElementLike {

    final int id;
    final Node node;

    HudElement parent;
    final List<HudElement> children = new ArrayList<>();

    boolean visible = true;

    HudLayout.Anchor anchor = HudLayout.Anchor.TOP_LEFT;
    HudLayout.Pivot  pivot  = HudLayout.Pivot.TOP_LEFT;

    float offsetX = 0f;
    float offsetY = 0f;

    // computed size for layout
    float w = 0f;
    float h = 0f;

    HudElement(int id, String name) {
        this.id = id;
        this.node = new Node(name);

        // 🔒 ЖЁСТКИЙ GUI-ПРОФИЛЬ
        this.node.setQueueBucket(RenderQueue.Bucket.Gui);
        this.node.setCullHint(Spatial.CullHint.Never);
    }

    // -----------------------------
    // Tree ops
    // -----------------------------

    void attach(HudElement child) {
        if (child == null || child.parent == this) return;

        if (child.parent != null) {
            child.parent.detach(child);
        }

        child.parent = this;
        children.add(child);
        node.attachChild(child.node);
    }

    void detach(HudElement child) {
        if (child == null || child.parent != this) return;

        children.remove(child);
        child.parent = null;
        child.node.removeFromParent();
    }

    // -----------------------------
    // Visibility
    // -----------------------------

    @Override
    public void applyVisibility() {
        node.setCullHint(visible
                ? Spatial.CullHint.Never
                : Spatial.CullHint.Always);
    }

    // -----------------------------
    // HudLayout.HudElementLike
    // -----------------------------

    @Override public HudLayout.Anchor anchor() { return anchor; }
    @Override public HudLayout.Pivot  pivot()  { return pivot; }
    @Override public float offsetX() { return offsetX; }
    @Override public float offsetY() { return offsetY; }
    @Override public float w() { return w; }
    @Override public float h() { return h; }

    /**
     * 🔥 КРИТИЧНО:
     * GUI ВСЕГДА НА z = -1 (перед камерой)
     */
    @Override
    public void setLocalTranslation(float x, float y, float zIgnored) {
        node.setLocalTranslation(x, y, -1f);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Iterable<Object> children() {
        return (Iterable) children;
    }

    /** override in subclasses */
    void onDestroy() {}
}