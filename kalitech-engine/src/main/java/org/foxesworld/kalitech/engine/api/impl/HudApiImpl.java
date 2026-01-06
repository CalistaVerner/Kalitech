// FILE: org/foxesworld/kalitech/engine/api/impl/HudApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.Application;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.style.BaseStyles;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.HudApi;
import org.foxesworld.kalitech.engine.modules.hud.HudCoords;
import org.foxesworld.kalitech.engine.modules.hud.HudSizeCache;
import org.foxesworld.kalitech.engine.modules.hud.HudSizing;
import org.graalvm.polyglot.HostAccess;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HudApiImpl (thin bridge).
 *
 * ✅ Responsibilities:
 *  - Registry (layers/elements)
 *  - Thread hop (rt -> JME thread)
 *  - Call into engine.modules.hud for math/sizing
 *
 * Script coordinate contract:
 *  - TOP-LEFT origin, y grows DOWN
 */
public final class HudApiImpl implements HudApi {

    private final EngineApiImpl engine;

    private static final AtomicInteger IDS = new AtomicInteger(1000);

    private final ConcurrentHashMap<Integer, Layer> layers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, SpatialHolder> elements = new ConcurrentHashMap<>();

    // AAA: keep explicit sizes to stabilize math even on forked Lemur
    private final HudSizeCache sizeCache = new HudSizeCache();

