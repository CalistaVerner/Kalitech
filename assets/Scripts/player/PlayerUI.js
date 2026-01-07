"use strict";

function num(v, fb) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

function isObj(v) {
    return !!v && typeof v === "object" && !Array.isArray(v);
}

class PlayerUI {
    constructor(player) {
        if (!player) throw new Error("[PlayerUI] player is required");

        this.player = player;

        const c = (player.cfg && player.cfg.ui) ? player.cfg.ui : Object.create(null);

        this.layerName = String(c.layerName || "debug-ui");
        this.anchor = String(c.anchor || "tl");
        this.mx = num(c.marginLeft, 10);
        this.my = num(c.marginTop, 10);

        this.w = num(c.w, 280);
        this.padX = num(c.padX, 12);
        this.padY = num(c.padY, 8);

        this.fontTitle = num(c.fontTitle, 16);
        this.fontLine = num(c.fontLine, 14);
        this.gap = num(c.lineGap, 4);

        this.style = isObj(c.style) ? c.style : {
            bg: {color: "#0b0f14", alpha: 0.65},
            border: {size: 1, color: "#8AA0B6", alpha: 0.45, radius: 8}
        };

        this.hud = null;
        this.layer = null;
        this.panel = null;
    }

    create() {
        if (this.layer) return this;

        const hud = this.player.HUD;
        if (!hud || typeof hud.layer !== "function") {
            throw new Error("[PlayerUI] HUD builtin is required (HUD.layer)");
        }

        this.hud = hud;
        this.layer = hud.layer(this.layerName);

        this.panel = this.layer.panel({
            id: "debug.panel",
            w: this.w,
            h: 60,
            autoHeight: true,
            padX: this.padX,
            padY: this.padY,
            flow: { fontSize: this.fontLine, gap: this.gap },
            place: { anchor: this.anchor, x: this.mx, y: this.my },
            style: this.style
        });

        this.panel.stack("debug.title", "DEBUG", {fontSize: this.fontTitle, color: "#FFFFFF"});
        this.panel.stack("debug.fps", "FPS: --", {fontSize: this.fontLine, color: "#FFFFFF"});
        this.panel.stack("debug.pos", "POS: --", {fontSize: this.fontLine, color: "#FFFFFF"});
        this.panel.stack("debug.cam", "CAM: --", {fontSize: this.fontLine, color: "#FFFFFF"});

        if (typeof this.layer.relayout === "function") this.layer.relayout();
        this.refresh();
        return this;
    }

    refresh() {
        const layer = this.layer;
        if (!layer || typeof layer.setText !== "function") return;

        const dom = this.player.dom;
        const p = dom && dom.pose ? dom.pose : null;
        if (!p) return;

        const cam = (dom && dom.view && dom.view.type) ? dom.view.type : "--";

        const eng = this.player.engine;
        const fps = (eng && typeof eng.fps === "function") ? (+eng.fps() || 0) : 0;

        layer.setText("debug.fps", "FPS: " + (fps > 0 ? fps.toFixed(1) : "--"));
        layer.setText("debug.pos", "POS: " + p.x.toFixed(2) + " | " + p.y.toFixed(2) + " | " + p.z.toFixed(2));
        layer.setText("debug.cam", "CAM: " + cam);
    }

    destroy() {
        if (!this.layer) return;
        if (typeof this.layer.destroy === "function") this.layer.destroy();

        this.hud = null;
        this.layer = null;
        this.panel = null;
    }
}

module.exports = PlayerUI;