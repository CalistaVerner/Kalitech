// FILE: org/foxesworld/kalitech/engine/modules/render/ViewportContract.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * ViewportContract (CDPR-style)
 * <p>
 * Goals:
 * - No heuristics. No "pick best viewport".
 * - Explicit roles: SCENE, FINAL, GUI.
 * - One authoritative binding source for render pipeline modules (shadows/post/sky).
 * - Self-healing ensure(): keeps scenes attached as defined by contract.
 * - Change notifications for modules (e.g., ShadowModule) when bindings change.
 * <p>
 * Default bindings:
 * - SCENE  = app.getViewPort()
 * - FINAL  = app.getViewPort() (same as SCENE unless you build a compositor)
 * - GUI    = app.getGuiViewPort()
 * - sceneRoot = app.getRootNode()
 * - guiRoot   = app.getGuiNode()
 */
public final class ViewportContract {

    private ViewPort sceneVp;

    private final SimpleApplication app;
    private final Logger log;
    private ViewPort finalVp;
    private ViewPort guiVp;
    private Spatial sceneRoot;
    private Spatial guiRoot;
    private Runnable onSceneBindingChanged;
    private Runnable onFinalBindingChanged;
    private Runnable onGuiBindingChanged;
    public ViewportContract(SimpleApplication app, Logger log) {
        this.app = Objects.requireNonNull(app, "app");
        this.log = Objects.requireNonNull(log, "log");
    }

    private static String safeName(Object o) {
        if (o == null) return "null";
        if (o instanceof ViewPort vp) return vp.getName();
        if (o instanceof Spatial sp) return sp.getName();
        return o.getClass().getSimpleName();
    }

    // -------------------- bindings (getters) --------------------

    public ViewPort sceneViewPort() {
        if (sceneVp != null) return sceneVp;
        return app.getViewPort();
    }

    public ViewPort finalViewPort() {
        // by default FINAL == SCENE
        if (finalVp != null) return finalVp;
        return sceneViewPort();
    }

    public ViewPort guiViewPort() {
        if (guiVp != null) return guiVp;
        return app.getGuiViewPort();
    }

    public Spatial sceneRoot() {
        if (sceneRoot != null) return sceneRoot;
        return app.getRootNode();
    }

    public Spatial guiRoot() {
        if (guiRoot != null) return guiRoot;
        return app.getGuiNode();
    }

    // -------------------- bindings (setters) --------------------

    public void bindScene(ViewPort vp, Spatial root, String why) {
        if (vp == null) throw new IllegalArgumentException("scene ViewPort is null");
        if (root == null) throw new IllegalArgumentException("scene root is null");

        boolean changed = false;

        if (sceneVp != vp) {
            sceneVp = vp;
            changed = true;
        }
        if (sceneRoot != root) {
            sceneRoot = root;
            changed = true;
        }

        if (changed) {
            log.info("[vp] bind SCENE vp='{}' root='{}' why={}", safeName(vp), safeName(root), why);
            if (onSceneBindingChanged != null) onSceneBindingChanged.run();
        }
    }

    public void bindFinal(ViewPort vp, String why) {
        if (vp == null) throw new IllegalArgumentException("final ViewPort is null");

        if (finalVp != vp) {
            finalVp = vp;
            log.info("[vp] bind FINAL vp='{}' why={}", safeName(vp), why);
            if (onFinalBindingChanged != null) onFinalBindingChanged.run();
        }
    }

    public void bindGui(ViewPort vp, Spatial root, String why) {
        if (vp == null) throw new IllegalArgumentException("gui ViewPort is null");
        if (root == null) throw new IllegalArgumentException("gui root is null");

        boolean changed = false;

        if (guiVp != vp) {
            guiVp = vp;
            changed = true;
        }
        if (guiRoot != root) {
            guiRoot = root;
            changed = true;
        }

        if (changed) {
            log.info("[vp] bind GUI vp='{}' root='{}' why={}", safeName(vp), safeName(root), why);
            if (onGuiBindingChanged != null) onGuiBindingChanged.run();
        }
    }

