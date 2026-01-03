// FILE: Scripts/player/PlayerUI.js
// Author: Calista Verner
"use strict";

/**
 * PlayerUI (DEV HUD)
 * FPS / POS / CAM
 *
 * Compatible with Hud.js v2.2.4+ and v2.3.x
 * - v2.2.4: layer.rect(), layer.stackText(panel,...)
 * - v2.3.x: layer.panel(), panel.stack(...)
 *
 * COORD MODE:
 *  - default: "topLeft" (x,y from top-left, y goes down)  ✅ your current runtime
 *  - optional: "bottomLeft" (legacy convert using viewport height)
 */
class PlayerUI {
    constructor(playerOrCtx, cfgMaybe) {
        const isPlayer = !!(playerOrCtx && typeof playerOrCtx === "object" && (playerOrCtx.ctx || playerOrCtx.cfg || playerOrCtx.getCfg));

        this.player = isPlayer ? playerOrCtx : null;
        this.ctx = isPlayer ? this.player.ctx : playerOrCtx;
        this.cfg = isPlayer ? (((this.player && this.player.cfg && this.player.cfg.ui) || {})) : (cfgMaybe || {});

        this.engine = (this.ctx && this.ctx.engine) ? this.ctx.engine : (typeof engine !== "undefined" ? engine : null);
        if (!this.engine) throw new Error("[PlayerUI] engine is not available");

        // HUD must NOT grab mouse
        const hudNative = this.engine.hud && this.engine.hud();
        if (hudNative && typeof hudNative.setCursorEnabled === "function") hudNative.setCursorEnabled(false, true);

        this.HUD = (typeof HUD !== "undefined" && HUD) ? HUD : require("kalitech/builtin/Hud.js")(this.engine);

        const c = this.cfg || {};
        this.layout = {
            layerName: String(c.layerName || "debug-ui"),

            anchor: String(c.anchor || "tl"),
            mx: (c.marginLeft != null) ? +c.marginLeft : 10,
            my: (c.marginTop  != null) ? +c.marginTop  : 10,

            w: (c.w != null) ? +c.w : 360,
            h0: (c.h0 != null) ? +c.h0 : 90,

            pad: (c.pad != null) ? +c.pad : 10,
            padX: (c.padX != null) ? +c.padX : null,
            padY: (c.padY != null) ? +c.padY : null,

            fontTitle: (c.fontTitle != null) ? +c.fontTitle : 18,
            fontLine:  (c.fontLine  != null) ? +c.fontLine  : 16,
            gap:       (c.lineGap   != null) ? +c.lineGap   : 6,

            fpsWindow: (c.fpsWindow != null) ? +c.fpsWindow : 0.25,

            // ✅ IMPORTANT: your current setup behaves like top-left/y-down
            coord: String(c.coord || "topLeft") // "topLeft" | "bottomLeft"
        };

        this.layer = null;
        this.panel = null;
        this.lines = { title: null, fps: null, pos: null, cam: null };

        this._last = { fps: "", pos: "", cam: "" };
        this._fps = 0;
        this._fpsAcc = 0;
        this._fpsFrames = 0;
    }

    _vp() {
        const HUD = this.HUD;
        if (HUD && typeof HUD.viewport === "function") return HUD.viewport();
        return { w: 0, h: 0 };
    }

    _makePanel(layer) {
        const L = this.layout;

        const mk = (layer && typeof layer.panel === "function") ? layer.panel.bind(layer) :
            (layer && typeof layer.rect === "function") ? layer.rect.bind(layer) :
                null;
        if (!mk) throw new Error("[PlayerUI] HUD layer has no panel/rect()");

        const cfg = {
            w: L.w,
            h: L.h0,
            visible: true,
            autoHeight: true,

            lineGap: L.gap,                 // v2.2.4
            flow: { fontSize: L.fontLine, gap: L.gap } // v2.3.x
        };

        // padding
        if (L.padX != null || L.padY != null) {
            cfg.padX = (L.padX != null) ? +L.padX : L.pad;
            cfg.padY = (L.padY != null) ? +L.padY : L.pad;
            cfg.pad = L.pad; // harmless for v2.3
        } else {
            cfg.pad = L.pad;  // v2.3
            cfg.padX = L.pad; // v2.2.4
            cfg.padY = L.pad; // v2.2.4
        }

        // v2.3.x uses place
        if (typeof layer.panel === "function") {
            cfg.place = { anchor: L.anchor, x: L.mx, y: L.my };
            return mk(cfg);
        }

        // v2.2.4 uses x/y. ✅ default is topLeft no conversion
        if (L.coord === "bottomLeft") {
            const vp = this._vp();
            cfg.x = L.mx;
            cfg.y = (vp.h > 0) ? (vp.h - L.my - L.h0) : L.my;
        } else {
            cfg.x = L.mx;
            cfg.y = L.my;
        }

        return mk(cfg);
    }

