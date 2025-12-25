// FILE: Scripts/game/CrosshairWidget.js
"use strict";

class CrosshairWidget {
    constructor(ctx) {
        this.ctx = ctx;
        this.root = null;
        this.img = null;

        this.style = {
            size: 256,                         // размер изображения
            texture: "Textures/ui/crosshair.png",
            color: { r: 1, g: 1, b: 1, a: 1 }
        };
    }

    create(styleOverride) {
        if (styleOverride) Object.assign(this.style, styleOverride);

        const hud = engine.hud();

        // Корень в центре экрана
// root-группа (центр)
        this.root = hud.create({
            kind: "group",
            anchor: "center",
            pivot: "center",
            visible: true
        });

        // Сам прицел — ОДНО изображение
        this.img = hud.create({
            kind: "image",
            parent: this.root,

            anchor: "center",
            pivot: "center",

            image: this.style.texture,

            size: {
                w: this.style.size,
                h: this.style.size
            },

            color: this.style.color   // опционально (white = без искажения)
        });

        return this;
    }

    destroy() {
        if (!this.root) return;
        try { engine.hud().destroy(this.root); } catch (_) {}
        this.root = this.img = null;
    }
}

module.exports = CrosshairWidget;