/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  org.foxesworld.kalitech.engine.api.EngineApi
 */
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import org.foxesworld.kalitech.engine.api.EngineApi;

public final class RenderThread {
    private final EngineApi engine;
    private final SimpleApplication app;

    public RenderThread(EngineApi engine, SimpleApplication app) {
        if (engine == null) {
            throw new IllegalArgumentException("engine is null");
        }
        if (app == null) {
            throw new IllegalArgumentException("app is null");
        }
        this.engine = engine;
        this.app = app;
    }

    public void onJme(Runnable r) {
        if (this.engine.isJmeThread()) {
            r.run();
        } else {
            this.app.enqueue(() -> {
                r.run();
                return null;
            });
        }
    }
}

