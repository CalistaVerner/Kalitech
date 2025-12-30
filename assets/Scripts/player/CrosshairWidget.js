// FILE: Scripts/game/CrosshairWidget.js
"use strict";

class CrosshairWidget {
    constructor(playerOrCtx) {
        this.player = (playerOrCtx && typeof playerOrCtx === "object" && playerOrCtx.ctx) ? playerOrCtx : null;
        this.ctx = this.player ? this.player.ctx : playerOrCtx;

        this.root = null;
        this.img = null;

        this.style = {
            size: 256,
            texture: "Textures/ui/crosshair.png",
            color: { r: 1, g: 1, b: 1, a: 1 }
        };
    }

    create(styleOverride) {
        if (styleOverride) Object.assign(this.style, styleOverride);

        const hud = engine.hud();

        this.root = hud.create({
            kind: "group",
            anchor: "center",
            pivot: "center",
            visible: true
        });

        this.img = hud.create({
            kind: "image",
            parent: this.root,
            anchor: "center",
            pivot: "center",
            image: this.style.texture,
            size: { w: this.style.size, h: this.style.size },
            color: this.style.color
        });

        return this;
    }

    destroy() {
        if (!this.root) return;
        engine.hud().destroy(this.root);
        this.root = this.img = null;
    }
}

module.exports = CrosshairWidget;