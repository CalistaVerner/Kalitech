package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.style.BaseStyles;
import org.foxesworld.kalitech.engine.api.interfaces.HudApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.modules.hud.HudCoords;
import org.foxesworld.kalitech.engine.modules.hud.HudSizeCache;
import org.foxesworld.kalitech.engine.modules.hud.HudSizing;
import org.graalvm.polyglot.HostAccess;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.clamp01f;

/**
 * HUD API (script-facing).
 *
 * <p>Coordinate contract:
 * <ul>
 *   <li>Top-left origin</li>
 *   <li>Y grows downward</li>
 * </ul>
 *
 * <p>Threading:
 * <ul>
 *   <li>All Lemur/JME scenegraph interactions are executed on the JME thread.</li>
 *   <li>Handles are stable integers stored in a registry.</li>
 * </ul>
 */
public final class HudApiImpl extends AbstractApiModule implements HudApi {

    private static final AtomicInteger IDS = new AtomicInteger(1000);

    private final ConcurrentHashMap<Integer, Layer> layers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, SpatialHolder> elements = new ConcurrentHashMap<>();

    private final HudSizeCache sizeCache = new HudSizeCache();

    private final AtomicBoolean inited = new AtomicBoolean(false);

    public HudApiImpl() {
        super("hud", "Hud", "1.0.0");
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        initOnce();
    }

    @Override
    public void detach() {
        layers.clear();
        elements.clear();
        try {
            sizeCache.clear();
        } catch (Throwable ignored) {
        }
        super.detach();
    }

    // ---------------------------------------------------------------------
    // Internal data
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // JME helpers
    // ---------------------------------------------------------------------

    private Application app() {
        var e = engine;
        return (e == null) ? null : e.getApp();
    }

    private Node guiNode() {
        Node n = engine.getApp().getGuiNode();
        if (n == null) throw new IllegalStateException("HudApiImpl: app.getGuiNode() returned null");
        return n;
    }


    private int vpW() {
        var a = app();
        var cam = (a == null) ? null : a.getCamera();
        return (cam != null) ? cam.getWidth() : 0;
    }

    private int vpH() {
        var a = app();
        var cam = (a == null) ? null : a.getCamera();
        return (cam != null) ? cam.getHeight() : 0;
    }

    private void rt(String where, Runnable r) {
        onJmeVoid(where, r);
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

        for (SpatialHolder sh : elements.values()) {
            if (sh != null && sh.spatial == parent) {
                float h = sizeCache.getH(sh.id);
                if (h > 0f) return h;
                break;
            }
        }

        return HudSizing.preferredH(parent);
    }

    private static void attachTo(Spatial parent, Spatial child) {
        if (parent instanceof Node n) n.attachChild(child);
    }

    private void initOnce() {
        if (!inited.compareAndSet(false, true)) return;

        rt("hud.init", () -> {
            try {
                if (GuiGlobals.getInstance() == null) {
                    GuiGlobals.initialize(app());
                }
            } catch (Throwable t) {
                if (log != null && log.isDebugEnabled()) log.debug("[hud] GuiGlobals init skipped", t);
            }

            try {
                BaseStyles.loadGlassStyle();
                if (GuiGlobals.getInstance() != null) {
                    GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");
                }
            } catch (Throwable t) {
                if (log != null && log.isDebugEnabled()) log.debug("[hud] style init skipped", t);
            }
        });
    }

    // ---------------------------------------------------------------------
    // HudApi exports
    // ---------------------------------------------------------------------

    @Override
    @HostAccess.Export
    public HudLayerHandle createLayer(String name) {
        final int id = IDS.incrementAndGet();
        final String nm = (name == null || name.isBlank()) ? ("layer-" + id) : name.trim();

        final Node root = new Node("hud:" + nm + ":" + id);
        root.setLocalTranslation(0, 0, 0);

        layers.put(id, new Layer(id, root));

        rt("hud.createLayer", () -> guiNode().attachChild(root));
        return new HudLayerHandle(id);
    }

