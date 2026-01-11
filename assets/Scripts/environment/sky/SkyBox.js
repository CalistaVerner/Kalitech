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

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug(
                "[sky][skybox] applyCfg default='" + this.defaultAsset + "'" +
                (this.dayAsset ? (" day='" + this.dayAsset + "'") : "") +
                (this.sunsetAsset ? (" sunset='" + this.sunsetAsset + "'") : "") +
                (this.nightAsset ? (" night='" + this.nightAsset + "'") : "")
            );
        }
    }

    pickAsset(dayFactor) {
        const d = (typeof dayFactor === "number") ? dayFactor : 1.0;

        if (!this.dayAsset && !this.sunsetAsset && !this.nightAsset) return this.defaultAsset;

        if (d < 0.10) return this.nightAsset || this.defaultAsset;
        if (d < 0.35) return this.sunsetAsset || this.dayAsset || this.defaultAsset;
        return this.dayAsset || this.defaultAsset;
    }

    update(render, dayFactor) {
        if (!render || typeof render.skyboxCube !== "function") {
            throw new Error("[skybox] render.skyboxCube(asset) is required");
        }

        const asset = this.pickAsset(dayFactor);
        if (!asset) throw new Error("[skybox] asset is empty");

        if (asset === this.lastAsset) return;

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug(
                "[sky][skybox] switch asset='" + asset + "' dayFactor=" + Number(dayFactor).toFixed(4)
            );
        }

        render.skyboxCube(asset);
        this.lastAsset = asset;
    }
}

module.exports = SkyBox;