package org.foxesworld.kalitech.engine;

import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.material.plugins.J3MLoader;
import com.jme3.shader.plugins.GLSLLoader;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.style.BaseStyles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.core.KalitechPlatform;
import org.foxesworld.kalitech.core.KalitechVersion;
import org.foxesworld.kalitech.engine.asset.InputTextLoader;
import org.foxesworld.kalitech.engine.asset.JsTextLoader;

import java.nio.file.Path;

public class KalitechApplication extends SimpleApplication {

    private static final Logger log = LogManager.getLogger(KalitechApplication.class);
    private String version, os, java, assetsDir;

    @Override
    public void simpleInitApp() {
        log.info("{} {}", KalitechVersion.NAME, KalitechVersion.VERSION);
        log.info("Java: {}", KalitechPlatform.java());
        log.info("OS: {}", KalitechPlatform.os());
        this.version = KalitechVersion.VERSION;
        this.os = KalitechPlatform.os();
        this.java = KalitechPlatform.java();
        this.assetsDir = KalitechVersion.ASSETSDIR;

        assetManager.registerLocator(assetsDir, com.jme3.asset.plugins.FileLocator.class);
        assetManager.registerLoader(InputTextLoader.class, "json", "html", "css");
        assetManager.registerLoader(JsTextLoader.class, "js", "mjs");
        GuiGlobals.initialize(this);
        BaseStyles.loadGlassStyle();
        GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");

        Container c = new Container();
        c.addChild(new Label("LEMUR OK"));
        guiNode.attachChild(c);


        var ecs = new org.foxesworld.kalitech.engine.ecs.EcsWorld();
        var bus = new org.foxesworld.kalitech.engine.script.events.ScriptEventBus();

        stateManager.detach(stateManager.getState(StatsAppState.class));
        stateManager.detach(stateManager.getState(DebugKeysAppState.class));
        stateManager.detach(stateManager.getState(FlyCamAppState.class));
        stateManager.attach(new org.foxesworld.kalitech.engine.app.RuntimeAppState(
                "Scripts/main.js",
                Path.of(assetsDir),
                0.25f,
                ecs,
                bus
        ));
        flyCam.setEnabled(false);
    }

    public String getVersion() {
        return version;
    }
    public String getOs() {
        return os;
    }
    public String getJava() {
        return java;
    }

    @Override
    public void handleError(String errMsg, Throwable t) {
        t.printStackTrace();
        stop();
    }
}