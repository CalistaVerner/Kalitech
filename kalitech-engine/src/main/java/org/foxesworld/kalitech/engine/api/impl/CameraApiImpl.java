package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.api.interfaces.CameraApi;

import java.util.Objects;

public final class CameraApiImpl implements CameraApi {

    private final SimpleApplication app;

    public CameraApiImpl(SimpleApplication app) {
        this.app = Objects.requireNonNull(app, "app");
    }

    @HostAccess.Export
    @Override
    public void setLocation(double x, double y, double z) {
        app.getCamera().setLocation(new Vector3f((float) x, (float) y, (float) z));
    }

    @HostAccess.Export
    @Override
    public void lookAt(double x, double y, double z, double upX, double upY, double upZ) {
        app.getCamera().lookAt(
                new Vector3f((float) x, (float) y, (float) z),
                new Vector3f((float) upX, (float) upY, (float) upZ)
        );
    }

    @HostAccess.Export
    @Override
    public void setFlyEnabled(boolean enabled) {
        if (app.getFlyByCamera() != null) {
            app.getFlyByCamera().setEnabled(enabled);
        }
    }

    @HostAccess.Export
    @Override
    public void setFlySpeed(double speed) {
        if (app.getFlyByCamera() != null) {
            app.getFlyByCamera().setMoveSpeed((float) speed);
        }
    }
}