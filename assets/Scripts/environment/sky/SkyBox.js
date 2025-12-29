"use strict";

class SkyBox {
    constructor() {
        this.defaultAsset = "Textures/Sky/skyBox.dds";
        this.dayAsset = null;
        this.sunsetAsset = null;
        this.nightAsset = null;
        this.lastAsset = "";
    }

    applyCfg(cfg) {
        if (cfg && cfg.skybox != null) this.defaultAsset = String(cfg.skybox);
        if (cfg && cfg.skyboxDay != null) this.dayAsset = String(cfg.skyboxDay);
        if (cfg && cfg.skyboxSunset != null) this.sunsetAsset = String(cfg.skyboxSunset);
        if (cfg && cfg.skyboxNight != null) this.nightAsset = String(cfg.skyboxNight);
    }

    pickAsset(sunEval) {
        const d = sunEval && typeof sunEval.dayFactor === "number" ? sunEval.dayFactor : 1.0;
        if (!this.dayAsset && !this.sunsetAsset && !this.nightAsset) return this.defaultAsset;

        if (d < 0.10) return this.nightAsset || this.defaultAsset;
        if (d < 0.35) return this.sunsetAsset || this.dayAsset || this.defaultAsset;
        return this.dayAsset || this.defaultAsset;
    }

    update(render, sunEval) {
        const asset = this.pickAsset(sunEval);
        if (!asset) return;
        if (asset === this.lastAsset) return;

        // 1) ensureScene (если есть)
        try {
            if (render && typeof render.ensureScene === "function") render.ensureScene();
        } catch (e) {
            (globalThis.LOG || console).warn("[skybox] ensureScene failed:", e && (e.stack || e.message || String(e)));
        }

        // 2) проверить метод
        if (!render || typeof render.skyboxCube !== "function") {
            (globalThis.LOG || console).warn("[skybox] render.skyboxCube() not found. render keys maybe host-object.");
            return;
        }

        // 3) применить и залогировать
        try {
            (globalThis.LOG || console).info("[skybox] applying:", asset);
            render.skyboxCube(asset);
            this.lastAsset = asset;
        } catch (e) {
            (globalThis.LOG || console).error("[skybox] skyboxCube failed asset=" + asset + " err=" + (e && (e.stack || e.message || String(e))));
            // не обновляем lastAsset, чтобы пробовало снова после фикса
        }
    }
}

module.exports = SkyBox;
