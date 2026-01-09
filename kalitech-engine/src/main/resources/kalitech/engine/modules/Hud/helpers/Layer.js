// FILE: resources/kalitech/builtin/helpers/hud/Layer.js
"use strict";

const {isObj, num, bool, idOf} = require("./HudUtil.js");
const {applyCoordY, parsePlace, placeRect, placePoint} = require("./HudPlacement.js");
const {Element, Panel} = require("./HudElements.js");
const {UIBuilder} = require("./UIBuilder.js");

class Layer {
    constructor(hud, handle) {
        this._hud = hud;
        this._api = hud._api;
        this.handle = handle;
        this.id = idOf(handle);

        this._reg = Object.create(null); // id -> Element
        this._placed = [];               // placed elements list (for relayout)
        this._lastVp = {w: 0, h: 0};
    }

    destroy() {
        this._api.destroyLayer(this.handle);
    }

    clear() {
        this._api.clearLayer(this.handle);
    }

    ui() {
        return new UIBuilder(this);
    }

    _regPut(id, el) {
        if (id == null) return el;
        const k = String(id);
        this._reg[k] = el;
        el.key = k;
        return el;
    }

    get(id) {
        return this._reg[String(id)] || null;
    }

    has(id) {
        return !!this._reg[String(id)];
    }

    drop(id, remove = false) {
        const k = String(id);
        const el = this._reg[k];
        if (el) {
            if (remove) this._api.remove(el.handle);
            delete this._reg[k];
        }
        return el || null;
    }

    setText(id, text) {
        const el = this.get(id);
        if (el) el.text(text);
        return el;
    }

    setValue(id, v) {
        const el = this.get(id);
        if (el) el.value(v);
        return el;
    }

    setVisible(id, v) {
        const el = this.get(id);
        if (el) el.visible(v);
        return el;
    }

    _vp() {
        const vp = this._hud.viewport();
        this._lastVp.w = vp.w | 0;
        this._lastVp.h = vp.h | 0;
        return this._lastVp;
    }

    _coord() {
        return this._hud._coord;
    }

    _trackPlaced(el) {
        if (el && el._place) this._placed.push(el);
        return el;
    }

    relayout() {
        const vp = this._vp();
        const coord = this._coord();

        for (let i = 0; i < this._placed.length; i++) {
            const el = this._placed[i];
            if (!el || !el._place) continue;

            const parent0 = el.parent;
            if (parent0 && parent0.kind === "panel") {
                const m = parent0.meta;
                const cw = Math.max(0, m.w - m.padX * 2);
                const ch = Math.max(0, m.h - m.padY * 2);

                const p = placePoint(cw, ch, el._place);
                let x = p.x + m.padX;
                let y = p.y + m.padY;

                y = applyCoordY(coord, 0, y);
                this._api.setPosition(el.handle, x, y);
            } else {
                if (el.kind === "panel") {
                    const p = placeRect(vp.w, vp.h, el._w, el._h, el._place);
                    const y = applyCoordY(coord, vp.h, p.y);
                    this._api.setPosition(el.handle, p.x, y);
                } else {
                    const p = placePoint(vp.w, vp.h, el._place);
                    const y = applyCoordY(coord, vp.h, p.y);
                    this._api.setPosition(el.handle, p.x, y);
                }
            }
        }
    }

    // --------------------------------------------------------
    // panel
    // --------------------------------------------------------

    panel(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        const vp = this._vp();
        const coord = this._coord();

        const w = num(c.w, 0);
        const h = num(c.h, 0);

        const padX = (c.padX != null) ? num(c.padX, 10) : (c.pad != null ? num(c.pad, 10) : 10);
        const padY = (c.padY != null) ? num(c.padY, 10) : (c.pad != null ? num(c.pad, 10) : 10);

        const place = parsePlace(c);

        let x = num(c.x, 0);
        let y = num(c.y, 0);

        if (place) {
            const xy = placeRect(vp.w, vp.h, w, h, place);
            x = xy.x;
            y = xy.y;
        }

        y = applyCoordY(coord, vp.h, y);

        const hPanel = this._api.addPanel(this.handle, x, y, w, h);

        const flowCfg = isObj(c.flow) ? c.flow : {};
        const meta = {
            w, h,
            padX, padY,
            fontSize: num(flowCfg.fontSize, 16),
            gap: num(flowCfg.gap, 6),
            autoHeight: bool(c.autoHeight, false)
        };

        const panel = new Panel(this._hud, hPanel, this, meta);
        panel._w = w;
        panel._h = h;

        if (!bool(c.visible, true)) panel.visible(false);

        const hContent = this._api.addPanel(this.handle, panel.handle, 0, 0, w, h);
        const content = new Element(this._hud, hContent, this, panel);
        content.visible(true);
        panel.content = content;

        if (place) {
            panel._setPlace(place);
            this._trackPlaced(panel);
        }

        if (c.id != null) this._regPut(c.id, panel);
        return panel;
    }

