// FILE: Scripts/player/PlayerUI.js
// Author: Calista Verner
"use strict";

const CrosshairWidget = require("./CrosshairWidget.js");

class PlayerUI {
    constructor(playerOrCtx, cfgMaybe) {
        // OOP mode: new PlayerUI(player)
        // Legacy mode: new PlayerUI(ctx, cfg)
        const isPlayer = !!(playerOrCtx && typeof playerOrCtx === "object" && (playerOrCtx.cfg || playerOrCtx.getCfg || playerOrCtx.ctx));
        this.player = isPlayer ? playerOrCtx : null;

        this.ctx = isPlayer ? (this.player && this.player.ctx) : playerOrCtx;
        this.cfg = isPlayer ? ((this.player && this.player.cfg && this.player.cfg.ui) || {}) : (cfgMaybe || {});

        this.crosshair = null;
    }

    create() {
        const style = this.cfg.crosshair || {
            size: 22,
            color: { r: 0.2, g: 1.0, b: 0.4, a: 1.0 }
        };

        this.crosshair = new CrosshairWidget(this.player || this.ctx).create(style);
        return this;
    }

    destroy() {
        if (!this.crosshair) return;
        try { this.crosshair.destroy(); }
        catch (e) {
            if (typeof LOG !== "undefined" && LOG && LOG.error) LOG.error("[ui] crosshair.destroy failed: " + (e && (e.stack || e.message) ? (e.stack || e.message) : e));
            else throw e;
        }
        this.crosshair = null;
    }
}

module.exports = PlayerUI;