    // convenience: revert to defaults
    public void bindDefaults(String why) {
        bindScene(app.getViewPort(), app.getRootNode(), why + ":defaults.scene");
        bindFinal(app.getViewPort(), why + ":defaults.final");
        bindGui(app.getGuiViewPort(), app.getGuiNode(), why + ":defaults.gui");
    }

    // -------------------- listeners --------------------

    public void onSceneBindingChanged(Runnable r) {
        this.onSceneBindingChanged = r;
    }

    public void onFinalBindingChanged(Runnable r) {
        this.onFinalBindingChanged = r;
    }

    public void onGuiBindingChanged(Runnable r) {
        this.onGuiBindingChanged = r;
    }

    // -------------------- ensure / validate --------------------

    /**
     * Ensure the contract is enforced:
     * - SCENE viewport renders sceneRoot (and not guiRoot)
     * - GUI viewport renders guiRoot (and not sceneRoot)
     * - FINAL viewport is allowed to render anything (post chain), but never should render guiRoot by accident unless you want that.
     * <p>
     * Safe to call often.
     */
    public void ensure(String where) {
        final ViewPort scene = sceneViewPort();
        final ViewPort gui = guiViewPort();
        final Spatial sRoot = sceneRoot();
        final Spatial gRoot = guiRoot();

        if (scene == null || gui == null || sRoot == null || gRoot == null) return;

        // SCENE must contain sceneRoot
        if (!scene.getScenes().contains(sRoot)) {
            scene.attachScene(sRoot);
            log.info("[vp] {} attach sceneRoot -> SCENE ({})", where, safeName(scene));
        }

        // SCENE must NOT contain guiRoot
        if (scene.getScenes().contains(gRoot)) {
            scene.detachScene(gRoot);
            log.warn("[vp] {} detached guiRoot from SCENE (fix)", where);
        }

        // GUI must contain guiRoot
        if (!gui.getScenes().contains(gRoot)) {
            gui.attachScene(gRoot);
            log.info("[vp] {} attach guiRoot -> GUI ({})", where, safeName(gui));
        }

        // GUI must NOT contain sceneRoot
        if (gui.getScenes().contains(sRoot)) {
            gui.detachScene(sRoot);
            log.warn("[vp] {} detached sceneRoot from GUI (fix)", where);
        }

        // Optional sanity: never attach GUI root to FINAL by accident
        ViewPort fin = finalViewPort();
        if (fin != null && fin != gui && fin.getScenes().contains(gRoot)) {
            // don't auto-fix FINAL because some pipelines intentionally composite GUI,
            // but we warn loudly.
            log.warn("[vp] {} FINAL contains guiRoot (pipeline?) finalVp={}", where, safeName(fin));
        }
    }

    public void dump(String tag) {
        ViewPort s = sceneViewPort();
        ViewPort f = finalViewPort();
        ViewPort g = guiViewPort();

        log.info("[vpDump] {} SCENE  vp='{}' scenes={} procs={}",
                tag, safeName(s),
                (s != null && s.getScenes() != null ? s.getScenes().size() : -1),
                (s != null && s.getProcessors() != null ? s.getProcessors().size() : -1));

        log.info("[vpDump] {} FINAL  vp='{}' scenes={} procs={}",
                tag, safeName(f),
                (f != null && f.getScenes() != null ? f.getScenes().size() : -1),
                (f != null && f.getProcessors() != null ? f.getProcessors().size() : -1));

        log.info("[vpDump] {} GUI    vp='{}' scenes={} procs={}",
                tag, safeName(g),
                (g != null && g.getScenes() != null ? g.getScenes().size() : -1),
                (g != null && g.getProcessors() != null ? g.getProcessors().size() : -1));
    }

    public enum Role {SCENE, FINAL, GUI}
}