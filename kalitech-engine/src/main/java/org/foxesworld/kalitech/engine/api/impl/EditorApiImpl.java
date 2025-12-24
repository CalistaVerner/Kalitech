// FILE: org/foxesworld/kalitech/engine/api/impl/EditorApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.EditorApi;

import java.util.Objects;

public final class EditorApiImpl implements EditorApi {

    private static final Logger log = LogManager.getLogger(EditorApiImpl.class);

    private final SimpleApplication app;
    private volatile boolean enabled;

    public EditorApiImpl(SimpleApplication app) {
        this.app = Objects.requireNonNull(app, "app");
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;

        // Безопасно: все это вызывается в JME update-thread (через ваш runOnMainThread / или напрямую)
        setFlyCam(enabled);
        setStatsView(enabled);

        log.info("Editor mode {}", enabled ? "ENABLED" : "DISABLED");
    }

    @Override
    public void toggle() {
        setEnabled(!enabled);
    }

    @Override
    public void setFlyCam(boolean enabled) {
        if (app.getFlyByCamera() != null) {
            app.getFlyByCamera().setEnabled(enabled);
        }
    }

    @Override
    public void setStatsView(boolean enabled) {
        app.setDisplayFps(enabled);
        app.setDisplayStatView(enabled);
    }
}