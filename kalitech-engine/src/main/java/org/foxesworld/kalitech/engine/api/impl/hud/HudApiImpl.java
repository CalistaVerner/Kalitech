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

    /** HUD root is rendered in its own viewport (NOT guiNode) */
    private final Node hudRoot = new Node("kalitech:hudRoot");

    private ViewPort hudVP;
    private Camera hudCam;

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, HudElement> byId = new ConcurrentHashMap<>();

    private volatile int lastW = -1;
    private volatile int lastH = -1;

    public HudApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = Objects.requireNonNull(engine.getApp(), "app");

        onJme(() -> {
            hudRoot.setQueueBucket(RenderQueue.Bucket.Gui);
            hudRoot.setCullHint(Spatial.CullHint.Never);

            int w = app.getCamera().getWidth();
            int h = app.getCamera().getHeight();
            lastW = w;
            lastH = h;

            ensureHudViewport(w, h, "ctor");
            relayoutAll();
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
            ensureHudViewport(lastW, lastH, "create");

            HudElement parent = resolveParent(cfg);

            final HudElement el;
            switch (kind) {
                case "group" -> el = new HudGroup(id);
                case "rect"  -> el = new HudRect(id);
                case "image" -> el = new HudImage(id);
                default -> throw new IllegalArgumentException("hud.create: unknown kind='" + kind + "'");
            }

            applyCommon(el, cfg);

            if (parent != null) parent.attach(el);
            else hudRoot.attachChild(el.node);

            forceGui(el.node);
            byId.put(id, el);

            if (el instanceof HudRect r)  applyRectProps(r, cfg);
            if (el instanceof HudImage i) applyImageProps(i, cfg);

            relayoutAll();

            dbg("create id={} kind={} parent={} visible={} size={}x{} anchor={} pivot={}",
                    id, kind,
                    parent == null ? "ROOT" : String.valueOf(parent.id),
                    el.visible, el.w, el.h,
                    el.anchor, el.pivot
            );
        });

        return handle;
    }

    @HostAccess.Export
    @Override
    public void set(Object handleOrId, Object cfg) {
        if (handleOrId == null || cfg == null) return;

        onJme(() -> {
            ensureHudViewport(lastW, lastH, "set");

            HudElement el = require(handleOrId, "hud.set");
            applyCommon(el, cfg);

            Object parentObj = HudValueParsers.member(cfg, "parent");
            if (parentObj != null) {
                HudElement newParent = resolveParent(cfg);
                reparent(el, newParent);
            }

            if (el instanceof HudRect r)  applyRectProps(r, cfg);
            if (el instanceof HudImage i) applyImageProps(i, cfg);

            forceGui(el.node);
            relayoutAll();
        });
    }

    @HostAccess.Export
    @Override
    public void destroy(Object handleOrId) {
        if (handleOrId == null) return;

        onJme(() -> {
            HudElement el = require(handleOrId, "hud.destroy");
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
            lastW = w;
            lastH = h;
            onJme(() -> {
                ensureHudViewport(w, h, "resize");
                relayoutAll();
            });
        } else {
            // if someone disabled our viewport, re-enable softly
            onJme(() -> {
                if (hudVP != null && !hudVP.isEnabled()) {
                    hudVP.setEnabled(true);
                    dbg("tick: hudVP was disabled -> enabled");
                }
            });
        }
    }

    // ------------------------
    // AAA viewport pipeline
    // ------------------------

    /**
     * HUD is rendered in a dedicated PostView with its own camera.
     * Critical: clear DEPTH so world depth never hides GUI.
     */
    private void ensureHudViewport(int w, int h, String where) {
        try {
            if (hudVP == null || hudCam == null) {
                hudCam = new Camera(w, h);
                hudCam.setParallelProjection(true);

                // Orthographic pixel space: x:[0..w], y:[0..h]
                float near = 1f;
                float far  = 1000f;
                hudCam.setFrustum(near, far, 0f, (float) w, (float) h, 0f);
                hudCam.setLocation(new Vector3f(0f, 0f, 10f));
                hudCam.lookAtDirection(new Vector3f(0f, 0f, -1f), new Vector3f(0f, 1f, 0f));
                hudCam.update();

                hudVP = app.getRenderManager().createPostView("kalitech:hudVP", hudCam);
                // DO NOT clear color (keep scene), DO clear depth (important), DO NOT clear stencil
                hudVP.setClearFlags(false, true, false);
                hudVP.setEnabled(true);

                hudVP.attachScene(hudRoot);
                forceGui(hudRoot);

                dbg("{}: hudVP created postView w={} h={} clearFlags(color={}, depth={}, stencil={})",
                        where, w, h, false, true, false
                );
                return;
            }

            // Resize / keep frustum stable
            if (hudCam.getWidth() != w || hudCam.getHeight() != h) {
                hudCam.resize(w, h, true);
                hudCam.setParallelProjection(true);
                hudCam.setFrustum(1f, 1000f, 0f, (float) w, (float) h, 0f);
                hudCam.update();
                dbg("{}: hudCam resized {}x{}", where, w, h);
            }

            if (!hudVP.isEnabled()) {
                hudVP.setEnabled(true);
                dbg("{}: hudVP enabled", where);
            }

            // Make sure scene is still attached
            boolean hasRoot = false;
            try {
                for (Spatial s : hudVP.getScenes()) {
                    if (s == hudRoot) { hasRoot = true; break; }
                }
            } catch (Throwable ignored) {}
            if (!hasRoot) {
                hudVP.attachScene(hudRoot);
                dbg("{}: hudRoot re-attached to hudVP", where);
            }

            forceGui(hudRoot);
        } catch (Throwable t) {
            log.error("[hud] ensureHudViewport failed at {}: {}", where, t.toString(), t);
        }
    }

    // ------------------------
    // Internals
    // ------------------------

    private void dbg(String fmt, Object... args) {
        if (!DEBUG) return;
        try { log.info("[hud] " + fmt, args); } catch (Throwable ignored) {}
    }

    private void onJme(Runnable r) {
        if (engine.isJmeThread()) {
            try { r.run(); } catch (Throwable t) { log.error("[hud] jme task failed", t); }
        } else {
            app.enqueue(() -> {
                try { r.run(); } catch (Throwable t) { log.error("[hud] jme task failed", t); }
                return null;
            });
        }
    }

    private static void forceGui(Spatial s) {
        if (s == null) return;
        s.setQueueBucket(RenderQueue.Bucket.Gui);
        s.setCullHint(Spatial.CullHint.Never);
        if (s instanceof Node n) for (Spatial c : n.getChildren()) forceGui(c);
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

        try { el.applyVisibility(); } catch (Throwable ignored) {}
    }

    private void applyRectProps(HudRect r, Object cfg) {
        Object size = HudValueParsers.member(cfg, "size");
        if (size != null) {
            float w = (float) HudValueParsers.asNum(HudValueParsers.member(size, "w"), r.w);
            float h = (float) HudValueParsers.asNum(HudValueParsers.member(size, "h"), r.h);
            r.setSize(w, h, engine);
        }

        Object col = HudValueParsers.member(cfg, "color");
        if (col != null) {
            float cr = (float) HudValueParsers.asNum(HudValueParsers.member(col, "r"), r.color.r);
            float cg = (float) HudValueParsers.asNum(HudValueParsers.member(col, "g"), r.color.g);
            float cb = (float) HudValueParsers.asNum(HudValueParsers.member(col, "b"), r.color.b);
            float ca = (float) HudValueParsers.asNum(HudValueParsers.member(col, "a"), r.color.a);
            r.setColor(new ColorRGBA(cr, cg, cb, ca), engine);
        }
    }

    private void applyImageProps(HudImage img, Object cfg) {
        // image first
        Object imgObj = HudValueParsers.member(cfg, "image");
        if (imgObj == null) imgObj = HudValueParsers.member(cfg, "texture");
        if (imgObj != null) {
            String asset = HudValueParsers.asString(imgObj, null);
            if (asset != null && !asset.isBlank()) img.setImage(asset, engine);
        }

        // then size (so texture stays visible after resize)
        Object size = HudValueParsers.member(cfg, "size");
        if (size != null) {
            float w = (float) HudValueParsers.asNum(HudValueParsers.member(size, "w"), img.w);
            float h = (float) HudValueParsers.asNum(HudValueParsers.member(size, "h"), img.h);
            img.setSize(w, h, engine);
        }

        Object col = HudValueParsers.member(cfg, "color");
        if (col != null) {
            float cr = (float) HudValueParsers.asNum(HudValueParsers.member(col, "r"), img.color.r);
            float cg = (float) HudValueParsers.asNum(HudValueParsers.member(col, "g"), img.color.g);
            float cb = (float) HudValueParsers.asNum(HudValueParsers.member(col, "b"), img.color.b);
            float ca = (float) HudValueParsers.asNum(HudValueParsers.member(col, "a"), img.color.a);
            img.setColor(new ColorRGBA(cr, cg, cb, ca), engine);
        }
    }

    private void relayoutAll() {
        ensureHudViewport(lastW, lastH, "relayoutAll");

        final int w = lastW;
        final int h = lastH;

        for (HudElement el : byId.values()) {
            if (el == null) continue;
            if (el.parent != null) continue;

            HudLayout.layoutRecursive(el, w, h, w, h);
            forceGui(el.node);
        }

        try { hudRoot.updateGeometricState(); } catch (Throwable ignored) {}
    }
}
