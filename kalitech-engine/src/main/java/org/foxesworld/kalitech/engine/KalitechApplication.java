package org.foxesworld.kalitech.engine;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.core.KalitechPlatform;
import org.foxesworld.kalitech.core.KalitechVersion;
import org.foxesworld.kalitech.script.GraalScriptService;

import java.util.Map;

public class KalitechApplication extends SimpleApplication {

    private static final Logger log = LogManager.getLogger(KalitechApplication.class);

    @Override
    public void simpleInitApp() {
        log.info("{} {}", KalitechVersion.NAME, KalitechVersion.VERSION);
        log.info("Java: {}", KalitechPlatform.java());
        log.info("OS: {}", KalitechPlatform.os());

        // Скрипты по дефолту (JS)
        var scripts = new GraalScriptService();
        Object out = scripts.eval("js", "var x = 2 + 3; x;", Map.of());
        log.info("Script bootstrap result: {}", out);

        // TODO: подключим сервис-локатор/DI и загрузку сцен
    }
}