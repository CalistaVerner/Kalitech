// FILE: Scripts/player/PlayerUI.js
// Author: Calista Verner
"use strict";

const CrosshairWidget = require("./CrosshairWidget.js");

class PlayerUI {
    constructor(ctx, cfg) {
        this.ctx = ctx;
        this.cfg = cfg || {};
        this.crosshair = null;
    }

    create() {
        const style = this.cfg.crosshair || {
            size: 22,
            color: { r: 0.2, g: 1.0, b: 0.4, a: 1.0 }
        };

        this.crosshair = new CrosshairWidget(this.ctx).create(style);
        return this;
    }

    destroy() {
        if (this.crosshair) {
            try { this.crosshair.destroy(); } catch (_) {}
            this.crosshair = null;
        }
    }
}

module.exports = PlayerUI;