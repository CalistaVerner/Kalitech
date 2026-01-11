// FILE: org/foxesworld/kalitech/engine/modules/render/RenderThread.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import org.foxesworld.kalitech.engine.api.EngineApi;

public final class RenderThread {

    private final EngineApi engine;
    private final SimpleApplication app;

    public RenderThread(EngineApi engine, SimpleApplication app) {
        if (engine == null) throw new IllegalArgumentException("engine is null");
        if (app == null) throw new IllegalArgumentException("app is null");
        this.engine = engine;
        this.app = app;
    }

    public void onJme(Runnable r) {
        if (engine.isJmeThread()) r.run();
        else app.enqueue(() -> {
            r.run();
            return null;
        });
    }
}