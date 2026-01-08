"use strict";

const PlayerUI = require("../PlayerUI.js");

class PlayerUIController {
    constructor() {
        this.ctx = null;
        this.entity = null;
        this.impl = null;
    }

    bind(ctx, entity) {
        this.ctx = ctx;
        this.entity = entity;
        return this;
    }

    onStart() {
        const pawn = this.entity;
        const d = pawn.d;

        if (d.hudNative && typeof d.hudNative.setCursorEnabled === "function") {
            d.hudNative.setCursorEnabled(false, true);
        }

        this.impl = new PlayerUI(pawn);
        this.impl.create();
    }

    onUpdate(dt) {
        this.impl.refresh();
        this.entity.endFrame();
    }

    onStop() {
        if (this.impl && typeof this.impl.destroy === "function") this.impl.destroy();
        this.impl = null;
    }
}

module.exports = {PlayerUIController};