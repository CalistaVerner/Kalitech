// FILE: Scripts/game/CrosshairWidget.js
"use strict";

class CrosshairWidget {
    constructor(ctx) {
        this.ctx = ctx;
        this.root = null;
        this.h = null;
        this.v = null;

        this.style = {
            size: 22,
            thickness: 2,
            color: { r: 1.0, g: 0.2, b: 0.2, a: 1.0 } // ярко-красный для теста
        };
    }

    create(styleOverride) {
        if (styleOverride) Object.assign(this.style, styleOverride);

        const hud = engine.hud();

        // root в центре экрана
        this.root = hud.create({
            kind: "group",
            anchor: "center",
            pivot: "center",
            visible: true
        });

        // ДЕТИ: anchor должен давать (0,0) у родителя.
        // В вашем HudLayout anchorY(topLeft)=vh => улетает вверх.
        // bottomLeft => (0,0) как надо.
        this.h = hud.create({
            kind: "rect",
            parent: this.root,
            anchor: "bottomLeft",
            pivot: "center",
            size: { w: this.style.size, h: this.style.thickness },
            color: this.style.color
        });

        this.v = hud.create({
            kind: "rect",
            parent: this.root,
            anchor: "bottomLeft",
            pivot: "center",
            size: { w: this.style.thickness, h: this.style.size },
            color: this.style.color
        });

        return this;
    }

    destroy() {
        if (!this.root) return;
        try { engine.hud().destroy(this.root); } catch (_) {}
        this.root = this.h = this.v = null;
    }
}

module.exports = CrosshairWidget;