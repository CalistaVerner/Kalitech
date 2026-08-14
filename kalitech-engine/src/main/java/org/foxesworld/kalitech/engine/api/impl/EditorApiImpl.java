package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import org.foxesworld.kalitech.engine.api.interfaces.EditorApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;

import java.util.Objects;

/**
 * Editor runtime toggles (flycam, stats).
 *
 * <p>Threading: all JME state changes are executed on the JME thread.
 */
public final class EditorApiImpl extends AbstractApiModule implements EditorApi {

    private SimpleApplication app;
    private volatile boolean enabled;

    public EditorApiImpl() {
        super("editor", "Editor", "1.0.0");
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.app = Objects.requireNonNull(ctx.app, "ctx.app");
    }

    @Override
    public void detach() {
        this.app = null;
        super.detach();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;

        onJmeVoid("editor.setEnabled", () -> {
            applyFlyCam(enabled);
            applyStatsView(enabled);
        });

        if (log != null) {
            log.info("[editor] {}", enabled ? "enabled" : "disabled");
        }
    }

    @Override
    public void toggle() {
        setEnabled(!enabled);
    }

    @Override
    public void setFlyCam(boolean enabled) {
        this.enabled = enabled;
        onJmeVoid("editor.setFlyCam", () -> applyFlyCam(enabled));
    }

    @Override
    public void setStatsView(boolean enabled) {
        this.enabled = enabled;
        onJmeVoid("editor.setStatsView", () -> applyStatsView(enabled));
    }

    private void applyFlyCam(boolean enabled) {
        SimpleApplication a = app;
        if (a == null) return;

        var fly = a.getFlyByCamera();
        if (fly != null) fly.setEnabled(enabled);
    }

    private void applyStatsView(boolean enabled) {
        SimpleApplication a = app;
        if (a == null) return;

        a.setDisplayFps(enabled);
        a.setDisplayStatView(enabled);
    }
}