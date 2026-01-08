"use strict";

const {PlayerController} = require("./PlayerController.js");

/**
 * Legacy adapter kept ONLY as an entrypoint compatibility layer.
 * Can be deleted later when you change Scripts/player/index.js to use PlayerController directly.
 */
class PlayerEntityController {
    constructor(ctx, cfg) {
        this.root = new PlayerController(ctx, cfg).init();
    }

    update(dt) {
        this.root.update(dt);
    }

    dispose() {
        this.root.dispose();
        this.root = null;
    }
}

module.exports = {PlayerEntityController};