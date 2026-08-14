/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  com.jme3.renderer.ViewPort
 *  com.jme3.scene.Node
 *  com.jme3.scene.Spatial
 *  org.apache.logging.log4j.Logger
 */
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.Logger;

public final class ViewportContract {
    private final SimpleApplication app;
    private final Logger log;

    public ViewportContract(SimpleApplication app, Logger log) {
        this.app = app;
        this.log = log;
    }

    public void ensure(String where) {
        ViewPort main = this.app.getViewPort();
        ViewPort gui = this.app.getGuiViewPort();
        if (main == null || gui == null) {
            return;
        }
        Node root = this.app.getRootNode();
        Node guiNode = this.app.getGuiNode();
        if (!main.getScenes().contains((Object)root)) {
            main.attachScene((Spatial)root);
            this.log.info("RenderApi: {} attach rootNode to MAIN", (Object)where);
        }
        if (main.getScenes().contains((Object)guiNode)) {
            main.detachScene((Spatial)guiNode);
            this.log.warn("RenderApi: {} detached guiNode from MAIN (fix)", (Object)where);
        }
        if (!gui.getScenes().contains((Object)guiNode)) {
            gui.attachScene((Spatial)guiNode);
            this.log.info("RenderApi: {} attach guiNode to GUI", (Object)where);
        }
        if (gui.getScenes().contains((Object)root)) {
            gui.detachScene((Spatial)root);
            this.log.warn("RenderApi: {} detached rootNode from GUI (fix)", (Object)where);
        }
    }
}

