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
 * NOTE:
 *  - Current Kalitech HUD runtime is top-left / y-down. No legacy conversions here.
 *  - Uses autoHeight + autoWidth so text never "spills" out of panel.
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

            // start size (panel can grow)
            w:  (c.w  != null) ? +c.w  : 260,
            h0: (c.h0 != null) ? +c.h0 : 70,

            // autosize limits (new)
            minW: (c.minW != null) ? +c.minW : ((c.w != null) ? +c.w : 260),
            maxW: (c.maxW != null) ? +c.maxW : 900,

            pad: (c.pad != null) ? +c.pad : 10,
            padX: (c.padX != null) ? +c.padX : null,
            padY: (c.padY != null) ? +c.padY : null,

            fontTitle: (c.fontTitle != null) ? +c.fontTitle : 18,
            fontLine:  (c.fontLine  != null) ? +c.fontLine  : 16,
            gap:       (c.lineGap   != null) ? +c.lineGap   : 4,

            fpsWindow: (c.fpsWindow != null) ? +c.fpsWindow : 0.25
        };

        this.layer = null;
        this.panel = null;
        this.lines = { title: null, fps: null, pos: null, cam: null };

        this._last = { fps: "", pos: "", cam: "" };

        // fps smoothing fallback
        this._fpsAccT = 0;
        this._fpsAccF = 0;
        this._fpsSmoothed = 0;
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

            // ✅ prevent spill: panel grows for content
            autoHeight: true,
            autoWidth: true,

            // ✅ safe width limits (Hud.js v2.3+ reads these; v2.2.4 will ignore harmlessly)
            minW: L.minW,
            maxW: L.maxW,

            // spacing (both generations)
            lineGap: L.gap,                      // v2.2.4
            flow: { fontSize: L.fontLine, gap: L.gap } // v2.3.x
        };

        // padding (both generations; ignored if not supported)
        if (L.padX != null || L.padY != null) {
            cfg.padX = (L.padX != null) ? +L.padX : L.pad;
            cfg.padY = (L.padY != null) ? +L.padY : L.pad;
            cfg.pad = L.pad;
        } else {
            cfg.pad = L.pad;
            cfg.padX = L.pad;
            cfg.padY = L.pad;
        }

        // v2.3.x uses place, v2.2.4 uses x/y (both in top-left in current runtime)
        if (typeof layer.panel === "function") {
            cfg.place = { anchor: L.anchor, x: L.mx, y: L.my };
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

    _fpsUpdate(dt) {
        // Preferred: engine.fps() (you said app.getFps() doesn't exist)
        try {
            if (this.engine && typeof this.engine.fps === "function") {
                const v = +this.engine.fps();
                if (Number.isFinite(v) && v > 0) return v;
            }
        } catch (_) {}

        // Fallback: smooth 1/dt over window (stable enough for dev HUD)
        if (dt > 0) {
            this._fpsAccT += dt;
            this._fpsAccF += 1;
            const win = this.layout.fpsWindow > 0 ? this.layout.fpsWindow : 0.25;
            if (this._fpsAccT >= win) {
                this._fpsSmoothed = this._fpsAccF / this._fpsAccT;
                this._fpsAccT = 0;
                this._fpsAccF = 0;
            }
        }
        return this._fpsSmoothed || 0;
    }

    _pose() {
        const p = this.player;
        if (!p) return { x: 0, y: 0, z: 0 };

        const pose = p.dom && p.dom.pose;
        if (pose && typeof pose.x === "number") return pose;

        const st = p.state && p.state.pos;
        if (st && typeof st.x === "number") return st;

        return { x: 0, y: 0, z: 0 };
    }

    _camType() {
        const v = this.player && this.player.dom && this.player.dom.view;
        return (v && v.type) ? String(v.type) : "--";
    }

    _fmt2(v) {
        const n = +v;
        return Number.isFinite(n) ? n.toFixed(2) : "0.00";
    }

    _setText(line, s) {
        if (!line) return;
        if (typeof line.text === "function") line.text(s);
        else if (typeof line.setText === "function") line.setText(s);
        else if (typeof line.set === "function") line.set("text", s);
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

        const dt = this._dt();
        const fps = this._fpsUpdate(dt);
        const pos = this._pose();
        const cam = this._camType();

        const fpsStr = "FPS: " + (fps > 0 ? fps.toFixed(1) : "--");
        const posStr = "POS: " + this._fmt2(pos.x) + " | " + this._fmt2(pos.y) + " | " + this._fmt2(pos.z);
        const camStr = "CAM: " + cam;

        if (force || this._last.fps !== fpsStr) { this._setText(this.lines.fps, fpsStr); this._last.fps = fpsStr; }
        if (force || this._last.pos !== posStr) { this._setText(this.lines.pos, posStr); this._last.pos = posStr; }
        if (force || this._last.cam !== camStr) { this._setText(this.lines.cam, camStr); this._last.cam = camStr; }
    }

    destroy() {
        if (!this.layer) return;

        try {
            if (typeof this.layer.destroy === "function") this.layer.destroy();
        } finally {
            this.layer = null;
            this.panel = null;
            this.lines.title = this.lines.fps = this.lines.pos = this.lines.cam = null;
            this._last.fps = this._last.pos = this._last.cam = "";
            this._fpsAccT = 0;
            this._fpsAccF = 0;
            this._fpsSmoothed = 0;
        }
    }
}

module.exports = PlayerUI;