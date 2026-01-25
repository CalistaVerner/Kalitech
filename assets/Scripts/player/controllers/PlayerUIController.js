"use strict";

const PlayerUI = require("../PlayerUI.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

class PlayerUIController {
    constructor() {
        this.ctx = null;
        this.entity = null;
        this.impl = null;
        this._started = false;
    }

    bind(ctx, entity) {
        this.ctx = req(ctx, "[PlayerUIController] ctx is required");
        this.entity = req(entity, "[PlayerUIController] entity is required");
        return this;
    }

    onStart() {
        if (this._started && this.impl) return;

        const pawn = req(this.entity, "[PlayerUIController] pawn is required");
        const d = req(pawn.d, "[PlayerUIController] pawn.d is required");
        req(d.hud, "[PlayerUIController] domains.hud is required for PlayerUI");

        if (d.hudNative && typeof d.hudNative.setCursorEnabled === "function") {
            d.hudNative.setCursorEnabled(false, true);
        }

        const ui = new PlayerUI(pawn).create();
        this.impl = ui;

        this._started = true;
    }

    onUpdate(dt) {
        // REDengine MAX stability: UI must exist. If not — re-init deterministically.
        if (!this.impl) this.onStart();
        if (!this.impl) throw new Error("[PlayerUIController] UI impl is null after onStart()");

        this.impl.refresh();
        this.entity.endFrame();
    }

    onStop() {
        if (this.impl && typeof this.impl.destroy === "function") this.impl.destroy();
        this.impl = null;
        this._started = false;
    }
}

module.exports = {PlayerUIController};