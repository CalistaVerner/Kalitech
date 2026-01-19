package org.foxesworld.kalitech.engine.modules.ui.chromium;

import com.jme3.app.Application;
import com.jme3.scene.Node;
import com.simsilica.lemur.GuiGlobals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

public final class ChromiumUiModule {
    private static final Logger log = LogManager.getLogger(ChromiumUiModule.class);

    private final Application app;
    private final Node guiNode;
    private final ChromiumService service;

    private ChromiumView view;
    private ChromiumTextureBridge bridge;
    private ChromiumLemurSurface surface;

    private int wantW, wantH;
    private String wantUrl;
    private boolean requested;

    public ChromiumUiModule(Application app, Node guiNode, ChromiumService service) {
        this.app = Objects.requireNonNull(app, "app");
        this.guiNode = Objects.requireNonNull(guiNode, "guiNode");
        this.service = Objects.requireNonNull(service, "service");
    }

    /**
     * Можно вызывать до ready — модуль сам дождётся, когда CEF поднимется.
     */
    public void requestInit(int w, int h, String url) {
        this.wantW = Math.max(1, w);
        this.wantH = Math.max(1, h);
        this.wantUrl = url;
        this.requested = true;

        service.startAsync().whenComplete((cef, err) -> {
            if (err != null) {
                log.error("[chromium] start failed", err);
                return;
            }
            log.info("[chromium] start ok");
        });
    }

    public void tick() {
        if (!requested) return;

        // 1) Если ещё не инициализировано — пробуем инициализировать (на render-thread JME)
        if (view == null) {
            if (!service.isReady()) return;

            if (GuiGlobals.getInstance() == null) {
                GuiGlobals.initialize(app);
            }

            view = new ChromiumView(service, wantW, wantH, wantUrl, true);
            bridge = new ChromiumTextureBridge(view);
            surface = new ChromiumLemurSurface(wantW, wantH, bridge.texture());
            guiNode.attachChild(surface.panel());

            log.info("[chromium] UI created {}x{} url={}", wantW, wantH, wantUrl);
        }

        // 2) Обновление текстуры (строго из update-loop JME)
        if (bridge != null) bridge.updateIfDirty();
    }

    public void resize(int w, int h) {
        wantW = Math.max(1, w);
        wantH = Math.max(1, h);
        if (view != null) view.resize(wantW, wantH);
        if (surface != null) surface.resize(wantW, wantH);
    }

    public ChromiumView view() { return view; }
}