    public HudApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        ensureLemur();
    }

    private void ensureLemur() {
        try {
            if (GuiGlobals.getInstance() == null) {
                GuiGlobals.initialize(engine.getApp());
            }
        } catch (Throwable ignore) {}

        try {
            BaseStyles.loadGlassStyle();
            GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");
        } catch (Throwable ignore) {}
    }

    // ------------------------------------------------------------
    // internal holders
    // ------------------------------------------------------------

    private static final class Layer {
        final int id;
        final Node root;

        Layer(int id, Node root) {
            this.id = id;
            this.root = root;
        }
    }

    private static final class SpatialHolder {
        final int id;
        final int layerId;
        final Spatial spatial;

        SpatialHolder(int id, int layerId, Spatial spatial) {
            this.id = id;
            this.layerId = layerId;
            this.spatial = spatial;
        }
    }

    // ------------------------------------------------------------
    // JME helpers
    // ------------------------------------------------------------

    private Application app() {
        return engine.getApp();
    }

    private Node guiNode() {
        Node n = engine.getApp().getGuiNode();
        if (n == null) throw new IllegalStateException("HudApiImpl: app.getGuiNode() returned null");
        return n;
    }

    private int vpW() {
        var cam = app().getCamera();
        return cam != null ? cam.getWidth() : 0;
    }

    private int vpH() {
        var cam = app().getCamera();
        return cam != null ? cam.getHeight() : 0;
    }

    private void rt(Runnable r) {
        app().enqueue(() -> {
            r.run();
            return null;
        });
    }

    private Layer reqLayer(int id) {
        Layer l = layers.get(id);
        if (l == null) throw new IllegalArgumentException("hud: unknown layer id=" + id);
        return l;
    }

    private SpatialHolder reqElement(int id) {
        SpatialHolder sh = elements.get(id);
        if (sh == null) throw new IllegalArgumentException("hud: unknown element id=" + id);
        return sh;
    }

    private boolean parentIsLayerRoot(Spatial parent) {
        if (parent == null) return false;
        for (Layer l : layers.values()) {
            if (l.root == parent) return true;
        }
        return false;
    }

    private float parentHeightOf(Spatial parent) {
        if (parent == null) return 0f;

        // if parent is one of our elements, prefer cached size
        for (Map.Entry<Integer, SpatialHolder> e : elements.entrySet()) {
            SpatialHolder sh = e.getValue();
            if (sh != null && sh.spatial == parent) {
                float h = sizeCache.getH(sh.id);
                if (h > 0f) return h;
                break;
            }
        }
        // fallback to Lemur preferred
        return HudSizing.preferredH(parent);
    }

    private static void attachTo(Spatial parent, Spatial child) {
        if (parent instanceof Node n) n.attachChild(child);
    }

    // ------------------------------------------------------------
    // HudApi exports
    // ------------------------------------------------------------

    @Override
    @HostAccess.Export
    public HudLayerHandle createLayer(String name) {
        final int id = IDS.incrementAndGet();
        final String nm = (name == null || name.isBlank()) ? ("layer-" + id) : name;

        final Node root = new Node("hud:" + nm + ":" + id);
        root.setLocalTranslation(0, 0, 0);

        layers.put(id, new Layer(id, root));

        rt(() -> guiNode().attachChild(root));
        return new HudLayerHandle(id);
    }

    @Override
    @HostAccess.Export
    public void destroyLayer(HudLayerHandle layer) {
        final int lid = (layer == null) ? 0 : layer.id;
        if (lid <= 0) return;

        Layer l = layers.remove(lid);
        if (l == null) return;

        for (Iterator<Map.Entry<Integer, SpatialHolder>> it = elements.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, SpatialHolder> e = it.next();
            SpatialHolder sh = e.getValue();
            if (sh != null && sh.layerId == lid) {
                it.remove();
                sizeCache.remove(sh.id);
                Spatial s = sh.spatial;
                if (s != null) rt(s::removeFromParent);
            }
        }

        rt(l.root::removeFromParent);
    }

    @Override
    @HostAccess.Export
    public void clearLayer(HudLayerHandle layer) {
        final int lid = (layer == null) ? 0 : layer.id;
        if (lid <= 0) return;

        Layer l = layers.get(lid);
        if (l == null) return;

        for (Iterator<Map.Entry<Integer, SpatialHolder>> it = elements.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, SpatialHolder> e = it.next();
            SpatialHolder sh = e.getValue();
            if (sh != null && sh.layerId == lid) {
                it.remove();
                sizeCache.remove(sh.id);
                Spatial s = sh.spatial;
                if (s != null) rt(s::removeFromParent);
            }
        }

        rt(l.root::detachAllChildren);
    }

    // ------------------------------------------------------------
    // elements (root)
    // ------------------------------------------------------------

    @Override
    @HostAccess.Export
    public HudElementHandle addContainer(HudLayerHandle layer, float x, float y) {
        final int lid = (layer == null) ? 0 : layer.id;
        if (lid <= 0) return new HudElementHandle(0);

        final Layer l = reqLayer(lid);

        final int id = IDS.incrementAndGet();
        final Container c = new Container();
        c.setName("hud.container:" + id);

        float elemH = HudSizing.heightOf(id, c, sizeCache);
        float guiY = HudCoords.toGuiYBox(vpH(), y, elemH);
        c.setLocalTranslation(x, guiY, 0);

        elements.put(id, new SpatialHolder(id, l.id, c));
        rt(() -> l.root.attachChild(c));

        return new HudElementHandle(id);
    }

    @Override
    @HostAccess.Export
    public HudElementHandle addPanel(HudLayerHandle layer, float x, float y, float w, float h) {
        final int lid = (layer == null) ? 0 : layer.id;
        if (lid <= 0) return new HudElementHandle(0);

        final Layer l = reqLayer(lid);

        final int id = IDS.incrementAndGet();
        final Panel p = new Panel();
        p.setName("hud.panel:" + id);

        if (w > 0f && h > 0f) {
            HudSizing.forceSize(id, p, w, h, sizeCache);
        }

        float elemH = HudSizing.heightOf(id, p, sizeCache);
        if (elemH <= 0f && h > 0f) elemH = h;

        float guiY = HudCoords.toGuiYBox(vpH(), y, elemH);
        p.setLocalTranslation(x, guiY, 0);

        elements.put(id, new SpatialHolder(id, l.id, p));
        rt(() -> l.root.attachChild(p));

        return new HudElementHandle(id);
    }

    @Override
    @HostAccess.Export
    public HudElementHandle addLabel(HudLayerHandle layer, String text, float x, float y) {
        final int lid = (layer == null) ? 0 : layer.id;
        if (lid <= 0) return new HudElementHandle(0);

        final Layer l = reqLayer(lid);

        final int id = IDS.incrementAndGet();
        final Label label = new Label(text != null ? text : "");
        label.setName("hud.label:" + id);

        float guiY = HudCoords.toGuiYPoint(vpH(), y);
        label.setLocalTranslation(x, guiY, 0);

        elements.put(id, new SpatialHolder(id, l.id, label));
        rt(() -> l.root.attachChild(label));

        return new HudElementHandle(id);
    }

    // ------------------------------------------------------------
    // elements (with parent)
    // ------------------------------------------------------------

    @Override
    @HostAccess.Export
    public HudElementHandle addContainer(HudLayerHandle layer, HudElementHandle parent, float x, float y) {
        final int lid = (layer == null) ? 0 : layer.id;
        final int pid = (parent == null) ? 0 : parent.id;
        if (lid <= 0 || pid <= 0) return new HudElementHandle(0);

        final Layer l = reqLayer(lid);
        final SpatialHolder ph = reqElement(pid);

        final int id = IDS.incrementAndGet();
        final Container c = new Container();
        c.setName("hud.container:" + id);

        float parentH = HudSizing.heightOf(ph.id, ph.spatial, sizeCache);
        if (parentH <= 0f) parentH = parentHeightOf(ph.spatial);

        float childH = HudSizing.heightOf(id, c, sizeCache);
        float localY = HudCoords.toLocalYBox(y, parentH, childH);

        c.setLocalTranslation(x, localY, 0);

        elements.put(id, new SpatialHolder(id, l.id, c));
        rt(() -> attachTo(ph.spatial, c));

        return new HudElementHandle(id);
    }

    @Override
    @HostAccess.Export
    public HudElementHandle addPanel(HudLayerHandle layer, HudElementHandle parent, float x, float y, float w, float h) {
        final int lid = (layer == null) ? 0 : layer.id;
        final int pid = (parent == null) ? 0 : parent.id;
        if (lid <= 0 || pid <= 0) return new HudElementHandle(0);

        final Layer l = reqLayer(lid);
        final SpatialHolder ph = reqElement(pid);

        final int id = IDS.incrementAndGet();
        final Panel p = new Panel();
        p.setName("hud.panel:" + id);

        if (w > 0f && h > 0f) {
            HudSizing.forceSize(id, p, w, h, sizeCache);
        }

        float parentH = HudSizing.heightOf(ph.id, ph.spatial, sizeCache);
        if (parentH <= 0f) parentH = parentHeightOf(ph.spatial);

        float childH = HudSizing.heightOf(id, p, sizeCache);
        if (childH <= 0f && h > 0f) childH = h;

        float localY = HudCoords.toLocalYBox(y, parentH, childH);
        p.setLocalTranslation(x, localY, 0);

        elements.put(id, new SpatialHolder(id, l.id, p));
        rt(() -> attachTo(ph.spatial, p));

        return new HudElementHandle(id);
    }

    @Override
    @HostAccess.Export
    public HudElementHandle addLabel(HudLayerHandle layer, HudElementHandle parent, String text, float x, float y) {
        final int lid = (layer == null) ? 0 : layer.id;
        final int pid = (parent == null) ? 0 : parent.id;
        if (lid <= 0 || pid <= 0) return new HudElementHandle(0);

        final Layer l = reqLayer(lid);
        final SpatialHolder ph = reqElement(pid);

        final int id = IDS.incrementAndGet();
        final Label label = new Label(text != null ? text : "");
        label.setName("hud.label:" + id);

        float parentH = HudSizing.heightOf(ph.id, ph.spatial, sizeCache);
        if (parentH <= 0f) parentH = parentHeightOf(ph.spatial);

        float localY = HudCoords.toLocalYPoint(y, parentH);
        label.setLocalTranslation(x, localY, 0);

        elements.put(id, new SpatialHolder(id, l.id, label));
        rt(() -> attachTo(ph.spatial, label));

        return new HudElementHandle(id);
    }

    // ------------------------------------------------------------
    // cursor
    // ------------------------------------------------------------

    @Override
    @HostAccess.Export
    public void setCursorEnabled(boolean enabled) {
        setCursorEnabled(enabled, false);
    }

    @Override
    @HostAccess.Export
    public void setCursorEnabled(boolean enabled, boolean force) {
        rt(() -> {
            try {
                if (GuiGlobals.getInstance() != null) {
                    GuiGlobals.getInstance().setCursorEventsEnabled(enabled);
                }
            } catch (Throwable ignore) {}
        });
    }

    // ------------------------------------------------------------
    // ops
    // ------------------------------------------------------------

    @Override
    @HostAccess.Export
    public void setText(HudElementHandle element, String text) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        final String t = (text != null) ? text : "";

        rt(() -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;
            if (sh.spatial instanceof Label l) l.setText(t);
        });
    }

    @Override
    @HostAccess.Export
    public void setVisible(HudElementHandle element, boolean visible) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        rt(() -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;
            sh.spatial.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        });
    }

    @Override
    @HostAccess.Export
    public void setPosition(HudElementHandle element, float x, float y) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        rt(() -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;

            Spatial s = sh.spatial;
            Spatial parent = s.getParent();

            float newY;

            // rooted to viewport?
            boolean rooted = (parent == null) || parentIsLayerRoot(parent);
            if (rooted) {
                if (HudSizing.isBoxLike(s)) {
                    float eh = HudSizing.heightOf(id, s, sizeCache);
                    newY = HudCoords.toGuiYBox(vpH(), y, eh);
                } else {
                    newY = HudCoords.toGuiYPoint(vpH(), y);
                }
            } else {
                float parentH = parentHeightOf(parent);
                if (HudSizing.isBoxLike(s)) {
                    float ch = HudSizing.heightOf(id, s, sizeCache);
                    newY = HudCoords.toLocalYBox(y, parentH, ch);
                } else {
                    newY = HudCoords.toLocalYPoint(y, parentH);
                }
            }

            Vector3f lt = s.getLocalTranslation();
            float z = (lt != null) ? lt.z : 0f;
            s.setLocalTranslation(x, newY, z);
        });
    }

    @Override
    @HostAccess.Export
    public void setSize(HudElementHandle element, float w, float h) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        rt(() -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;

            final Spatial s = sh.spatial;

            // Only box-like elements must keep TOP pinned when height changes.
            final boolean box = HudSizing.isBoxLike(s);

            float oldH = 0f;
            if (box) {
                // Prefer cached explicit size, fallback to Lemur preferred
                oldH = sizeCache.getH(id);
                if (!(oldH > 0f)) oldH = HudSizing.preferredH(s);
                if (!(oldH > 0f)) oldH = 0f;
            }

            // Apply new size (updates cache too)
            HudSizing.forceSize(id, s, w, h, sizeCache);

            if (box) {
                float newH = sizeCache.getH(id);
                if (!(newH > 0f)) newH = HudSizing.preferredH(s);
                if (!(newH > 0f)) newH = oldH;

                float dh = newH - oldH;
                if (dh != 0f) {
                    // To keep TOP-LEFT pinned:
                    // guiYBox = vpH - yTopLeft - h  => when h grows, guiY must go DOWN by -dh (move UP)
                    Vector3f lt = s.getLocalTranslation();
                    float x0 = (lt != null) ? lt.x : 0f;
                    float y0 = (lt != null) ? lt.y : 0f;
                    float z0 = (lt != null) ? lt.z : 0f;

                    s.setLocalTranslation(x0, y0 - dh, z0);
                }
            }
        });
    }

    @Override
    @HostAccess.Export
    public void setFontSize(HudElementHandle element, float px) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        final float size = (Float.isFinite(px) && px > 0f) ? Math.max(6f, px) : 16f;

        rt(() -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;
            if (sh.spatial instanceof Label l) {
                try { l.setFontSize(size); } catch (Throwable ignore) {}
            }
        });
    }

    @Override
    @HostAccess.Export
    public void remove(HudElementHandle element) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        SpatialHolder sh = elements.remove(id);
        sizeCache.remove(id);
        if (sh == null || sh.spatial == null) return;

        rt(sh.spatial::removeFromParent);
    }

    // ------------------------------------------------------------
    // viewport
    // ------------------------------------------------------------

    @Override
    @HostAccess.Export
    public HudViewport viewport() {
        return new HudViewport(vpW(), vpH());
    }
}