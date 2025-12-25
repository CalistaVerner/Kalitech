// FILE: Scripts/game/Player.js
// Author: Calista Verner
"use strict";

const CrosshairWidget = require("./CrosshairWidget.js");

class Player {

    constructor(ctx) {
        this.ctx = ctx;

        // state keys
        this.KEY_PLAYER = "game:player";
        this.KEY_UI = "game:player:ui";

        // components
        this.crosshair = null;

        // gameplay state
        this.alive = true;
    }

    init() {
        const st = this.ctx.state();

        // Create UI components
        this.crosshair = new CrosshairWidget(this.ctx).create({
            size: 22,
            thickness: 2,
            color: { r: 0.2, g: 1.0, b: 0.4, a: 1.0 }
        });

        // store references for debugging / hot reload safety
        st.set(this.KEY_UI, {
            crosshair: true
        });

        st.set(this.KEY_PLAYER, {
            alive: true
        });

        engine.log().info("[player] init (crosshair created)");
    }

    update(tpf) {
        if (!this.alive) return;

        // Example: if you later implement shooting, you can animate crosshair:
        // this.crosshair.setSize(22 + recoil * 10);
        //
        // Or hide in editor mode etc:
        // this.crosshair.setVisible(!engine.editor().enabled());
    }

    destroy() {
        const st = this.ctx.state();

        if (this.crosshair) {
            this.crosshair.destroy();
            this.crosshair = null;
        }

        st.remove(this.KEY_UI);
        st.remove(this.KEY_PLAYER);

        engine.log().info("[player] destroy");
    }
}

module.exports = Player;