    @Override
    @HostAccess.Export
    public void destroyLayer(HudLayerHandle layer) {
        final int lid = (layer == null) ? 0 : layer.id;
        if (lid <= 0) return;

        final Layer l = layers.remove(lid);
        if (l == null) return;

        rt("hud.destroyLayer", () -> {
            for (Iterator<Map.Entry<Integer, SpatialHolder>> it = elements.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, SpatialHolder> e = it.next();
                SpatialHolder sh = e.getValue();
                if (sh != null && sh.layerId == lid) {
                    it.remove();
                    sizeCache.remove(sh.id);
                    if (sh.spatial != null) sh.spatial.removeFromParent();
                }
            }
            l.root.removeFromParent();
        });
    }

    @Override
    @HostAccess.Export
    public void clearLayer(HudLayerHandle layer) {
        final int lid = (layer == null) ? 0 : layer.id;
        if (lid <= 0) return;

        final Layer l = layers.get(lid);
        if (l == null) return;

        rt("hud.clearLayer", () -> {
            for (Iterator<Map.Entry<Integer, SpatialHolder>> it = elements.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, SpatialHolder> e = it.next();
                SpatialHolder sh = e.getValue();
                if (sh != null && sh.layerId == lid) {
                    it.remove();
                    sizeCache.remove(sh.id);
                    if (sh.spatial != null) sh.spatial.removeFromParent();
                }
            }
            l.root.detachAllChildren();
        });
    }

    @Override
    @HostAccess.Export
    public HudElementHandle addContainer(HudLayerHandle layer, float x, float y) {
        final int lid = (layer == null) ? 0 : layer.id;
        if (lid <= 0) return new HudElementHandle(0);

        final Layer l = reqLayer(lid);
        final int id = IDS.incrementAndGet();

        final Container c = new Container();
        c.setName("hud.container:" + id);

        elements.put(id, new SpatialHolder(id, l.id, c));

        rt("hud.addContainer", () -> {
            float elemH = HudSizing.heightOf(id, c, sizeCache);
            float guiY = HudCoords.toGuiYBox(vpH(), y, elemH);
            c.setLocalTranslation(x, guiY, 0);
            l.root.attachChild(c);
        });

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

        elements.put(id, new SpatialHolder(id, l.id, p));

        rt("hud.addPanel", () -> {
            if (w > 0f && h > 0f) HudSizing.forceSize(id, p, w, h, sizeCache);

            float elemH = HudSizing.heightOf(id, p, sizeCache);
            if (elemH <= 0f && h > 0f) elemH = h;

            float guiY = HudCoords.toGuiYBox(vpH(), y, elemH);
            p.setLocalTranslation(x, guiY, 0);
            l.root.attachChild(p);
        });

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

        elements.put(id, new SpatialHolder(id, l.id, label));

        rt("hud.addLabel", () -> {
            float guiY = HudCoords.toGuiYPoint(vpH(), y);
            label.setLocalTranslation(x, guiY, 0);
            l.root.attachChild(label);
        });

        return new HudElementHandle(id);
    }

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

        elements.put(id, new SpatialHolder(id, l.id, c));

        rt("hud.addContainerChild", () -> {
            float parentH = HudSizing.heightOf(ph.id, ph.spatial, sizeCache);
            if (parentH <= 0f) parentH = parentHeightOf(ph.spatial);

            float childH = HudSizing.heightOf(id, c, sizeCache);
            float localY = HudCoords.toLocalYBox(y, parentH, childH);

            c.setLocalTranslation(x, localY, 0);
            attachTo(ph.spatial, c);
        });

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

        elements.put(id, new SpatialHolder(id, l.id, p));

        rt("hud.addPanelChild", () -> {
            if (w > 0f && h > 0f) HudSizing.forceSize(id, p, w, h, sizeCache);

            float parentH = HudSizing.heightOf(ph.id, ph.spatial, sizeCache);
            if (parentH <= 0f) parentH = parentHeightOf(ph.spatial);

            float childH = HudSizing.heightOf(id, p, sizeCache);
            if (childH <= 0f && h > 0f) childH = h;

            float localY = HudCoords.toLocalYBox(y, parentH, childH);
            p.setLocalTranslation(x, localY, 0);
            attachTo(ph.spatial, p);
        });

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

