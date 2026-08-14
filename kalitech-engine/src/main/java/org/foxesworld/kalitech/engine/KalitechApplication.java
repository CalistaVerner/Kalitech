package org.foxesworld.kalitech.engine;

import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.style.BaseStyles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.core.KalitechPlatform;
import org.foxesworld.kalitech.core.KalitechVersion;
import org.foxesworld.kalitech.engine.asset.InputTextLoader;
import org.foxesworld.kalitech.engine.asset.LuaTextLoader;
import org.foxesworld.kalitech.engine.project.ProjectDescriptor;

import java.util.Objects;

public class KalitechApplication extends SimpleApplication {

    private static final Logger log = LogManager.getLogger(KalitechApplication.class);
    private final ProjectDescriptor project;
    private String version, os, java;
    private final float smokeExitAfterSeconds = positiveFloatProperty("kalitech.smokeExitAfterSeconds");
    private float runtimeSeconds;

    public KalitechApplication(ProjectDescriptor project) {
        this.project = Objects.requireNonNull(project, "project");
    }

    @Override
    public void simpleInitApp() {
        log.info("{} {}", KalitechVersion.NAME, KalitechVersion.VERSION);
        log.info("Java: {}", KalitechPlatform.java());
        log.info("OS: {}", KalitechPlatform.os());
        this.version = KalitechVersion.VERSION;
        this.os = KalitechPlatform.os();
        this.java = KalitechPlatform.java();
        log.info("[Project] id={} namespace={} projectOwned={} descriptor={}",
                project.id(),
                project.scripts().namespace(),
                project.projectOwnedRoot(),
                project.descriptorFile());

        assetManager.registerLocator(
                project.projectOwnedRoot().toString(),
                com.jme3.asset.plugins.FileLocator.class
        );
        assetManager.registerLoader(InputTextLoader.class, "json", "html", "css");
        assetManager.registerLoader(LuaTextLoader.class, "lua");
        GuiGlobals.initialize(this);
        BaseStyles.loadGlassStyle();
        GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");


        var ecs = new org.foxesworld.kalitech.engine.ecs.EcsWorld();
        var bus = new org.foxesworld.kalitech.engine.script.events.ScriptEventBus();

        stateManager.detach(stateManager.getState(StatsAppState.class));
        stateManager.detach(stateManager.getState(DebugKeysAppState.class));
        stateManager.detach(stateManager.getState(FlyCamAppState.class));

        stateManager.attach(new org.foxesworld.kalitech.engine.app.RuntimeAppState(
                project.scripts(),
                project.projectOwnedRoot(),
                ecs,
                bus
        ));

    }

    @Override
    public void simpleUpdate(float tpf) {
        if (smokeExitAfterSeconds <= 0f) return;
        runtimeSeconds += Math.max(0f, tpf);
        if (runtimeSeconds >= smokeExitAfterSeconds) {
            log.info("[Smoke] runtime remained active for {} seconds; stopping test instance",
                    smokeExitAfterSeconds);
            stop();
        }
    }

    private static float positiveFloatProperty(String name) {
        try {
            return Math.max(0f, Float.parseFloat(System.getProperty(name, "0").trim()));
        } catch (RuntimeException ignored) {
            return 0f;
        }
    }

    public ProjectDescriptor getProject() {
        return project;
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
    public void handleError(String errMsg, Throwable failure) {
        log.error("[Engine] fatal application error: {}", errMsg, failure);
        stop();
        if (smokeExitAfterSeconds > 0f) {
            System.exit(2);
        }
    }
}