package org.foxesworld.kalitech.engine.api.impl.hud;

import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.ArrayList;
import java.util.List;

abstract class HudElement implements HudLayout.HudElementLike, HudLayout.HudElementEx {

    // -----------------------------
    // Type
    // -----------------------------

    enum Kind { GROUP, RECT, IMAGE, TEXT, HTML }
    Kind kind = Kind.GROUP;

    // -----------------------------
    // Layout (HTML/CSS)
    // -----------------------------

    enum LayoutKind { ABSOLUTE, FLEX }
    enum FlexDirection { ROW, COLUMN }

    /** How children are laid out inside this element (container behavior). */
    LayoutKind layoutKind = LayoutKind.ABSOLUTE;

    /** For LayoutKind.FLEX containers. */
    FlexDirection flexDirection = FlexDirection.ROW;

    /** Padding inside this element (pixels). */
    float padL = 0f, padT = 0f, padR = 0f, padB = 0f;

    /** Gap between flex items (pixels). */
    float gap = 0f;

    /**
     * If true, the element size is derived from content (text/image auto bounds).
     * Layout should not overwrite w/h unless explicit.
     */
    boolean contentSized = false;

    /**
     * If true, element should match parent size (useful for panel backgrounds).
     */
    boolean fillParent = false;

    /** Optional z-order hint (HudElement forces -1 in translation by default). */
    float z = 0f;

    // -----------------------------
    // Tree
    // -----------------------------

    final int id;
    final Node node;

    HudElement parent;
    final List<HudElement> children = new ArrayList<>();

    // -----------------------------
    // Common props
    // -----------------------------

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

        // 🔒 GUI-PROFILE
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

    // aliases for compatibility
    final void addChild(HudElement child) { attach(child); }
    final void removeChild(HudElement child) { detach(child); }

    // -----------------------------
    // Visibility
    // -----------------------------

    @Override
    public void applyVisibility() {
        node.setCullHint(visible ? Spatial.CullHint.Never : Spatial.CullHint.Always);
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
     * GUI always on z = -1 to avoid depth issues.
     * If you later want true z layering, change this implementation.
     */
    @Override
    public void setLocalTranslation(float x, float y, float z) {
        node.setLocalTranslation(x, y, z);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Iterable<Object> children() {
        return (Iterable) children;
    }

    // -----------------------------
    // HudLayout.HudElementEx
    // -----------------------------

    @Override public boolean fillParent() { return fillParent; }

    @Override
    public void setSize(float w, float h) {
        this.w = w;
        this.h = h;
    }

    @Override public float z() { return z; }

    // -----------------------------
    // Lifecycle
    // -----------------------------

    void onDestroy() {}
}