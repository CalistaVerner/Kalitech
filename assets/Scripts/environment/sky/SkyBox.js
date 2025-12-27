// FILE: Scripts/systems/sky/SkyBox.js
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
        if (!cfg) return;
        if (cfg.skybox != null) this.defaultAsset = String(cfg.skybox);

        if (cfg.skyboxDay != null) this.dayAsset = String(cfg.skyboxDay);
        if (cfg.skyboxSunset != null) this.sunsetAsset = String(cfg.skyboxSunset);
        if (cfg.skyboxNight != null) this.nightAsset = String(cfg.skyboxNight);
    }

    pickAsset(sunEval) {
        const d = sunEval.dayFactor;
        if (!this.dayAsset && !this.sunsetAsset && !this.nightAsset) return this.defaultAsset;

        if (d < 0.10) return this.nightAsset || this.defaultAsset;
        if (d < 0.35) return this.sunsetAsset || this.dayAsset || this.defaultAsset;
        return this.dayAsset || this.defaultAsset;
    }

    update(render, sunEval) {
        const asset = this.pickAsset(sunEval);
        if (!asset) return;
        if (asset === this.lastAsset) return;

        try {
            render.skyboxCube(asset);
        } catch (_) {}
        this.lastAsset = asset;
    }
}

module.exports = SkyBox;