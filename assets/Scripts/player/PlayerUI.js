// FILE: Scripts/player/PlayerUI.js
// Author: KΛYLΛ
"use strict";

/**
 * PlayerUI (DEV HUD) — styled & friendly
 *
 * Requires:
 *  - kalitech/builtin/Hud.js v2.5.0+ (panel.style, border/bg)
 *
 * Features:
 *  - Declarative panel placement (place.anchor)
 *  - Optional style: bg/border/alpha/radius
 *  - AutoHeight + relayout after build
 */
class PlayerUI {
    constructor(playerOrCtx, cfg = {}) {
        const isPlayer = !!(playerOrCtx && playerOrCtx.ctx);
        this.player = isPlayer ? playerOrCtx : null;
        this.ctx = isPlayer ? playerOrCtx.ctx : playerOrCtx;

        this.engine = this.ctx.engine;
        if (!this.engine) throw new Error("[PlayerUI] engine is not available");

        const hudNative = this.engine.hud && this.engine.hud();
        if (hudNative && typeof hudNative.setCursorEnabled === "function") hudNative.setCursorEnabled(false, true);

        // Prefer global HUD if host injected it, else require builtin
        this.HUD = HUD;

        const c = (isPlayer && playerOrCtx.cfg && playerOrCtx.cfg.ui) ? playerOrCtx.cfg.ui : (cfg || {});

        // --- Layout
        this.layerName = String(c.layerName || "debug-ui");
        this.anchor = String(c.anchor || "tl");
        this.mx = (c.marginLeft != null) ? +c.marginLeft : 10;
        this.my = (c.marginTop != null) ? +c.marginTop : 10;

        this.w = (c.w != null) ? +c.w : 280;
        this.padX = (c.padX != null) ? +c.padX : 12;
        this.padY = (c.padY != null) ? +c.padY : 8;

        this.fontTitle = (c.fontTitle != null) ? +c.fontTitle : 16;
        this.fontLine = (c.fontLine != null) ? +c.fontLine : 14;
        this.gap = (c.lineGap != null) ? +c.lineGap : 4;

        // --- Style (all optional)
        // You can override any of these via cfg.ui.style.*
        const style = this._styleFromCfg(c, "flat");

        this.style = style;

        this.layer = null;
        this.panel = null;

        this._fpsAccT = 0;
        this._fpsAccF = 0;
        this._fps = 0;
    }

    create() {
        if (this.layer) return this;

        this.layer = this.HUD.layer(this.layerName);

        this.panel = this.layer.panel({
            id: "debug.panel",
            w: this.w,
            h: 60,                 // start size; autoHeight expands
            autoHeight: true,
            padX: this.padX,
            padY: this.padY,
            flow: { fontSize: this.fontLine, gap: this.gap },
            place: { anchor: this.anchor, x: this.mx, y: this.my },
            style: this.style
        });

        // Title + lines
        this.panel.stack("debug.title", "DEBUG", { fontSize: this.fontTitle, color:"#FFFFFF" });
        this.panel.stack("debug.fps",   "FPS: --", { fontSize: this.fontLine, color:"#FFFFFF" });
        this.panel.stack("debug.pos",   "POS: --", { fontSize: this.fontLine, color:"#FFFFFF" });
        this.panel.stack("debug.cam",   "CAM: --", { fontSize: this.fontLine, color:"#FFFFFF" });

        // ✅ key: after autoHeight, recompute placement with new height
        this.layer.relayout();

        this.refresh(true);
        return this;
    }

    refresh(force = false) {
        if (!this.layer) return;

        const dt = this._dt();
        const fps = this.engine.api().fps();
        const pos = this._pose();
        const cam = this._camType();
        const fpsStr = "FPS: " + (fps > 0 ? fps.toFixed(1) : "--");
        const posStr = "POS: " + this._f2(pos.x) + " | " + this._f2(pos.y) + " | " + this._f2(pos.z);
        const camStr = "CAM: " + cam;

        this.layer.setText("debug.fps", fpsStr);
        this.layer.setText("debug.pos", posStr);
        this.layer.setText("debug.cam", camStr);
    }

    destroy() {
        if (!this.layer) return;
        try { this.layer.destroy(); }
        finally { this.layer = this.panel = null; }
    }

    // ------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------

    _dt() {
        const v = +(this.ctx && typeof this.ctx.dt === "number" ? this.ctx.dt : 0);
        return (v > 0 && Number.isFinite(v)) ? v : 0;
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

    _f2(v) {
        const n = +v;
        return Number.isFinite(n) ? n.toFixed(2) : "0.00";
    }

    _isObj(v) {
        return !!v && typeof v === "object" && !Array.isArray(v);
    }

    _styleFromCfg(c, theme) {
        // user override: c.style (exact passthrough expected by Hud.js v2.5.0)
        if (this._isObj(c.style)) return c.style;

        // optional shorthand overrides
        const bg = c.bgColor != null ? c.bgColor : null;
        const bgA = (c.bgAlpha != null) ? +c.bgAlpha : null;

        const br = c.borderColor != null ? c.borderColor : null;
        const brA = (c.borderAlpha != null) ? +c.borderAlpha : null;
        const brS = (c.borderSize != null) ? +c.borderSize : null;
        const brR = (c.borderRadius != null) ? +c.borderRadius : null;

        // defaults by theme
        // Note: radius may be ignored by engine; kept for future rounded bg support
        if (theme === "neon") {
            return {
                bg: { color: bg || "#05080cff", alpha: (bgA != null ? bgA : 0.55) },
                border: { size: (brS != null ? brS : 2), color: br || "#00E5FF", alpha: (brA != null ? brA : 0.90), radius: (brR != null ? brR : 10) }
            };
        }
        if (theme === "flat") {
            return {
                bg: { color: bg || "#101318", alpha: (bgA != null ? bgA : 0.80) },
                border: { size: (brS != null ? brS : 0), color: br || "#000000", alpha: (brA != null ? brA : 0.0), radius: (brR != null ? brR : 0) }
            };
        }
        // default "glass"
        return {
            bg: { color: bg || "#0b0f14", alpha: (bgA != null ? bgA : 0.65) },
            border: { size: (brS != null ? brS : 1), color: br || "#8AA0B6", alpha: (brA != null ? brA : 0.45), radius: (brR != null ? brR : 8) }
        };
    }
}

module.exports = PlayerUI;