    _stack(panel, text, fontSize) {
        const layer = this.layer;
        const fs = (fontSize != null) ? +fontSize : this.layout.fontLine;

        if (panel && typeof panel.stack === "function") {
            // v2.3.x: panel.stack(text, cfg)
            return panel.stack(text, { fontSize: fs });
        }
        if (layer && typeof layer.stackText === "function") {
            // v2.2.4: layer.stackText(panel, cfg)
            return layer.stackText(panel, { text: text, fontSize: fs });
        }
        throw new Error("[PlayerUI] HUD has no stack method");
    }

    _dt() {
        const ctx = this.ctx;
        const n = +(
            (ctx && typeof ctx.dt === "number") ? ctx.dt :
                (ctx && ctx.frame && typeof ctx.frame.dt === "number") ? ctx.frame.dt :
                    (ctx && ctx.frame && typeof ctx.frame.delta === "number") ? ctx.frame.delta :
                        0
        );
        return (n > 0 && Number.isFinite(n)) ? n : 0;
    }

    _fpsUpdate(dt) {
        const fps = (this.engine && typeof this.engine.fps === "function") ? this.engine.fps() : 0;
        return fps;
    }

    _pose() {
        const p = this.player;
        const pose = p.dom.pose;
        if (pose && typeof pose.x === "number") return pose;

        const st = p && p.state && p.state.pos;
        if (st && typeof st.x === "number") return st;

        return { x: 0, y: 0, z: 0 };
    }

    _camType() {
        const v = this.player.dom.view;
        return v.type;
    }

    _fmt2(v) {
        const n = +v;
        return Number.isFinite(n) ? n.toFixed(2) : "0.00";
    }

    create() {
        if (this.layer) return this;

        const L = this.layout;

        this.layer = this.HUD.layer(L.layerName);
        this.panel = this._makePanel(this.layer);

        // ✅ consistent order
        this.lines.title = this._stack(this.panel, "DEBUG", L.fontTitle);
        this.lines.fps   = this._stack(this.panel, "FPS: --", L.fontLine);
        this.lines.pos   = this._stack(this.panel, "POS: --", L.fontLine);
        this.lines.cam   = this._stack(this.panel, "CAM: --", L.fontLine);

        this.refresh(true);
        return this;
    }

    refresh(force = false) {
        if (!this.panel) return;

        const fps = this._fpsUpdate(this._dt());
        const pos = this._pose();
        const cam = this._camType();

        const fpsStr = "FPS: " + (fps > 0 ? fps.toFixed(1) : "--");
        const posStr = "POS: " + this._fmt2(pos.x) + " " + this._fmt2(pos.y) + " " + this._fmt2(pos.z);
        const camStr = "CAM: " + cam;

        if (force || this._last.fps !== fpsStr) { if (this.lines.fps) this.lines.fps.text(fpsStr); this._last.fps = fpsStr; }
        if (force || this._last.pos !== posStr) { if (this.lines.pos) this.lines.pos.text(posStr); this._last.pos = posStr; }
        if (force || this._last.cam !== camStr) { if (this.lines.cam) this.lines.cam.text(camStr); this._last.cam = camStr; }
    }

    destroy() {
        if (!this.layer) return;

        try { this.layer.destroy(); }
        finally {
            this.layer = null;
            this.panel = null;
            this.lines.title = this.lines.fps = this.lines.pos = this.lines.cam = null;
            this._last.fps = this._last.pos = this._last.cam = "";
        }
    }
}

module.exports = PlayerUI;