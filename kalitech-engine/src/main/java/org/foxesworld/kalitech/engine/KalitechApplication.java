package org.foxesworld.kalitech.engine;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.core.ICOParser;
import org.foxesworld.kalitech.core.KalitechPlatform;
import org.foxesworld.kalitech.core.KalitechVersion;

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
        assetManager.registerLoader(org.foxesworld.kalitech.engine.script.asset.ScriptTextLoader.class, "js");
        assetManager.registerLoader(org.foxesworld.kalitech.engine.script.asset.ScriptTextLoader.class, "json");

        var ecs = new org.foxesworld.kalitech.engine.ecs.EcsWorld();
        var bus = new org.foxesworld.kalitech.engine.script.events.ScriptEventBus();

        stateManager.attach(new org.foxesworld.kalitech.engine.app.RuntimeAppState(
                "Scripts/main.js",
                Path.of(assetsDir),
                0.25f,
                ecs,
                bus
        ));
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