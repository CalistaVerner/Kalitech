// FILE: org/foxesworld/kalitech/engine/modules/render/ViewportContract.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import org.apache.logging.log4j.Logger;

public final class ViewportContract {

    private final SimpleApplication app;
    private final Logger log;

    public ViewportContract(SimpleApplication app, Logger log) {
        this.app = app;
        this.log = log;
    }

    public ViewPort main() {
        return app.getViewPort();
    }

    public ViewPort gui() {
        return app.getGuiViewPort();
    }

    public void ensure(String where) {
        ViewPort main = app.getViewPort();
        ViewPort gui = app.getGuiViewPort();
        if (main == null || gui == null) return;

        Node root = app.getRootNode();
        Node guiNode = app.getGuiNode();

        if (!main.getScenes().contains(root)) {
            main.attachScene(root);
            log.info("RenderApi: {} attach rootNode to MAIN", where);
        }
        if (main.getScenes().contains(guiNode)) {
            main.detachScene(guiNode);
            log.warn("RenderApi: {} detached guiNode from MAIN (fix)", where);
        }

        if (!gui.getScenes().contains(guiNode)) {
            gui.attachScene(guiNode);
            log.info("RenderApi: {} attach guiNode to GUI", where);
        }
        if (gui.getScenes().contains(root)) {
            gui.detachScene(root);
            log.warn("RenderApi: {} detached rootNode from GUI (fix)", where);
        }
    }
}