        elements.put(id, new SpatialHolder(id, l.id, label));

        rt("hud.addLabelChild", () -> {
            float parentH = HudSizing.heightOf(ph.id, ph.spatial, sizeCache);
            if (parentH <= 0f) parentH = parentHeightOf(ph.spatial);

            float localY = HudCoords.toLocalYPoint(y, parentH);
            label.setLocalTranslation(x, localY, 0);
            attachTo(ph.spatial, label);
        });

        return new HudElementHandle(id);
    }

    @Override
    @HostAccess.Export
    public void setCursorEnabled(boolean enabled) {
        setCursorEnabled(enabled, false);
    }

    @Override
    @HostAccess.Export
    public void setCursorEnabled(boolean enabled, boolean force) {
        rt("hud.setCursorEnabled", () -> {
            try {
                if (GuiGlobals.getInstance() != null) {
                    GuiGlobals.getInstance().setCursorEventsEnabled(enabled);
                }
            } catch (Throwable t) {
                if (log != null && log.isDebugEnabled())
                    log.debug("[hud] cursor toggle skipped enabled={}", enabled, t);
            }
        });
    }

    @Override
    @HostAccess.Export
    public void setText(HudElementHandle element, String text) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        final String t = (text != null) ? text : "";

        rt("hud.setText", () -> {
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

        rt("hud.setVisible", () -> {
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

        rt("hud.setPosition", () -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;

            Spatial s = sh.spatial;
            Spatial parent = s.getParent();

            final float newY;
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

        rt("hud.setSize", () -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;

            Spatial s = sh.spatial;
            boolean box = HudSizing.isBoxLike(s);

            float oldH = 0f;
            if (box) {
                oldH = sizeCache.getH(id);
                if (!(oldH > 0f)) oldH = HudSizing.preferredH(s);
                if (!(oldH > 0f)) oldH = 0f;
            }

            HudSizing.forceSize(id, s, w, h, sizeCache);

            if (box) {
                float newH = sizeCache.getH(id);
                if (!(newH > 0f)) newH = HudSizing.preferredH(s);
                if (!(newH > 0f)) newH = oldH;

                float dh = newH - oldH;
                if (dh != 0f) {
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

        rt("hud.setFontSize", () -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;
            if (sh.spatial instanceof Label l) {
                try {
                    l.setFontSize(size);
                } catch (Throwable t) {
                    if (log != null && log.isDebugEnabled()) log.debug("[hud] fontSize skipped id={}", id, t);
                }
            }
        });
    }

    @Override
    @HostAccess.Export
    public void setBgColor(HudElementHandle element, double r, double g, double b, double a) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        final float rr = clamp01f(r);
        final float gg = clamp01f(g);
        final float bb = clamp01f(b);
        final float aa = clamp01f(a);

        rt("hud.setBgColor", () -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;

            Spatial s = sh.spatial;
            try {
                QuadBackgroundComponent bg = new QuadBackgroundComponent(new ColorRGBA(rr, gg, bb, aa));
                if (s instanceof Panel p) {
                    p.setBackground(bg);
                }
            } catch (Throwable t) {
                if (log != null && log.isDebugEnabled()) log.debug("[hud] bgColor skipped id={}", id, t);
            }
        });
    }

    @Override
    @HostAccess.Export
    public void setTextColor(HudElementHandle element, double r, double g, double b, double a) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        final float rr = clamp01f(r);
        final float gg = clamp01f(g);
        final float bb = clamp01f(b);
        final float aa = clamp01f(a);

        rt("hud.setTextColor", () -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;

            if (sh.spatial instanceof Label l) {
                try {
                    l.setColor(new ColorRGBA(rr, gg, bb, aa));
                } catch (Throwable t) {
                    if (log != null && log.isDebugEnabled()) log.debug("[hud] textColor skipped id={}", id, t);
                }
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

        rt("hud.remove", sh.spatial::removeFromParent);
    }

    @Override
    @HostAccess.Export
    public HudViewport viewport() {
        return new HudViewport(vpW(), vpH());
    }
}