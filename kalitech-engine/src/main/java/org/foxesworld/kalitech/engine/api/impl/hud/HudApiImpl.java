// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudApiImpl.java
package org.foxesworld.kalitech.engine.api.impl.hud;

import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.impl.hud.font.HudSdfFontManager;
import org.foxesworld.kalitech.engine.api.interfaces.HudApi;
import org.graalvm.polyglot.HostAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AAA HUD API:
 *  - Own viewport (not guiNode)
 *  - Thread-safe public methods: all scene changes happen on JME thread (via __tick pending ops)
 *  - HTML HUD uses dirty/flush pipeline (no scenegraph mutation from non-JME threads)
 *  - Layout pipeline: HTML flush -> Yoga -> Flex fallback -> Anchor/Pivot pass
 *  - Modern TTF/SDF text (via HudSdfFontFontManager)
 *
 * Debugging:
 *  - hud.debug=true enables verbose logs
 *  - hud.debug.smoketest=true spawns a visible red rect once viewport is created
 *  - hud.debug.everyFrames=120 periodic HUD status logs
 */
public final class HudApiImpl implements HudApi {

    private static final Logger log = LogManager.getLogger(HudApiImpl.class);

    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("hud.debug", "true"));
    private static final boolean DEBUG_SMOKETEST = Boolean.parseBoolean(System.getProperty("hud.debug.smoketest", "false"));
    private static final int DEBUG_EVERY_FRAMES = Integer.parseInt(System.getProperty("hud.debug.everyFrames", "120"));

    private final ConcurrentLinkedQueue<Runnable> pendingOps = new ConcurrentLinkedQueue<>();

    private final EngineApiImpl engine;
    private final SimpleApplication app;

    /** HUD root rendered in its own viewport */
    private final Node hudRoot = new Node("kalitech:hudRoot");

    private ViewPort hudVP;
    private Camera hudCam;

    private volatile float uiScale = 1f;

    private volatile int lastW = -1;
    private volatile int lastH = -1;

    private final AtomicInteger ids = new AtomicInteger(1);

    /**
     * Elements indexed by id.
     * Scenegraph mutation is JME-thread only.
     * Map is concurrent because JS threads may call exists()/create() and we don't want UB.
     */
    private final Map<Integer, HudElement> byId = new ConcurrentHashMap<>();

    /** Reuse handle instances per id (optional but nice for JS identity/debug). */
    private final Map<Integer, HudHandle> handles = new ConcurrentHashMap<>();

    // Layout engines (Yoga/Flex)
    private final HudYogaLayout yoga = new HudYogaLayout();
    private final HudFlexLayout flex = new HudFlexLayout();

    // TTF/SDF font manager
    private final HudSdfFontManager fontManager;

    // Reused roots list to avoid allocations
    private final ArrayList<HudElement> tmpRoots = new ArrayList<>(256);

    // Dirty relayout flag (set when something changes)
    private volatile boolean relayoutDirty = false;

    /** Captured on first __tick(). */
    private volatile Thread jmeThread;

    /** Cached viewport DTO for JS (no engine classes exposed). */
    private volatile HudViewport viewportDto = new HudViewport(1, 1, 0, 0, 1.0f);

    private static final float DESIGN_W = Float.parseFloat(System.getProperty("hud.designW", "1920"));
    private static final float DESIGN_H = Float.parseFloat(System.getProperty("hud.designH", "1080"));


    // --- debug counters ---
    private int frameCounter = 0;
    private boolean smoketestSpawned = false;

    public HudApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = engine.getApp();

        this.hudRoot.setQueueBucket(RenderQueue.Bucket.Gui);
        this.hudRoot.setCullHint(com.jme3.scene.Spatial.CullHint.Never);

        this.fontManager = new HudSdfFontManager(app.getAssetManager());
        HudText.bindFontManager(this.fontManager);
        HudTextRuntime.bindFontManager(fontManager);

        if (DEBUG) {
            log.info("[hud] init debug={} smoketest={} everyFrames={} bakePx={} spreadPx={}",
                    DEBUG, DEBUG_SMOKETEST, DEBUG_EVERY_FRAMES,
                    Integer.parseInt(System.getProperty("hud.ttf.bakePx", "64")),
                    Integer.parseInt(System.getProperty("hud.ttf.spreadPx", "12")));
        }
    }

    // ------------------- Public API -------------------

    @HostAccess.Export
    @Override
    public HudHandle create(Object cfg) {
        if (cfg == null) throw new IllegalArgumentException("hud.create(cfg): cfg is required");

        String kind = HudValueParsers.asString(HudValueParsers.member(cfg, "kind"), null);
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("hud.create(cfg): cfg.kind is required");

        final int id = ids.getAndIncrement();
        final HudHandle handle = new HudHandle(id);
        handles.put(id, handle); // visible immediately for JS

        if (DEBUG) log.debug("[hud] create queued id={} kind={} cfgKeys? (js)", id, kind);

        postHudOp(() -> {
            ensureHudViewportForCurrentApp("create");

            HudElement parent = resolveParent(cfg);
            HudElement el = createElementOfKind(kind, id);

            applyCommon(el, cfg);
            switch (el.kind) {
                case TEXT -> applyText((HudText) el, cfg);
                case RECT -> applyRect((HudRect) el, cfg);
                case IMAGE -> applyImage((HudImage) el, cfg);
                case HTML -> applyHtml((HudHtml) el, cfg);
                default -> { /* group */ }
            }

            if (parent != null) parent.addChild(el);
            else hudRoot.attachChild(el.node);

            byId.put(id, el);
            relayoutDirty = true;

            if (DEBUG) log.debug("[hud] create applied id={} kind={} parent={} total={}",
                    id, el.kind, (parent != null ? parent.id : 0), byId.size());
        });

        return handle;
    }

    @HostAccess.Export
    @Override
    public void set(Object handleOrId, Object cfg) {
        if (cfg == null) return;
        final int fid = normalizeId(handleOrId);
        if (fid <= 0) return;

        if (DEBUG) log.debug("[hud] set queued id={}", fid);

        postHudOp(() -> {
            HudElement el = byId.get(fid);
            if (el == null) {
                if (DEBUG) log.debug("[hud] set ignored: id={} not found", fid);
                return;
            }

            applyCommon(el, cfg);
            switch (el.kind) {
                case TEXT -> applyText((HudText) el, cfg);
                case RECT -> applyRect((HudRect) el, cfg);
                case IMAGE -> applyImage((HudImage) el, cfg);
                case HTML -> applyHtml((HudHtml) el, cfg);
                default -> { /* group */ }
            }

            relayoutDirty = true;
        });
    }

    @HostAccess.Export
    @Override
    public void destroy(Object handleOrId) {
        final int fid = normalizeId(handleOrId);
        if (fid <= 0) return;

        if (DEBUG) log.debug("[hud] destroy queued id={}", fid);

        postHudOp(() -> {
            HudElement el = byId.get(fid);
            if (el == null) return;

            __destroyRecursiveInternal(el);
            relayoutDirty = true;

            if (DEBUG) log.debug("[hud] destroy applied id={} remaining={}", fid, byId.size());
        });
    }

    @HostAccess.Export
    public void clear() {
        if (DEBUG) log.debug("[hud] clear queued");

        postHudOp(() -> {
            ArrayList<HudElement> roots = new ArrayList<>(64);
            for (HudElement e : byId.values()) {
                if (e != null && e.parent == null) roots.add(e);
            }
            for (HudElement r : roots) __destroyRecursiveInternal(r);

            byId.clear();
            handles.clear();
            relayoutDirty = true;

            if (DEBUG) log.debug("[hud] clear applied");
        });
    }

    @HostAccess.Export
    public boolean exists(Object handleOrId) {
        final int fid = normalizeId(handleOrId);
        if (fid <= 0) return false;
        return byId.containsKey(fid);
    }

    @HostAccess.Export
    public void show(Object handleOrId) {
        final int fid = normalizeId(handleOrId);
        if (fid <= 0) return;
        postHudOp(() -> {
            HudElement el = byId.get(fid);
            if (el == null) return;
            el.visible = true;
            el.node.setCullHint(com.jme3.scene.Spatial.CullHint.Never);
            relayoutDirty = true;
        });
    }

    @HostAccess.Export
    public void hide(Object handleOrId) {
        final int fid = normalizeId(handleOrId);
        if (fid <= 0) return;
        postHudOp(() -> {
            HudElement el = byId.get(fid);
            if (el == null) return;
            el.visible = false;
            el.node.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
            relayoutDirty = true;
        });
    }

    @HostAccess.Export
    @Override
    public HudViewport viewport() {
        return viewportDto;
    }

    @HostAccess.Export
    @Override
    public void resize(int w, int h) {
        lastW = w;
        lastH = h;

        if (DEBUG) log.info("[hud] resize requested {}x{}", w, h);

        postHudOp(() -> {
            ensureHudViewport(w, h, "resize");
            if (hudCam != null) hudCam.resize(Math.max(1, w), Math.max(1, h), true);
            relayoutDirty = true;
        });
    }

    /**
     * Engine tick hook (call it each frame from JME update).
     * Executes pending ops + layout + updates HUD node states.
     */
    @Override
    public void __tick() {
        if (jmeThread == null) {
            jmeThread = Thread.currentThread();
            if (DEBUG) log.info("[hud] __tick bound to JME thread '{}'", jmeThread.getName());
        }

        frameCounter++;

        // Lazy viewport creation
        if (hudVP == null) {
            Camera c = app.getCamera();
            int w = (c != null) ? c.getWidth() : lastW;
            int h = (c != null) ? c.getHeight() : lastH;
            ensureHudViewport(Math.max(1, w), Math.max(1, h), "tick-lazy");
        }

        // 1) Execute pending ops
        int ops = 0;
        for (;;) {
            Runnable r = pendingOps.poll();
            if (r == null) break;
            ops++;
            try {
                r.run();
            } catch (Throwable t) {
                log.error("[hud] pending op failed", t);
            }
        }

        if (hudVP == null) {
            if (DEBUG && frameCounter % DEBUG_EVERY_FRAMES == 0) {
                log.warn("[hud] hudVP is STILL null after tick. appCam={} last={}x{} opsThisTick={}",
                        (app.getCamera() != null ? (app.getCamera().getWidth() + "x" + app.getCamera().getHeight()) : "null"),
                        lastW, lastH, ops);
            }
            return;
        }

        // Optional smoke-test: ensure something visible exists
        if (DEBUG_SMOKETEST && !smoketestSpawned) {
            smoketestSpawned = true;
            spawnSmokeTestRect();
        }

        // 2) Layout only when dirty
        boolean didLayout = false;
        if (relayoutDirty) {
            relayoutDirty = false;
            didLayout = true;
            relayoutAll();
        }

        // 3) Update HUD root states
        hudRoot.updateLogicalState(0f);
        hudRoot.updateGeometricState();

        if (DEBUG && frameCounter % DEBUG_EVERY_FRAMES == 0) {
            int roots = countRoots();
            log.info("[hud] tick frame={} ops={} layout={} elements={} roots={} vp={}x{} scale={} pendingOps={}",
                    frameCounter, ops, didLayout, byId.size(), roots,
                    hudCam != null ? hudCam.getWidth() : -1,
                    hudCam != null ? hudCam.getHeight() : -1,
                    uiScale, pendingOps.size());
        }
    }

    // ------------------- queueing -------------------

    private void postHudOp(Runnable op) {
        pendingOps.add(op);
    }

    // ------------------- Helpers -------------------

    private int normalizeId(Object handleOrId) {
        if (handleOrId instanceof HudHandle hh) return hh.id;
        return HudValueParsers.asInt(handleOrId, 0);
    }

    private HudElement resolveParent(Object cfg) {
        Object p = HudValueParsers.member(cfg, "parent");
        if (p == null) return null;
        int pid = (p instanceof HudHandle hh) ? hh.id : HudValueParsers.asInt(p, 0);
        if (pid <= 0) return null;
        return byId.get(pid);
    }

    // ------------------- common props -------------------

    private void applyCommon(HudElement el, Object cfg) {
        if (el == null || cfg == null) return;

        Object a = HudValueParsers.member(cfg, "anchor");
        if (a != null) el.anchor = HudLayout.anchorOf(HudValueParsers.asString(a, "topLeft"));

        Object p = HudValueParsers.member(cfg, "pivot");
        if (p != null) el.pivot = HudLayout.pivotOf(HudValueParsers.asString(p, "topLeft"));

        Object off = HudValueParsers.member(cfg, "offset");
        if (off != null) {
            el.offsetX = (float) HudValueParsers.asNum(HudValueParsers.member(off, "x"), el.offsetX);
            el.offsetY = (float) HudValueParsers.asNum(HudValueParsers.member(off, "y"), el.offsetY);
        } else {
            Object x = HudValueParsers.member(cfg, "x");
            Object y = HudValueParsers.member(cfg, "y");
            if (x != null) el.offsetX = (float) HudValueParsers.asNum(x, el.offsetX);
            if (y != null) el.offsetY = (float) HudValueParsers.asNum(y, el.offsetY);
        }

        Object size = HudValueParsers.member(cfg, "size");
        if (size != null) {
            float w = (float) HudValueParsers.asNum(HudValueParsers.member(size, "w"), el.w);
            float h = (float) HudValueParsers.asNum(HudValueParsers.member(size, "h"), el.h);
            if (w > 0f) el.w = w;
            if (h > 0f) el.h = h;
            if (w > 0f || h > 0f) el.contentSized = false;
        }

        Object pad = HudValueParsers.member(cfg, "padding");
        if (pad != null) {
            if (HudValueParsers.isNumber(pad)) {
                float pp = Math.max(0f, (float) HudValueParsers.asNum(pad, 0));
                el.padL = el.padT = el.padR = el.padB = pp;
            } else {
                el.padL = Math.max(0f, (float) HudValueParsers.asNum(HudValueParsers.member(pad, "l"), el.padL));
                el.padT = Math.max(0f, (float) HudValueParsers.asNum(HudValueParsers.member(pad, "t"), el.padT));
                el.padR = Math.max(0f, (float) HudValueParsers.asNum(HudValueParsers.member(pad, "r"), el.padR));
                el.padB = Math.max(0f, (float) HudValueParsers.asNum(HudValueParsers.member(pad, "b"), el.padB));
            }
        }

        Object gap = HudValueParsers.member(cfg, "gap");
        if (gap != null) el.gap = Math.max(0f, (float) HudValueParsers.asNum(gap, el.gap));

        String layout = HudValueParsers.asString(HudValueParsers.member(cfg, "layout"), null);
        if (layout != null) {
            if ("flex".equalsIgnoreCase(layout)) el.layoutKind = HudElement.LayoutKind.FLEX;
            else if ("absolute".equalsIgnoreCase(layout)) el.layoutKind = HudElement.LayoutKind.ABSOLUTE;
        }

        Object posAbs = HudValueParsers.member(cfg, "positionAbsolute");
        if (posAbs != null) {
            boolean abs = HudValueParsers.asBool(posAbs, true);
            if (abs) el.layoutKind = HudElement.LayoutKind.ABSOLUTE;
        }

        Object pos = HudValueParsers.member(cfg, "position");
        if (pos != null) {
            String s = HudValueParsers.asString(pos, "").trim().toLowerCase();
            if ("absolute".equals(s)) el.layoutKind = HudElement.LayoutKind.ABSOLUTE;
        }

        Object fill = HudValueParsers.member(cfg, "fillParent");
        if (fill != null) el.fillParent = HudValueParsers.asBool(fill, el.fillParent);

        Object vis = HudValueParsers.member(cfg, "visible");
        if (vis != null) el.visible = HudValueParsers.asBool(vis, el.visible);
        el.node.setCullHint(el.visible ? com.jme3.scene.Spatial.CullHint.Never : com.jme3.scene.Spatial.CullHint.Always);

        Object z = HudValueParsers.member(cfg, "z");
        if (z != null) el.z = (float) HudValueParsers.asNum(z, el.z);

        String dir = HudValueParsers.asString(HudValueParsers.member(cfg, "flexDirection"), null);
        if (dir != null) {
            el.flexDirection = "column".equalsIgnoreCase(dir)
                    ? HudElement.FlexDirection.COLUMN
                    : HudElement.FlexDirection.ROW;
        }

        Object cs = HudValueParsers.member(cfg, "contentSized");
        if (cs != null) el.contentSized = HudValueParsers.asBool(cs, el.contentSized);
    }

    // ------------------- specific props -------------------

    private void applyRect(HudRect r, Object cfg) {
        Object col = HudValueParsers.member(cfg, "color");
        if (col != null) r.setColor(HudValueParsers.asColor(col, new ColorRGBA(0, 0, 0, 0.4f)), engine);

        Object size = HudValueParsers.member(cfg, "size");
        if (size != null) {
            float w = (float) HudValueParsers.asNum(HudValueParsers.member(size, "w"), r.w);
            float h = (float) HudValueParsers.asNum(HudValueParsers.member(size, "h"), r.h);
            r.setSize(w, h, engine);
        }
    }

    private void applyImage(HudImage im, Object cfg) {
        Object img = HudValueParsers.member(cfg, "image");
        if (img == null) img = HudValueParsers.member(cfg, "src");
        if (img != null) im.setImage(HudValueParsers.asString(img, null), engine);

        Object tint = HudValueParsers.member(cfg, "color");
        if (tint != null) im.setColor(HudValueParsers.asColor(tint, ColorRGBA.White), engine);

        Object size = HudValueParsers.member(cfg, "size");
        if (size != null) {
            float w = (float) HudValueParsers.asNum(HudValueParsers.member(size, "w"), im.w);
            float h = (float) HudValueParsers.asNum(HudValueParsers.member(size, "h"), im.h);
            im.setSize(w, h, engine);
        }
    }

    private void applyText(HudText t, Object cfg) {
        Object text = HudValueParsers.member(cfg, "text");
        if (text != null) t.setText(HudValueParsers.asString(text, ""), engine);

        Object font = HudValueParsers.member(cfg, "font");
        if (font != null) t.setFontTtf(HudValueParsers.asString(font, null), engine);

        Object fs = HudValueParsers.member(cfg, "fontSize");
        if (fs != null) t.setFontPx((float) HudValueParsers.asNum(fs, t.rawFontPx), engine);

        Object col = HudValueParsers.member(cfg, "color");
        if (col != null) t.setColor(HudValueParsers.asColor(col, ColorRGBA.White), engine);

        Object softness = HudValueParsers.member(cfg, "softness");
        if (softness != null) t.setSoftness((float) HudValueParsers.asNum(softness, t.softness));

        Object outline = HudValueParsers.member(cfg, "outline");
        if (outline != null) {
            float os = (float) HudValueParsers.asNum(HudValueParsers.member(outline, "size"), 0.0);
            ColorRGBA oc = HudValueParsers.asColor(HudValueParsers.member(outline, "color"), new ColorRGBA(0, 0, 0, 0));
            t.setOutline(os, oc);
        }

        Object shadow = HudValueParsers.member(cfg, "shadow");
        if (shadow != null) {
            float x = (float) HudValueParsers.asNum(HudValueParsers.member(shadow, "x"), 0);
            float y = (float) HudValueParsers.asNum(HudValueParsers.member(shadow, "y"), 0);
            float a = (float) HudValueParsers.asNum(HudValueParsers.member(shadow, "a"), 0);
            t.setShadow(x, y, a);
        }
    }

    private void applyHtml(HudHtml h, Object cfg) {
        Object html = HudValueParsers.member(cfg, "markup");
        if (html == null) html = HudValueParsers.member(cfg, "html");
        if (html != null) {
            h.setHtml(HudValueParsers.asString(html, ""), this, engine);
            if (DEBUG) log.debug("[hud] html set queued for id={}", h.id);
        }

        // optional data binding object (only if your HudHtml supports it)
        Object data = HudValueParsers.member(cfg, "data");
        if (data != null) {
            try {
                h.setData(data, this, engine);
            } catch (Throwable ignored) {
                if (DEBUG) log.debug("[hud] html data ignored: HudHtml.setData not available");
            }
        }
    }

    // ------------------- layout pipeline -------------------

    private void relayoutAll() {
        if (hudVP == null) return;

        final int w = hudVP.getCamera().getWidth();
        final int h = hudVP.getCamera().getHeight();

        tmpRoots.clear();
        for (HudElement e : byId.values()) {
            if (e != null && e.parent == null) tmpRoots.add(e);
        }
        if (tmpRoots.isEmpty()) return;

        boolean htmlChanged = false;
        for (HudElement r : tmpRoots) {
            if (r instanceof HudHtml hh) {
                if (hh.flushIfDirty(this, engine)) htmlChanged = true;
            }
        }
        if (htmlChanged) {
            tmpRoots.clear();
            for (HudElement e : byId.values()) {
                if (e != null && e.parent == null) tmpRoots.add(e);
            }
        }

        yoga.layout(tmpRoots, w, h);
        flex.layout(tmpRoots, w, h);

        for (HudElement r : tmpRoots) {
            HudLayout.apply(r, w, h);
        }

        // debug dump roots
        if (DEBUG) {
            int n = Math.min(5, tmpRoots.size());
            for (int i = 0; i < n; i++) {
                HudElement r = tmpRoots.get(i);
                var t = r.node.getLocalTranslation();
                var sc = r.node.getLocalScale();
                log.info("[hud] root#{} id={} kind={} pos=({}, {}, {}) size=({}, {}) vis={} anchor={} pivot={} scale=({}, {}, {})",
                        i, r.id, r.kind,
                        t.x, t.y, t.z,
                        r.w, r.h,
                        r.visible,
                        r.anchor, r.pivot,
                        sc.x, sc.y, sc.z);
            }
        }
    }

    // ------------------- viewport -------------------


    private void ensureHudViewportForCurrentApp(String reason) {
        int w = lastW;
        int h = lastH;
        if (w <= 0 || h <= 0) {
            Camera cam = app.getCamera();
            if (cam != null) {
                w = cam.getWidth();
                h = cam.getHeight();
            }
        }
        if (w <= 0) w = 1;
        if (h <= 0) h = 1;
        ensureHudViewport(w, h, reason);
    }

    private void ensureHudViewport(int w, int h, String reason) {
        w = Math.max(1, w);
        h = Math.max(1, h);

        lastW = w;
        lastH = h;

        uiScale = computeUiScale(w, h);
        viewportDto = new HudViewport(w, h, w / 2, h / 2, uiScale);

        if (hudCam == null) hudCam = new Camera(w, h);
        else hudCam.resize(w, h, true);

        // ✅ Y-DOWN ortho: (0,0) top-left, y grows down
        hudCam.setParallelProjection(true);
        hudCam.setFrustum(-1000f, 1000f, 0f, w, 0f, h);
        hudCam.setLocation(new Vector3f(0f, 0f, 0f));
        hudCam.lookAtDirection(new Vector3f(0f, 0f, -1f), Vector3f.UNIT_Y);

        // ✅ Scale virtual 1920x1080 authored UI into actual viewport
        float sx = w / DESIGN_W;
        float sy = h / DESIGN_H;
        float rootScale = Math.min(sx, sy);
        hudRoot.setLocalScale(rootScale, rootScale, 1f);

        if (hudVP == null) {
            hudVP = app.getRenderManager().createPostView("kalitech:hudVP", hudCam);
            hudVP.attachScene(hudRoot);
            hudVP.setClearFlags(false, true, false);
            hudVP.setEnabled(true);

            hudRoot.setQueueBucket(RenderQueue.Bucket.Gui);
            hudRoot.setCullHint(com.jme3.scene.Spatial.CullHint.Never);

            if (DEBUG) log.info("[hud] created POST viewport {}x{} scale={} rootScale={} reason={}", w, h, uiScale, rootScale, reason);
        } else {
            if (DEBUG) log.info("[hud] resized HUD viewport {}x{} scale={} rootScale={} reason={}", w, h, uiScale, rootScale, reason);
        }

        if (DEBUG) {
            log.debug("[hud] cam frustum nearFar=[{},{}] lrtb=[{}, {}, {}, {}]",
                    hudCam.getFrustumNear(), hudCam.getFrustumFar(),
                    hudCam.getFrustumLeft(), hudCam.getFrustumRight(),
                    hudCam.getFrustumTop(), hudCam.getFrustumBottom());
            log.info("[hud] design={}x{} viewport={}x{} rootScale={}", DESIGN_W, DESIGN_H, w, h, rootScale);
        }
    }


    private float computeUiScale(int w, int h) {
        float s = h / 1080f;
        if (s < 0.75f) s = 0.75f;
        if (s > 2.00f) s = 2.00f;
        return s;
    }

    private int countRoots() {
        int roots = 0;
        for (HudElement e : byId.values()) {
            if (e != null && e.parent == null) roots++;
        }
        return roots;
    }

    private void spawnSmokeTestRect() {
        try {
            int id = ids.getAndIncrement();
            HudRect r = new HudRect(id);
            r.w = 240;
            r.h = 90;
            r.offsetX = 30;
            r.offsetY = 30;
            r.visible = true;
            r.setColor(new ColorRGBA(1f, 0f, 0f, 0.85f), engine);
            hudRoot.attachChild(r.node);
            byId.put(id, r);
            relayoutDirty = true;

            log.warn("[hud] SMOKETEST: spawned visible red rect id={} at (30,30) size=240x90", id);
        } catch (Throwable t) {
            log.error("[hud] SMOKETEST failed", t);
        }
    }

    // ------------------- INTERNAL: used by HudHtml -------------------

    HudElement __createInternal(String kind, HudElement parent, Object cfg) {
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("__createInternal: kind is required");

        final int id = ids.getAndIncrement();
        final HudElement el = createElementOfKind(kind, id);

        if (parent != null) parent.addChild(el);
        else hudRoot.attachChild(el.node);

        byId.put(id, el);

        if (cfg != null) {
            applyCommon(el, cfg);
            switch (el.kind) {
                case RECT -> applyRect((HudRect) el, cfg);
                case IMAGE -> applyImage((HudImage) el, cfg);
                case TEXT -> applyText((HudText) el, cfg);
                case HTML -> applyHtml((HudHtml) el, cfg);
                default -> { /* group */ }
            }
        }

        relayoutDirty = true;
        return el;
    }

    void __destroyRecursiveInternal(HudElement el) {
        if (el == null) return;

        if (!el.children.isEmpty()) {
            List<HudElement> kids = new ArrayList<>(el.children);
            for (HudElement c : kids) __destroyRecursiveInternal(c);
            el.children.clear();
        }

        if (el.parent != null) {
            el.parent.removeChild(el);
        } else {
            el.node.removeFromParent();
        }
        el.parent = null;

        byId.remove(el.id);
        handles.remove(el.id);

        try { el.onDestroy(); } catch (Throwable ignored) {}
    }

    private HudElement createElementOfKind(String kind, int id) {
        return switch (kind) {
            case "group" -> new HudGroup(id);
            case "rect"  -> new HudRect(id);
            case "image" -> new HudImage(id);
            case "text"  -> new HudText(id);
            case "html"  -> new HudHtml(id);
            default -> throw new IllegalArgumentException("hud: unknown kind='" + kind + "'");
        };
    }
}