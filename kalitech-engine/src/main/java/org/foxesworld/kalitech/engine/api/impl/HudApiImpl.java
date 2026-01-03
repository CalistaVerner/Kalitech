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
import com.simsilica.lemur.core.GuiControl;
import com.simsilica.lemur.style.BaseStyles;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.HudApi;
import org.graalvm.polyglot.HostAccess;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class HudApiImpl implements HudApi {

    private final EngineApiImpl engine;
    private final Application app;
    private final Node guiNode;

    private boolean lemurReady = false;

    private int nextLayerId = 1;
    private int nextElemId = 1;

    private final Map<Integer, Layer> layers = new HashMap<>();
    private final Map<Integer, SpatialHolder> elements = new HashMap<>();

    public HudApiImpl(EngineApiImpl engine) {
        if (engine == null) throw new IllegalArgumentException("engine is null");
        this.engine = engine;
        this.app = engine.getApp();
        this.guiNode = engine.getApp().getGuiNode();
    }

    // ------------------------------------------------------------
    // Render-thread dispatch (hard rule)
    // ------------------------------------------------------------
    private void rt(Runnable r) {
        engine.getApp().enqueue(() -> {
            r.run();
            return null;
        });
    }


    private void ensureLemur() {
        if (lemurReady) return;

        GuiGlobals.initialize(app);
        BaseStyles.loadGlassStyle();
        GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");

        lemurReady = true;
    }

    private Layer reqLayer(int id) {
        if (id <= 0) throw new IllegalArgumentException("[hud] layer id <= 0");
        Layer l = layers.get(id);
        if (l == null) throw new IllegalStateException("[hud] unknown layer id=" + id);
        return l;
    }

    private Node resolveAttachRoot(int layerId, HudElementHandle parent) {
        Layer l = reqLayer(layerId);
        if (parent == null || parent.id <= 0) return l.root;

        SpatialHolder ph = elements.get(parent.id);
        if (ph == null || ph.spatial == null) return l.root;

        // attachChild только у Node
        if (ph.spatial instanceof Node n) return n;

        return l.root;
    }

    // ------------------------------------------------------------
    // Layers
    // ------------------------------------------------------------

    @Override
    @HostAccess.Export
    public HudLayerHandle createLayer(String name) {
        final String layerName = (name == null || name.isBlank()) ? ("layer-" + nextLayerId) : name.trim();
        final int id = nextLayerId++;

        rt(() -> {
            ensureLemur();
            Layer layer = new Layer(id, layerName);
            guiNode.attachChild(layer.root);
            layers.put(id, layer);
        });

        return new HudLayerHandle(id);
    }

    @Override
    @HostAccess.Export
    public void destroyLayer(HudLayerHandle layer) {
        final int lid = (layer == null) ? 0 : layer.id;
        if (lid <= 0) return;

        rt(() -> {
            Layer l = layers.remove(lid);
            if (l == null) return;

            // удалить все элементы слоя
            for (Iterator<Map.Entry<Integer, SpatialHolder>> it = elements.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, SpatialHolder> e = it.next();
                SpatialHolder sh = e.getValue();
                if (sh.layerId == lid) {
                    if (sh.spatial != null) sh.spatial.removeFromParent();
                    it.remove();
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

        rt(() -> {
            Layer l = layers.get(lid);
            if (l == null) return;

            for (Iterator<Map.Entry<Integer, SpatialHolder>> it = elements.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, SpatialHolder> e = it.next();
                SpatialHolder sh = e.getValue();
                if (sh.layerId == lid) {
                    if (sh.spatial != null) sh.spatial.removeFromParent();
                    it.remove();
                }
            }

            l.root.detachAllChildren();
        });
    }

    // ------------------------------------------------------------
    // Elements (root on layer)
    // ------------------------------------------------------------

    @Override
    @HostAccess.Export
    public void setCursorEnabled(boolean enabled) {
        rt(() -> {
            ensureLemur();
            GuiGlobals.getInstance().setCursorEventsEnabled(enabled);
            // опционально: если хочешь, чтобы именно jME курсор тоже прятался/показывался
            app.getInputManager().setCursorVisible(enabled);
        });
    }

    @Override
    @HostAccess.Export
    public void setCursorEnabled(boolean enabled, boolean force) {
        rt(() -> {
            ensureLemur();
            GuiGlobals.getInstance().setCursorEventsEnabled(enabled, force);
            app.getInputManager().setCursorVisible(enabled);
        });
    }

    @Override
    @HostAccess.Export
    public HudElementHandle addContainer(HudLayerHandle layer, float x, float y) {
        final int lid = (layer == null) ? 0 : layer.id;
        final int id = nextElemId++;

        rt(() -> {
            ensureLemur();
            Layer l = reqLayer(lid);

            Container c = new Container();
            c.setLocalTranslation(x, y, 0);

            l.root.attachChild(c);
            elements.put(id, new SpatialHolder(id, lid, c));
        });

        return new HudElementHandle(id);
    }

    @Override
    @HostAccess.Export
    public HudElementHandle addPanel(HudLayerHandle layer, float x, float y, float w, float h) {
        final int lid = (layer == null) ? 0 : layer.id;
        final int id = nextElemId++;

        rt(() -> {
            ensureLemur();
            Layer l = reqLayer(lid);

            Panel p = new Panel();
            p.setLocalTranslation(x, y, 0);
            p.setPreferredSize(new Vector3f(w, h, 0));

            l.root.attachChild(p);
            elements.put(id, new SpatialHolder(id, lid, p));
        });

        return new HudElementHandle(id);
    }

    @Override
    @HostAccess.Export
    public HudElementHandle addLabel(HudLayerHandle layer, String text, float x, float y) {
        final int lid = (layer == null) ? 0 : layer.id;
        final int id = nextElemId++;
        final String t = (text == null) ? "" : text;

        rt(() -> {
            ensureLemur();
            Layer l = reqLayer(lid);

            Label label = new Label(t);
            label.setLocalTranslation(x, y, 0);

            l.root.attachChild(label);
            elements.put(id, new SpatialHolder(id, lid, label));
        });

        return new HudElementHandle(id);
    }

    // ------------------------------------------------------------
    // Elements (with parent)
    // ------------------------------------------------------------

    @Override
    @HostAccess.Export
    public HudElementHandle addContainer(HudLayerHandle layer, HudElementHandle parent, float x, float y) {
        final int lid = (layer == null) ? 0 : layer.id;
        final int id = nextElemId++;

        rt(() -> {
            ensureLemur();
            reqLayer(lid);
            Node root = resolveAttachRoot(lid, parent);

            Container c = new Container();
            c.setLocalTranslation(x, y, 0);

            root.attachChild(c);
            elements.put(id, new SpatialHolder(id, lid, c));
        });

        return new HudElementHandle(id);
    }

    @Override
    @HostAccess.Export
    public HudElementHandle addPanel(HudLayerHandle layer, HudElementHandle parent, float x, float y, float w, float h) {
        final int lid = (layer == null) ? 0 : layer.id;
        final int id = nextElemId++;

        rt(() -> {
            ensureLemur();
            reqLayer(lid);
            Node root = resolveAttachRoot(lid, parent);

            Panel p = new Panel();
            p.setLocalTranslation(x, y, 0);
            p.setPreferredSize(new Vector3f(w, h, 0));

            root.attachChild(p);
            elements.put(id, new SpatialHolder(id, lid, p));
        });

        return new HudElementHandle(id);
    }

    @Override
    @HostAccess.Export
    public HudElementHandle addLabel(HudLayerHandle layer, HudElementHandle parent, String text, float x, float y) {
        final int lid = (layer == null) ? 0 : layer.id;
        final int id = nextElemId++;
        final String t = (text == null) ? "" : text;

        rt(() -> {
            ensureLemur();
            reqLayer(lid);
            Node root = resolveAttachRoot(lid, parent);

            Label label = new Label(t);
            label.setLocalTranslation(x, y, 0);

            root.attachChild(label);
            elements.put(id, new SpatialHolder(id, lid, label));
        });

        return new HudElementHandle(id);
    }

    // ------------------------------------------------------------
    // Element ops
    // ------------------------------------------------------------

    @Override
    @HostAccess.Export
    public void setText(HudElementHandle element, String text) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        final String t = (text == null) ? "" : text;

        rt(() -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;

            if (sh.spatial instanceof Label l) {
                l.setText(t);
            }
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
            Vector3f lt = sh.spatial.getLocalTranslation();
            sh.spatial.setLocalTranslation(x, y, lt.z);
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

            if (sh.spatial instanceof Panel p) {
                p.setPreferredSize(new Vector3f(w, h, 0));
            }
        });
    }

    @Override
    @HostAccess.Export
    public void remove(HudElementHandle element) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        rt(() -> {
            SpatialHolder sh = elements.remove(id);
            if (sh == null || sh.spatial == null) return;
            sh.spatial.removeFromParent();
        });
    }

    // ------------------------------------------------------------
    // NEW: Viewport
    // ------------------------------------------------------------
    @Override
    @HostAccess.Export
    public HudViewport viewport() {
        // Это можно возвращать синхронно — чтение размеров окна потокобезопасно на практике,
        // но чтобы не спорить с life-cycle — читаем через rt и кэшируем.
        final int[] out = new int[2];

        rt(() -> {
            // Берём размеры реального рендера
            int w = app.getCamera() != null ? app.getCamera().getWidth() : 0;
            int h = app.getCamera() != null ? app.getCamera().getHeight() : 0;

            if (w <= 0 || h <= 0) {
                // fallback: guiNode camera может отличаться, но обычно тот же app camera
                w = 0; h = 0;
            }

            out[0] = w;
            out[1] = h;
        });

        return new HudViewport(out[0], out[1]);
    }

    // ------------------------------------------------------------
    // NEW: Typography
    // ------------------------------------------------------------
    @Override
    @HostAccess.Export
    public void setFontSize(HudElementHandle element, float px) {
        final int id = (element == null) ? 0 : element.id;
        if (id <= 0) return;

        final float size = (px > 1f && Float.isFinite(px)) ? px : 16f;

        rt(() -> {
            SpatialHolder sh = elements.get(id);
            if (sh == null || sh.spatial == null) return;

            // Lemur: Label supports setFontSize() in newer versions.
            if (sh.spatial instanceof Label l) {
                try {
                    l.setFontSize(size);
                } catch (Throwable ignored) {
                    // Если вдруг метод недоступен в конкретной версии Lemur —
                    // тогда оставляем как есть (не падаем, это дев-утилита).
                }
            }
        });
    }



    // ------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------

    private static final class Layer {
        final int id;
        final String name;
        final Node root = new Node("HudLayer");

        Layer(int id, String name) {
            this.id = id;
            this.name = name;
            root.setName("HudLayer:" + id + ":" + name);
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
}