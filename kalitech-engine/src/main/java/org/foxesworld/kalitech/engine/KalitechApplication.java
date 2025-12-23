package org.foxesworld.kalitech.engine;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.core.KalitechPlatform;
import org.foxesworld.kalitech.core.KalitechVersion;

public class KalitechApplication extends SimpleApplication {

    private static final Logger log = LogManager.getLogger(KalitechApplication.class);

    @Override
    public void simpleInitApp() {
        log.info("{} {}", KalitechVersion.NAME, KalitechVersion.VERSION);
        log.info("Java: {}", KalitechPlatform.java());
        log.info("OS: {}", KalitechPlatform.os());

        assetManager.registerLocator("assets", com.jme3.asset.plugins.FileLocator.class);
        assetManager.registerLoader(org.foxesworld.kalitech.engine.script.asset.ScriptTextLoader.class, "js");

        var ecs = new org.foxesworld.kalitech.engine.ecs.EcsWorld();
        var bus = new org.foxesworld.kalitech.engine.script.events.ScriptEventBus();

        stateManager.attach(new org.foxesworld.kalitech.engine.app.RuntimeAppState(
                "Scripts/main.js",
                java.nio.file.Path.of("assets"),
                0.25f,
                ecs,
                bus
        ));
    }
}