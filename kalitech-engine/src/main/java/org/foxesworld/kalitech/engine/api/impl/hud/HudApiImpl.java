// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudApiImpl.java
package org.foxesworld.kalitech.engine.api.impl.hud;

import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.HudApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class HudApiImpl implements HudApi {

    private static final Logger log = LogManager.getLogger(HudApiImpl.class);
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("hud.debug", "false"));

    private final EngineApiImpl engine;
    private final SimpleApplication app;

    /**
     * Root node attached to guiNode. All HUD elements are children of this node.
     */
    private final Node hudRoot = new Node("kalitech:hudRoot");

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, HudElement> byId = new ConcurrentHashMap<>();

    /**
     * Track last viewport to detect resize and relayout.
     */
    private volatile int lastW = -1;
    private volatile int lastH = -1;

    // internal: once-only test quad
    private volatile boolean guiSelfTestCreated = false;

    public HudApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = Objects.requireNonNull(engine.getApp(), "app");

        onJme(() -> {
            ensureGuiConfigured("ctor");
            attachHudRootIfNeeded("ctor");

            lastW = app.getCamera().getWidth();
            lastH = app.getCamera().getHeight();

            // ✅ НЕ ломаем frustum вручную. Только гарантируем parallel+resize.
            configureGuiCameraSoft(lastW, lastH, "ctor");
            relayoutAll();
            dbg("init hudRootParent={} guiChildren={} guiVP.enabled={} guiVP.scenes={} guiCam.parallel={} guiCam={}x{}",
                    hudRoot.getParent() != null,
                    app.getGuiNode().getQuantity(),
                    safeGuiEnabled(),
                    safeGuiScenesCount(),
                    safeGuiCamParallel(),
                    safeGuiCamW(),
                    safeGuiCamH()
            );
        });
    }

    // ------------------------
    // Public API
    // ------------------------

    @HostAccess.Export
    @Override
    public HudHandle create(Object cfg) {
        if (cfg == null) throw new IllegalArgumentException("hud.create(cfg): cfg is required");

        String kind = HudValueParsers.asString(HudValueParsers.member(cfg, "kind"), null);
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("hud.create(cfg): cfg.kind is required");

        final int id = ids.getAndIncrement();
        final HudHandle handle = new HudHandle(id);

        onJme(() -> {
            ensureGuiConfigured("create");
            attachHudRootIfNeeded("create");

            HudElement parent = resolveParent(cfg);

            HudElement el;
            switch (kind) {
                case "group" -> el = new HudGroup(id);
                case "rect" -> el = new HudRect(id);
                default -> throw new IllegalArgumentException("hud.create: unknown kind='" + kind + "'");
            }

            applyCommon(el, cfg);

            // attach
            if (parent != null) parent.attach(el);
            else hudRoot.attachChild(el.node);

            // make sure gui profile is applied to subtree
            forceGui(el.node);

            byId.put(id, el);

            // kind-specific init
            if (el instanceof HudRect r) applyRectProps(r, cfg);

            dbg("create id={} kind={} parent={} visible={} size={}x{} off=({}, {}) anchor={} pivot={}",
                    id, kind,
                    parent == null ? "ROOT" : String.valueOf(parent.id),
                    el.visible, el.w, el.h,
                    el.offsetX, el.offsetY,
                    el.anchor, el.pivot
            );

            relayoutAll();

            dbg("create.afterLayout id={} local={} parentLocal={}",
                    id,
                    el.node.getLocalTranslation(),
                    el.parent == null ? "(root)" : el.parent.node.getLocalTranslation()
            );
        });

        return handle;
    }

    @HostAccess.Export
    @Override
    public void set(Object handleOrId, Object cfg) {
        if (handleOrId == null || cfg == null) return;

        onJme(() -> {
            ensureGuiConfigured("set");
            attachHudRootIfNeeded("set");

            HudElement el = require(handleOrId, "hud.set");
            applyCommon(el, cfg);

            Object parentObj = HudValueParsers.member(cfg, "parent");
            if (parentObj != null) {
                HudElement newParent = resolveParent(cfg);
                reparent(el, newParent);
                dbg("set.reparent id={} newParent={}", el.id, newParent == null ? "ROOT" : String.valueOf(newParent.id));
            }

            if (el instanceof HudRect r) applyRectProps(r, cfg);

            forceGui(el.node);
            relayoutAll();
        });
    }

    @HostAccess.Export
    @Override
    public void destroy(Object handleOrId) {
        if (handleOrId == null) return;

        onJme(() -> {
            ensureGuiConfigured("destroy");
            attachHudRootIfNeeded("destroy");

            HudElement el = require(handleOrId, "hud.destroy");
            dbg("destroy id={} children={}", el.id, el.children.size());

            destroyRecursive(el);
            relayoutAll();
        });
    }

    @HostAccess.Export
    @Override
    public Viewport viewport() {
        int w = app.getCamera().getWidth();
        int h = app.getCamera().getHeight();
        return new Viewport(w, h);
    }

    @Override
    public void __tick() {
        int w = app.getCamera().getWidth();
        int h = app.getCamera().getHeight();

        if (w != lastW || h != lastH) {
            dbg("resize {}x{} -> {}x{}", lastW, lastH, w, h);
            lastW = w;
            lastH = h;
            onJme(() -> {
                ensureGuiConfigured("resize");
                attachHudRootIfNeeded("resize");
                configureGuiCameraSoft(w, h, "resize");
                relayoutAll();
            });
        }
    }

    // ------------------------
    // Internals
    // ------------------------

    private void dbg(String fmt, Object... args) {
        if (!DEBUG) return;
        try {
            log.info("[hud] " + fmt, args);
        } catch (Throwable ignored) {
        }
    }

    private void onJme(Runnable r) {
        if (engine.isJmeThread()) {
            try {
                r.run();
            } catch (Throwable t) {
                log.error("[hud] jme task failed", t);
            }
        } else {
            app.enqueue(() -> {
                try {
                    r.run();
                } catch (Throwable t) {
                    log.error("[hud] jme task failed", t);
                }
                return null;
            });
        }
    }

    /**
     * ✅ Self-healing GUI setup.
     * Make guiVP enabled and GUARANTEE guiNode is attached as the ONLY root scene.
     */
    private void ensureGuiConfigured(String where) {
        try {
            ViewPort guiVP = app.getGuiViewPort();
            if (guiVP == null) return;

            if (!guiVP.isEnabled()) {
                guiVP.setEnabled(true);
                dbg("{}: guiVP was disabled -> enabled", where);
            }

            // Hard guarantee: guiVP must render guiNode.
            boolean hasGuiNode = false;
            try {
                for (Spatial s : guiVP.getScenes()) {
                    if (s == app.getGuiNode()) {
                        hasGuiNode = true;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }

            if (!hasGuiNode) {
                // 🔥 самый надёжный путь — пересобрать сцену viewport’а
                try {
                    guiVP.clearScenes();
                } catch (Throwable ignored) {
                }
                guiVP.attachScene(app.getGuiNode());
                dbg("{}: guiVP scenes rebuilt -> guiNode attached", where);
            }

            Node guiNode = app.getGuiNode();
            forceGui(guiNode);

        } catch (Throwable t) {
            dbg("{}: ensureGuiConfigured failed: {}", where, t.toString());
        }
    }

    private void attachHudRootIfNeeded(String where) {
        try {
            Node guiNode = app.getGuiNode();

            if (hudRoot.getParent() == null) {
                guiNode.attachChild(hudRoot);
                dbg("{}: hudRoot attached to guiNode", where);
            }

            // ensure GUI profile
            forceGui(hudRoot);
            // hudRoot should not be offset
            hudRoot.setLocalTranslation(0f, 0f, 0f);

        } catch (Throwable t) {
            dbg("{}: attachHudRootIfNeeded failed: {}", where, t.toString());
        }
    }

    /**
     * ✅ Soft GUI camera config: do NOT override frustum.
     * jME already configures guiCam for pixel space.
     */
    private void configureGuiCameraSoft(int w, int h, String where) {
        try {
            ViewPort guiVP = app.getGuiViewPort();
            if (guiVP == null) return;
            Camera cam = guiVP.getCamera();
            if (cam == null) return;

            cam.setParallelProjection(true);
            if (cam.getWidth() != w || cam.getHeight() != h) {
                cam.resize(w, h, true);
            }

            // keep conventional orientation (often already correct)
            cam.setLocation(new Vector3f(0f, 0f, 1f));
            cam.lookAtDirection(new Vector3f(0f, 0f, -1f), new Vector3f(0f, 1f, 0f));
            cam.update();

            dbg("{}: guiCam soft-config w={} h={} parallel={} loc={}",
                    where, w, h, cam.isParallelProjection(), cam.getLocation());

        } catch (Throwable t) {
            dbg("{}: configureGuiCameraSoft failed: {}", where, t.toString());
        }
    }

    /**
     * 🔒 Apply GUI render profile to whole subtree (Node bucket does NOT reliably propagate to Geometry).
     */
    private static void forceGui(Spatial s) {
        if (s == null) return;
        s.setQueueBucket(RenderQueue.Bucket.Gui);
        s.setCullHint(Spatial.CullHint.Never);

        if (s instanceof Node n) {
            for (Spatial c : n.getChildren()) {
                forceGui(c);
            }
        }
    }

    private boolean safeGuiEnabled() {
        try {
            return app.getGuiViewPort() != null && app.getGuiViewPort().isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int safeGuiScenesCount() {
        try {
            return app.getGuiViewPort() == null ? -1 : app.getGuiViewPort().getScenes().size();
        } catch (Throwable ignored) {
            return -2;
        }
    }

    private boolean safeGuiCamParallel() {
        try {
            return app.getGuiViewPort().getCamera() != null && app.getGuiViewPort().getCamera().isParallelProjection();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int safeGuiCamW() {
        try {
            return app.getGuiViewPort().getCamera() == null ? -1 : app.getGuiViewPort().getCamera().getWidth();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private int safeGuiCamH() {
        try {
            return app.getGuiViewPort().getCamera() == null ? -1 : app.getGuiViewPort().getCamera().getHeight();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private HudElement require(Object handleOrId, String where) {
        int id = resolveId(handleOrId);
        HudElement el = byId.get(id);
        if (el == null) throw new IllegalArgumentException(where + ": hud element not found id=" + id);
        return el;
    }

    private int resolveId(Object handleOrId) {
        if (handleOrId instanceof HudHandle h) return h.id;
        if (handleOrId instanceof Number n) return n.intValue();

        if (handleOrId instanceof Value v) {
            if (v.isNumber()) return v.asInt();
            if (v.hasMember("id")) {
                Value id = v.getMember("id");
                if (id != null && id.isNumber()) return id.asInt();
            }
        }

        if (handleOrId instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        throw new IllegalArgumentException("hud: invalid handle/id type: " + handleOrId.getClass().getName());
    }

    private HudElement resolveParent(Object cfg) {
        Object p = HudValueParsers.member(cfg, "parent");
        if (p == null) return null;
        int pid = resolveId(p);
        return byId.get(pid);
    }

    private void reparent(HudElement el, HudElement newParent) {
        HudElement oldParent = el.parent;
        if (oldParent == newParent) return;

        if (oldParent != null) oldParent.detach(el);
        else el.node.removeFromParent();

        if (newParent != null) newParent.attach(el);
        else hudRoot.attachChild(el.node);

        forceGui(el.node);
    }

    private void destroyRecursive(HudElement el) {
        List<HudElement> kids = new ArrayList<>(el.children);
        for (HudElement c : kids) destroyRecursive(c);

        if (el.parent != null) el.parent.detach(el);
        else el.node.removeFromParent();

        byId.remove(el.id);
        el.onDestroy();
    }

    private void applyCommon(HudElement el, Object cfg) {
        Object visObj = HudValueParsers.member(cfg, "visible");
        if (visObj != null) el.visible = HudValueParsers.asBool(visObj, true);

        String anchor = HudValueParsers.asString(HudValueParsers.member(cfg, "anchor"), null);
        if (anchor != null) el.anchor = HudLayout.anchorOf(anchor);

        String pivot = HudValueParsers.asString(HudValueParsers.member(cfg, "pivot"), null);
        if (pivot != null) el.pivot = HudLayout.pivotOf(pivot);

        Object off = HudValueParsers.member(cfg, "offset");
        if (off != null) {
            el.offsetX = (float) HudValueParsers.asNum(HudValueParsers.member(off, "x"), el.offsetX);
            el.offsetY = (float) HudValueParsers.asNum(HudValueParsers.member(off, "y"), el.offsetY);
        }
    }

    private void applyRectProps(HudRect r, Object cfg) {
        Object size = HudValueParsers.member(cfg, "size");
        if (size != null) {
            float w = (float) HudValueParsers.asNum(HudValueParsers.member(size, "w"), r.w);
            float h = (float) HudValueParsers.asNum(HudValueParsers.member(size, "h"), r.h);
            r.setSize(w, h, engine);
            dbg("rect.setSize id={} -> {}x{}", r.id, r.w, r.h);
        }

        Object col = HudValueParsers.member(cfg, "color");
        if (col != null) {
            float cr = (float) HudValueParsers.asNum(HudValueParsers.member(col, "r"), r.color.r);
            float cg = (float) HudValueParsers.asNum(HudValueParsers.member(col, "g"), r.color.g);
            float cb = (float) HudValueParsers.asNum(HudValueParsers.member(col, "b"), r.color.b);
            float ca = (float) HudValueParsers.asNum(HudValueParsers.member(col, "a"), r.color.a);
            r.setColor(new ColorRGBA(cr, cg, cb, ca), engine);
            dbg("rect.setColor id={} -> rgba({}, {}, {}, {})", r.id, cr, cg, cb, ca);
        }
    }

    private void relayoutAll() {
        ensureGuiConfigured("relayoutAll");
        attachHudRootIfNeeded("relayoutAll");

        final int w = app.getCamera().getWidth();
        final int h = app.getCamera().getHeight();

        dbg("relayoutAll cam={}x{} elements={} guiChildren={} hudRootParent={} guiVP.enabled={} guiVP.scenes={} guiCam.parallel={} guiCam={}x{}",
                w, h,
                byId.size(),
                app.getGuiNode().getQuantity(),
                hudRoot.getParent() != null,
                safeGuiEnabled(),
                safeGuiScenesCount(),
                safeGuiCamParallel(),
                safeGuiCamW(),
                safeGuiCamH()
        );

        for (HudElement el : byId.values()) {
            if (el == null) continue;
            if (el.parent != null) continue;

            HudLayout.layoutRecursive(el, w, h, w, h);

            // ensure subtree stays GUI
            forceGui(el.node);

            dbg("relayout.root id={} pos={} size={}x{} children={}",
                    el.id,
                    el.node.getLocalTranslation(),
                    el.w, el.h,
                    el.children.size()
            );
        }

        // help update bounds/state (safe in gui)
        try {
            app.getGuiNode().updateGeometricState();
        } catch (Throwable ignored) {
        }
    }
}