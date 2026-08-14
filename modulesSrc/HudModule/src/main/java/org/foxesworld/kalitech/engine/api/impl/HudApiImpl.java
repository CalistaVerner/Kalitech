/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.Application
 *  com.jme3.math.ColorRGBA
 *  com.jme3.math.Vector3f
 *  com.jme3.renderer.Camera
 *  com.jme3.scene.Node
 *  com.jme3.scene.Spatial
 *  com.jme3.scene.Spatial$CullHint
 *  com.simsilica.lemur.Container
 *  com.simsilica.lemur.GuiGlobals
 *  com.simsilica.lemur.Label
 *  com.simsilica.lemur.Panel
 *  com.simsilica.lemur.TextField
 *  com.simsilica.lemur.component.QuadBackgroundComponent
 *  com.simsilica.lemur.core.GuiComponent
 *  com.simsilica.lemur.style.BaseStyles
 *  org.foxesworld.kalitech.engine.api.EngineApiImpl
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.api.module.AbstractApiModule
 *  org.foxesworld.kalitech.engine.api.module.ApiContext
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 */
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.core.GuiComponent;
import com.simsilica.lemur.style.BaseStyles;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.HudApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.modules.hud.HudCoords;
import org.foxesworld.kalitech.engine.modules.hud.HudSizeCache;
import org.foxesworld.kalitech.engine.modules.hud.HudSizing;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public final class HudApiImpl
extends AbstractApiModule
implements HudApi {
    private static final AtomicInteger IDS = new AtomicInteger(1000);
    private final HudSizeCache sizeCache = new HudSizeCache();
    private final ConcurrentHashMap<Integer, Layer> layers = new ConcurrentHashMap();
    private final ConcurrentHashMap<Integer, SpatialHolder> elements = new ConcurrentHashMap();
    private EngineApiImpl engine;
    private volatile boolean inited;

    public HudApiImpl() {
        super("hud", "Hud", "1.0.0");
    }

    public HudApiImpl(EngineApiImpl engineApi) {
        this();
        this.bind(engineApi);
    }

    private static void attachTo(Spatial parent, Spatial child) {
        if (parent instanceof Node) {
            Node n = (Node)parent;
            n.attachChild(child);
        }
    }

    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.bind(ctx.engine);
    }

    private void bind(EngineApiImpl engine) {
        this.engine = engine;
        this.initOnce();
    }

    private void initOnce() {
        if (this.inited) {
            return;
        }
        this.inited = true;
        this.ensureLemur();
    }

    private void ensureLemur() {
        boolean initializedHere = false;
        try {
            if (GuiGlobals.getInstance() == null) {
                GuiGlobals.initialize((Application)this.engine.getApp());
                initializedHere = true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            // The normal engine bootstrap already loads the glass style once.
            // Only pay the Groovy/style parsing cost here when this module had
            // to bootstrap Lemur on its own.
            if (initializedHere) {
                BaseStyles.loadGlassStyle();
            }
            GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private Node guiNode() {
        Node n = this.engine.getApp().getGuiNode();
        if (n == null) {
            throw new IllegalStateException("HudApiImpl: app.getGuiNode() returned null");
        }
        return n;
    }

    private Application app() {
        return this.engine.getApp();
    }

    private int vpW() {
        Camera cam = this.app().getCamera();
        return cam != null ? cam.getWidth() : 0;
    }

    private int vpH() {
        Camera cam = this.app().getCamera();
        return cam != null ? cam.getHeight() : 0;
    }

    private Layer reqLayer(int id) {
        Layer l = this.layers.get(id);
        if (l == null) {
            throw new IllegalArgumentException("hud: unknown layer id=" + id);
        }
        return l;
    }

    private SpatialHolder reqElement(int id) {
        SpatialHolder sh = this.elements.get(id);
        if (sh == null) {
            throw new IllegalArgumentException("hud: unknown element id=" + id);
        }
        return sh;
    }

    private boolean parentIsLayerRoot(Spatial parent) {
        if (parent == null) {
            return false;
        }
        for (Layer l : this.layers.values()) {
            if (l.root != parent) continue;
            return true;
        }
        return false;
    }

    private void rt(Runnable r) {
        this.app().enqueue(() -> {
            r.run();
            return null;
        });
    }

    private float parentHeightOf(Spatial parent) {
        if (parent == null) {
            return 0.0f;
        }
        for (Map.Entry<Integer, SpatialHolder> e : this.elements.entrySet()) {
            SpatialHolder sh = e.getValue();
            if (sh == null || sh.spatial != parent) continue;
            float h = this.sizeCache.getH(sh.id);
            if (!(h > 0.0f)) break;
            return h;
        }
        return HudSizing.preferredH(parent);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public HudApi.HudLayerHandle createLayer(String name) {
        int id = IDS.incrementAndGet();
        Object nm = name == null || name.isBlank() ? "layer-" + id : name;
        Node root = new Node("hud:" + (String)nm + ":" + id);
        root.setLocalTranslation(0.0f, 0.0f, 0.0f);
        this.layers.put(id, new Layer(id, root));
        this.rt(() -> this.guiNode().attachChild((Spatial)root));
        return new HudApi.HudLayerHandle(id);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void destroyLayer(HudApi.HudLayerHandle layer) {
        int lid;
        int n = lid = layer == null ? 0 : layer.id;
        if (lid <= 0) {
            return;
        }
        Layer l = this.layers.remove(lid);
        if (l == null) {
            return;
        }
        Iterator<Map.Entry<Integer, SpatialHolder>> it = this.elements.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, SpatialHolder> e = it.next();
            SpatialHolder sh = e.getValue();
            if (sh == null || sh.layerId != lid) continue;
            it.remove();
            this.sizeCache.remove(sh.id);
            Spatial s = sh.spatial;
            if (s == null) continue;
            this.rt(() -> ((Spatial)s).removeFromParent());
        }
        this.rt(() -> ((Node)l.root).removeFromParent());
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void clearLayer(HudApi.HudLayerHandle layer) {
        int lid;
        int n = lid = layer == null ? 0 : layer.id;
        if (lid <= 0) {
            return;
        }
        Layer l = this.layers.get(lid);
        if (l == null) {
            return;
        }
        Iterator<Map.Entry<Integer, SpatialHolder>> it = this.elements.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, SpatialHolder> e = it.next();
            SpatialHolder sh = e.getValue();
            if (sh == null || sh.layerId != lid) continue;
            it.remove();
            this.sizeCache.remove(sh.id);
            Spatial s = sh.spatial;
            if (s == null) continue;
            this.rt(() -> ((Spatial)s).removeFromParent());
        }
        this.rt(() -> ((Node)l.root).detachAllChildren());
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=true, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public String getText(HudApi.HudElementHandle element) {
        int id;
        int n = id = element == null ? 0 : element.id;
        if (id <= 0) {
            return "";
        }
        try {
            return (String)this.app().enqueue(() -> {
                SpatialHolder sh = this.elements.get(id);
                if (sh == null || sh.spatial == null) {
                    return "";
                }
                Spatial s = sh.spatial;
                if (s instanceof Label) {
                    Label l = (Label)s;
                    String t = l.getText();
                    return t != null ? t : "";
                }
                if (s instanceof TextField) {
                    TextField tf = (TextField)s;
                    String t = tf.getText();
                    return t != null ? t : "";
                }
                return "";
            }).get();
        }
        catch (Exception e) {
            throw new RuntimeException("[HudApiImpl] getText failed id=" + id, e);
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public HudApi.HudElementHandle addContainer(HudApi.HudLayerHandle layer, float x, float y) {
        int lid;
        int n = lid = layer == null ? 0 : layer.id;
        if (lid <= 0) {
            return new HudApi.HudElementHandle(0);
        }
        Layer l = this.reqLayer(lid);
        int id = IDS.incrementAndGet();
        Container c = new Container();
        c.setName("hud.container:" + id);
        float elemH = HudSizing.heightOf(id, (Spatial)c, this.sizeCache);
        float guiY = HudCoords.toGuiYBox(this.vpH(), y, elemH);
        c.setLocalTranslation(x, guiY, 0.0f);
        this.elements.put(id, new SpatialHolder(id, l.id, (Spatial)c));
        this.rt(() -> l.root.attachChild((Spatial)c));
        return new HudApi.HudElementHandle(id);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public HudApi.HudElementHandle addPanel(HudApi.HudLayerHandle layer, float x, float y, float w, float h) {
        float elemH;
        int lid;
        int n = lid = layer == null ? 0 : layer.id;
        if (lid <= 0) {
            return new HudApi.HudElementHandle(0);
        }
        Layer l = this.reqLayer(lid);
        int id = IDS.incrementAndGet();
        Panel p = new Panel();
        p.setName("hud.panel:" + id);
        if (w > 0.0f && h > 0.0f) {
            HudSizing.forceSize(id, (Spatial)p, w, h, this.sizeCache);
        }
        if ((elemH = HudSizing.heightOf(id, (Spatial)p, this.sizeCache)) <= 0.0f && h > 0.0f) {
            elemH = h;
        }
        float guiY = HudCoords.toGuiYBox(this.vpH(), y, elemH);
        p.setLocalTranslation(x, guiY, 0.0f);
        this.elements.put(id, new SpatialHolder(id, l.id, (Spatial)p));
        this.rt(() -> l.root.attachChild((Spatial)p));
        return new HudApi.HudElementHandle(id);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public HudApi.HudElementHandle addLabel(HudApi.HudLayerHandle layer, String text, float x, float y) {
        int lid;
        int n = lid = layer == null ? 0 : layer.id;
        if (lid <= 0) {
            return new HudApi.HudElementHandle(0);
        }
        Layer l = this.reqLayer(lid);
        int id = IDS.incrementAndGet();
        Label label = new Label(text != null ? text : "");
        label.setName("hud.label:" + id);
        float guiY = HudCoords.toGuiYPoint(this.vpH(), y);
        label.setLocalTranslation(x, guiY, 0.0f);
        this.elements.put(id, new SpatialHolder(id, l.id, (Spatial)label));
        this.rt(() -> l.root.attachChild((Spatial)label));
        return new HudApi.HudElementHandle(id);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public HudApi.HudElementHandle addContainer(HudApi.HudLayerHandle layer, HudApi.HudElementHandle parent, float x, float y) {
        int pid;
        int lid = layer == null ? 0 : layer.id;
        int n = pid = parent == null ? 0 : parent.id;
        if (lid <= 0 || pid <= 0) {
            return new HudApi.HudElementHandle(0);
        }
        Layer l = this.reqLayer(lid);
        SpatialHolder ph = this.reqElement(pid);
        int id = IDS.incrementAndGet();
        Container c = new Container();
        c.setName("hud.container:" + id);
        float parentH = HudSizing.heightOf(ph.id, ph.spatial, this.sizeCache);
        if (parentH <= 0.0f) {
            parentH = this.parentHeightOf(ph.spatial);
        }
        float childH = HudSizing.heightOf(id, (Spatial)c, this.sizeCache);
        float localY = HudCoords.toLocalYBox(y, parentH, childH);
        c.setLocalTranslation(x, localY, 0.0f);
        this.elements.put(id, new SpatialHolder(id, l.id, (Spatial)c));
        this.rt(() -> HudApiImpl.attachTo(ph.spatial, (Spatial)c));
        return new HudApi.HudElementHandle(id);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public HudApi.HudElementHandle addPanel(HudApi.HudLayerHandle layer, HudApi.HudElementHandle parent, float x, float y, float w, float h) {
        float childH;
        float parentH;
        int pid;
        int lid = layer == null ? 0 : layer.id;
        int n = pid = parent == null ? 0 : parent.id;
        if (lid <= 0 || pid <= 0) {
            return new HudApi.HudElementHandle(0);
        }
        Layer l = this.reqLayer(lid);
        SpatialHolder ph = this.reqElement(pid);
        int id = IDS.incrementAndGet();
        Panel p = new Panel();
        p.setName("hud.panel:" + id);
        if (w > 0.0f && h > 0.0f) {
            HudSizing.forceSize(id, (Spatial)p, w, h, this.sizeCache);
        }
        if ((parentH = HudSizing.heightOf(ph.id, ph.spatial, this.sizeCache)) <= 0.0f) {
            parentH = this.parentHeightOf(ph.spatial);
        }
        if ((childH = HudSizing.heightOf(id, (Spatial)p, this.sizeCache)) <= 0.0f && h > 0.0f) {
            childH = h;
        }
        float localY = HudCoords.toLocalYBox(y, parentH, childH);
        p.setLocalTranslation(x, localY, 0.0f);
        this.elements.put(id, new SpatialHolder(id, l.id, (Spatial)p));
        this.rt(() -> HudApiImpl.attachTo(ph.spatial, (Spatial)p));
        return new HudApi.HudElementHandle(id);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public HudApi.HudElementHandle addLabel(HudApi.HudLayerHandle layer, HudApi.HudElementHandle parent, String text, float x, float y) {
        int pid;
        int lid = layer == null ? 0 : layer.id;
        int n = pid = parent == null ? 0 : parent.id;
        if (lid <= 0 || pid <= 0) {
            return new HudApi.HudElementHandle(0);
        }
        Layer l = this.reqLayer(lid);
        SpatialHolder ph = this.reqElement(pid);
        int id = IDS.incrementAndGet();
        Label label = new Label(text != null ? text : "");
        label.setName("hud.label:" + id);
        float parentH = HudSizing.heightOf(ph.id, ph.spatial, this.sizeCache);
        if (parentH <= 0.0f) {
            parentH = this.parentHeightOf(ph.spatial);
        }
        float localY = HudCoords.toLocalYPoint(y, parentH);
        label.setLocalTranslation(x, localY, 0.0f);
        this.elements.put(id, new SpatialHolder(id, l.id, (Spatial)label));
        this.rt(() -> HudApiImpl.attachTo(ph.spatial, (Spatial)label));
        return new HudApi.HudElementHandle(id);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setCursorEnabled(boolean enabled) {
        this.setCursorEnabled(enabled, false);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setCursorEnabled(boolean enabled, boolean force) {
        this.rt(() -> {
            try {
                if (GuiGlobals.getInstance() != null) {
                    GuiGlobals.getInstance().setCursorEventsEnabled(enabled);
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setText(HudApi.HudElementHandle element, String text) {
        int id;
        int n = id = element == null ? 0 : element.id;
        if (id <= 0) {
            return;
        }
        String t = text != null ? text : "";
        this.rt(() -> {
            SpatialHolder sh = this.elements.get(id);
            if (sh == null || sh.spatial == null) {
                return;
            }
            Spatial patt17483$temp = sh.spatial;
            if (patt17483$temp instanceof Label) {
                Label l = (Label)patt17483$temp;
                l.setText(t);
            }
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setVisible(HudApi.HudElementHandle element, boolean visible) {
        int id;
        int n = id = element == null ? 0 : element.id;
        if (id <= 0) {
            return;
        }
        this.rt(() -> {
            SpatialHolder sh = this.elements.get(id);
            if (sh == null || sh.spatial == null) {
                return;
            }
            sh.spatial.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setPosition(HudApi.HudElementHandle element, float x, float y) {
        int id;
        int n = id = element == null ? 0 : element.id;
        if (id <= 0) {
            return;
        }
        this.rt(() -> {
            float newY;
            boolean rooted;
            SpatialHolder sh = this.elements.get(id);
            if (sh == null || sh.spatial == null) {
                return;
            }
            Spatial s = sh.spatial;
            Node parent = s.getParent();
            boolean bl = rooted = parent == null || this.parentIsLayerRoot((Spatial)parent);
            if (rooted) {
                if (HudSizing.isBoxLike(s)) {
                    float eh = HudSizing.heightOf(id, s, this.sizeCache);
                    newY = HudCoords.toGuiYBox(this.vpH(), y, eh);
                } else {
                    newY = HudCoords.toGuiYPoint(this.vpH(), y);
                }
            } else {
                float parentH = this.parentHeightOf((Spatial)parent);
                if (HudSizing.isBoxLike(s)) {
                    float ch = HudSizing.heightOf(id, s, this.sizeCache);
                    newY = HudCoords.toLocalYBox(y, parentH, ch);
                } else {
                    newY = HudCoords.toLocalYPoint(y, parentH);
                }
            }
            Vector3f lt = s.getLocalTranslation();
            float z = lt != null ? lt.z : 0.0f;
            s.setLocalTranslation(x, newY, z);
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setSize(HudApi.HudElementHandle element, float w, float h) {
        int id;
        int n = id = element == null ? 0 : element.id;
        if (id <= 0) {
            return;
        }
        this.rt(() -> {
            SpatialHolder sh = this.elements.get(id);
            if (sh == null || sh.spatial == null) {
                return;
            }
            Spatial s = sh.spatial;
            boolean box = HudSizing.isBoxLike(s);
            float oldH = 0.0f;
            if (box) {
                oldH = this.sizeCache.getH(id);
                if (!(oldH > 0.0f)) {
                    oldH = HudSizing.preferredH(s);
                }
                if (!(oldH > 0.0f)) {
                    oldH = 0.0f;
                }
            }
            HudSizing.forceSize(id, s, w, h, this.sizeCache);
            if (box) {
                float dh;
                float newH = this.sizeCache.getH(id);
                if (!(newH > 0.0f)) {
                    newH = HudSizing.preferredH(s);
                }
                if (!(newH > 0.0f)) {
                    newH = oldH;
                }
                if ((dh = newH - oldH) != 0.0f) {
                    Vector3f lt = s.getLocalTranslation();
                    float x0 = lt != null ? lt.x : 0.0f;
                    float y0 = lt != null ? lt.y : 0.0f;
                    float z0 = lt != null ? lt.z : 0.0f;
                    s.setLocalTranslation(x0, y0 - dh, z0);
                }
            }
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setFontSize(HudApi.HudElementHandle element, float px) {
        int id;
        int n = id = element == null ? 0 : element.id;
        if (id <= 0) {
            return;
        }
        float size = Float.isFinite(px) && px > 0.0f ? Math.max(6.0f, px) : 16.0f;
        this.rt(() -> {
            SpatialHolder sh = this.elements.get(id);
            if (sh == null || sh.spatial == null) {
                return;
            }
            Spatial patt21949$temp = sh.spatial;
            if (patt21949$temp instanceof Label) {
                Label l = (Label)patt21949$temp;
                try {
                    l.setFontSize(size);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setBgColor(HudApi.HudElementHandle element, double r, double g, double b, double a) {
        int id;
        int n = id = element == null ? 0 : element.id;
        if (id <= 0) {
            return;
        }
        float rr = LuaCfg.clamp01f((double)r);
        float gg = LuaCfg.clamp01f((double)g);
        float bb = LuaCfg.clamp01f((double)b);
        float aa = LuaCfg.clamp01f((double)a);
        this.rt(() -> {
            SpatialHolder sh = this.elements.get(id);
            if (sh == null || sh.spatial == null) {
                return;
            }
            Spatial s = sh.spatial;
            try {
                QuadBackgroundComponent bg = new QuadBackgroundComponent(new ColorRGBA(rr, gg, bb, aa));
                if (s instanceof Panel) {
                    Panel p = (Panel)s;
                    p.setBackground((GuiComponent)bg);
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setTextColor(HudApi.HudElementHandle element, double r, double g, double b, double a) {
        int id;
        int n = id = element == null ? 0 : element.id;
        if (id <= 0) {
            return;
        }
        float rr = LuaCfg.clamp01f((double)r);
        float gg = LuaCfg.clamp01f((double)g);
        float bb = LuaCfg.clamp01f((double)b);
        float aa = LuaCfg.clamp01f((double)a);
        this.rt(() -> {
            SpatialHolder sh = this.elements.get(id);
            if (sh == null || sh.spatial == null) {
                return;
            }
            Spatial patt24082$temp = sh.spatial;
            if (patt24082$temp instanceof Label) {
                Label l = (Label)patt24082$temp;
                try {
                    l.setColor(new ColorRGBA(rr, gg, bb, aa));
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void remove(HudApi.HudElementHandle element) {
        int id;
        int n = id = element == null ? 0 : element.id;
        if (id <= 0) {
            return;
        }
        SpatialHolder sh = this.elements.remove(id);
        this.sizeCache.remove(id);
        if (sh == null || sh.spatial == null) {
            return;
        }
        this.rt(() -> ((Spatial)sh.spatial).removeFromParent());
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public HudApi.HudViewport viewport() {
        return new HudApi.HudViewport(this.vpW(), this.vpH());
    }

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
}

