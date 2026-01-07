"use strict";

function isObj(v) {
    return !!v && typeof v === "object" && !Array.isArray(v);
}

function num(v, fb) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

class PlayerUI {
    constructor(player) {
        if (!player) throw new Error("[PlayerUI] player is required");

        this.player = player;
        this.ctx = player.ctx || null;

        this.engine = null;
        this.HUD_NATIVE = null;
        this.HUD = null;

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

        this.style = this._styleFromCfg(c, "flat");

        this.layer = null;
        this.panel = null;
    }

    _bindRuntime() {
        const p = this.player;

        const E = p && p.engine;
        if (!E) throw new Error("[PlayerUI] player.engine is not ready (call player.init() before ui.create)");

        this.engine = E;
        this.HUD_NATIVE = p.HUD_NATIVE || null;

        const hudWrapper =
            (typeof HUD !== "undefined" && HUD) ? HUD :
                (p.HUD ? p.HUD : null);

        if (!hudWrapper) throw new Error("[PlayerUI] HUD wrapper (HUD.js) is missing");
        if (typeof hudWrapper.layer !== "function") throw new Error("[PlayerUI] HUD.layer(name) is required");

        this.HUD = hudWrapper;
    }

    create() {
        if (this.layer) return this;

        this._bindRuntime();

        if (this.HUD_NATIVE && typeof this.HUD_NATIVE.setCursorEnabled === "function") {
            try {
                this.HUD_NATIVE.setCursorEnabled(false, true);
            } catch (_) {
            }
        }

        this.layer = this.HUD.layer(this.layerName);

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
        if (!this.layer) return;

        const fps = this._fps();
        const pos = this._pose();
        const cam = this._camType();

        const fpsStr = "FPS: " + (fps > 0 ? (+fps).toFixed(1) : "--");
        const posStr = "POS: " + this._f2(pos.x) + " | " + this._f2(pos.y) + " | " + this._f2(pos.z);
        const camStr = "CAM: " + cam;

        if (typeof this.layer.setText === "function") {
            this.layer.setText("debug.fps", fpsStr);
            this.layer.setText("debug.pos", posStr);
            this.layer.setText("debug.cam", camStr);
        }
    }

    destroy() {
        if (!this.layer) return;
        try {
            if (typeof this.layer.destroy === "function") this.layer.destroy();
        } finally {
            this.layer = null;
            this.panel = null;
        }
    }

    _fps() {
        const E = this.engine || (this.player && this.player.engine);
        if (!E) return 0;

        try {
            if (typeof E.fps === "function") return +E.fps() || 0;
            if (typeof E.api === "function") {
                const api = E.api();
                if (api && typeof api.fps === "function") return +api.fps() || 0;
            }
        } catch (_) {
        }
        return 0;
    }

    _pose() {
        const p = this.player;
        const pose = p && p.dom && p.dom.pose;
        if (pose && typeof pose.x === "number") return pose;
        return { x: 0, y: 0, z: 0 };
    }

    _camType() {
        const v = this.player && this.player.dom && this.player.dom.view;
        return (v && v.type) ? String(v.type) : "--";
    }

    _f2(v) {
        const n = +v;
        return Number.isFinite(n) ? n.toFixed(2) : "0.00";
    }

    _styleFromCfg(c, theme) {
        if (isObj(c.style)) return c.style;

        const bg = (c.bgColor != null) ? String(c.bgColor) : null;
        const bgA = (c.bgAlpha != null) ? num(c.bgAlpha, null) : null;

        const br = (c.borderColor != null) ? String(c.borderColor) : null;
        const brA = (c.borderAlpha != null) ? num(c.borderAlpha, null) : null;
        const brS = (c.borderSize != null) ? num(c.borderSize, null) : null;
        const brR = (c.borderRadius != null) ? num(c.borderRadius, null) : null;

        if (theme === "flat") {
            return {
                bg: { color: bg || "#101318", alpha: (bgA != null ? bgA : 0.80) },
                border: { size: (brS != null ? brS : 0), color: br || "#000000", alpha: (brA != null ? brA : 0.0), radius: (brR != null ? brR : 0) }
            };
        }

        return {
            bg: { color: bg || "#0b0f14", alpha: (bgA != null ? bgA : 0.65) },
            border: { size: (brS != null ? brS : 1), color: br || "#8AA0B6", alpha: (brA != null ? brA : 0.45), radius: (brR != null ? brR : 8) }
        };
    }
}

module.exports = PlayerUI;