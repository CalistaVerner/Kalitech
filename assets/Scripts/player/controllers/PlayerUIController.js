// FILE: Scripts/player/controllers/PlayerUIController.js
"use strict";

const {EntityController} = require("../../core/EntityController.js");
const PlayerUI = require("../PlayerUI.js");

class PlayerUIController extends EntityController {
    constructor() {
        super();
        this.impl = null;
    }

    onStart() {
        const d = this.entity.d;
        if (d.hudNative && typeof d.hudNative.setCursorEnabled === "function") {
            d.hudNative.setCursorEnabled(false, true);
        }

        this.impl = new PlayerUI(this.entity);
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