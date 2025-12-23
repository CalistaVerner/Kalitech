package org.foxesworld.kalitech.engine;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.core.KalitechPlatform;
import org.foxesworld.kalitech.core.KalitechVersion;
import org.foxesworld.kalitech.engine.script.ScriptAppState;

public class KalitechApplication extends SimpleApplication {

    private static final Logger log = LogManager.getLogger(KalitechApplication.class);

    @Override
    public void simpleInitApp() {
        log.info("{} {}", KalitechVersion.NAME, KalitechVersion.VERSION);
        log.info("Java: {}", KalitechPlatform.java());
        log.info("OS: {}", KalitechPlatform.os());

        // Подцепляем JS-логику как “игру”
        assetManager.registerLocator("assets", com.jme3.asset.plugins.FileLocator.class);
        assetManager.registerLoader(org.foxesworld.kalitech.engine.script.asset.ScriptTextLoader.class, "js");
        stateManager.attach(new ScriptAppState(assetManager, "Scripts/main.js"));


        // Камера чуть дальше, чтобы куб было видно
        cam.setLocation(cam.getLocation().add(0, 1.5f, 6f));
        flyCam.setMoveSpeed(10f);
    }
}