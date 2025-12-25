// FILE: Scripts/systems/player.system.js
// Author: Calista Verner
"use strict";

const Player = require("../player/player.js");

class PlayerSystem {
    constructor() { this.p = null; }

    init(ctx) {
        console.log("PLAYER INIT")
        this.p = new Player(ctx);
        this.p.init();
    }

    update(ctx, tpf) {
        if (this.p) this.p.update(tpf);
    }

    destroy(ctx) {
        if (this.p) {
            this.p.destroy();
            this.p = null;
        }
    }
}

module.exports = new PlayerSystem();