    rect(cfg = {}) {
        return this.panel(cfg);
    }

    // --------------------------------------------------------
    // text / label
    // --------------------------------------------------------

    _parentInfo(parent0) {
        if (!parent0) return {ph: null, insetX: 0, insetY: 0, cw: 0, ch: 0};

        if (parent0 && parent0.kind === "panel") {
            const m = parent0.meta;
            return {
                ph: parent0.content ? parent0.content.handle : parent0.handle,
                insetX: m.padX,
                insetY: m.padY,
                cw: Math.max(0, m.w - m.padX * 2),
                ch: Math.max(0, m.h - m.padY * 2)
            };
        }

        return {ph: parent0.handle, insetX: 0, insetY: 0, cw: 0, ch: 0};
    }

    text(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        const vp = this._vp();
        const coord = this._coord();

        const parent0 = c.parent || null;
        const pi = this._parentInfo(parent0);

        let x = num(c.x, 0) + pi.insetX;
        let y = num(c.y, 0) + pi.insetY;

        const place = parsePlace(c);
        if (place) {
            const cw = pi.cw || vp.w;
            const ch = pi.ch || vp.h;
            const xy = placePoint(cw, ch, place);
            x = xy.x + pi.insetX;
            y = xy.y + pi.insetY;
        }

        if (!parent0) y = applyCoordY(coord, vp.h, y);

        const ph = pi.ph;
        const hLabel = ph
            ? this._api.addLabel(this.handle, ph, String(c.text ?? ""), x, y)
            : this._api.addLabel(this.handle, String(c.text ?? ""), x, y);

        const el = new Element(this._hud, hLabel, this, parent0);

        if (c.key != null) el._bindPrefix = String(c.key);
        if (typeof c.format === "function") el._bindFmt = c.format;

        if (!bool(c.visible, true)) el.visible(false);
        if (c.fontSize != null) el.fontSize(c.fontSize);

        if (place) {
            el._setPlace(place);
            this._trackPlaced(el);
        }

        if (c.id != null) this._regPut(c.id, el);
        return el;
    }

    label(cfg = {}) {
        return this.text(cfg);
    }

    // --------------------------------------------------------
    // flow stacking (FIXED)
    // --------------------------------------------------------

    /**
     * stackText(panel, { id?, text, x?, fontSize?, gap?, visible? })
     *
     * FIX (2.4.2):
     *  - Shift stacked labels DOWN by `fontSize` to keep baseline inside panel.
     *  - AutoHeight accounts for this shift.
     *
     * FIX (2.4.3):
     *  - After autoHeight resize, re-apply panel placement so anchors stay fixed.
     */
    stackText(panel, cfg = {}) {
        if (!panel || panel.kind !== "panel") throw new Error("[HUD] stackText expects Panel");

        const c = isObj(cfg) ? cfg : {};
        const m = panel.meta;

        const fs = num(c.fontSize, panel.flow.fontSize);
        const gap = num(c.gap, panel.flow.gap);

        const lh = Math.max(1, fs + Math.ceil(fs * 0.25));

        const x = num(c.x, 0);

        const y = panel.flow.y + fs;

        panel.flow.y += lh + gap;

        const prefix = (c.key != null) ? String(c.key)
            : (c.prefix != null) ? String(c.prefix)
                : null;

        const fmt = (typeof c.format === "function") ? c.format : null;

        let text0;
        if (c.text != null) text0 = String(c.text);
        else if (fmt) text0 = String(fmt(c.value));
        else if (prefix != null) {
            const v0 = (c.value != null) ? c.value : "--";
            text0 = prefix + String(v0);
        } else {
            text0 = String(c.value != null ? c.value : "");
        }

        const el = this.text({
            parent: panel,
            x,
            y,
            text: String(text0 ?? ""),
            visible: bool(c.visible, true),
            fontSize: fs
        });

        if (c.id != null) this._regPut(c.id, el);

        if (m.autoHeight) {
            const contentBottom = panel.flow.y + fs;
            const need = m.padY + contentBottom + m.padY;

            if (need > m.h) {
                m.h = need;
                panel._h = m.h;

                this._api.setSize(panel.handle, m.w, m.h);
                if (panel.content) this._api.setSize(panel.content.handle, m.w, m.h);

                if (panel._place) {
                    const vp = this._vp();
                    const coord = this._coord();
                    const p = placeRect(vp.w, vp.h, panel._w, panel._h, panel._place);
                    const yPinned = applyCoordY(coord, vp.h, p.y);
                    this._api.setPosition(panel.handle, p.x, yPinned);
                }
            }
        }

        return el;
    }
}

module.exports = {Layer};