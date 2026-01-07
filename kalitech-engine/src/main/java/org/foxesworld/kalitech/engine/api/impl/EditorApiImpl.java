// FILE: org/foxesworld/kalitech/engine/api/impl/EditorApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.EditorApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;

public final class EditorApiImpl extends AbstractApiModule implements EditorApi {

    private static final Logger log = LogManager.getLogger(EditorApiImpl.class);

    private SimpleApplication app;
    private volatile boolean enabled;

    public EditorApiImpl() {
        super("editor", "Editor", "1.0.0");
    }

    public EditorApiImpl(EngineApiImpl engineApi) {
        this();
        bind(engineApi);
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        bind(ctx.engine);
    }

    private void bind(EngineApiImpl engineApi) {
        this.app = engineApi.getApp();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;

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
