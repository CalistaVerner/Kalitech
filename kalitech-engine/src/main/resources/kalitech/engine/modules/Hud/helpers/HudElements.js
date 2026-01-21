// FILE: resources/kalitech/builtin/helpers/hud/HudElements.js
"use strict";

const {num, bool, idOf} = require("./HudUtil.js");

class Element {
    constructor(hud, handle, layer, parent) {
        this._hud = hud;
        this._api = hud._api;
        this.handle = handle;
        this.id = idOf(handle);
        this.layer = layer;
        this.parent = parent || null;

        this.kind = "element"; // panel/text/container/input/checkbox/slider/radio
        this.key = null;

        // placement (Layer relayout uses this)
        this._place = null;

        // sizing cache in JS for relayout; Layer keeps _w/_h
        this._w = 0;
        this._h = 0;

        // bind helpers for value()
        this._bindPrefix = null;
        this._bindFmt = null;
    }

    // --------------------------------------------------------
    // base ops
    // --------------------------------------------------------

    text(v) {
        this._api.setText(this.handle, String(v ?? ""));
        return this;
    }

    getText() {
        if (typeof this._api.getText === "function") {
            return String(this._api.getText(this.handle) ?? "");
        }
        return "";
    }

    visible(v) {
        this._api.setVisible(this.handle, !!v);
        return this;
    }

    pos(x, y) {
        this._api.setPosition(this.handle, num(x, 0), num(y, 0));
        return this;
    }

    size(w, h) {
        this._w = num(w, 0);
        this._h = num(h, 0);
        this._api.setSize(this.handle, this._w, this._h);
        return this;
    }

    bg(r, g, b, a) {
        this._api.setBgColor(this.handle, num(r, 0), num(g, 0), num(b, 0), num(a, 1));
        return this;
    }

    color(r, g, b, a) {
        this._api.setTextColor(this.handle, num(r, 1), num(g, 1), num(b, 1), num(a, 1));
        return this;
    }

    fontSize(px) {
        if (typeof this._api.setFontSize === "function") {
            this._api.setFontSize(this.handle, Math.max(6, num(px, 14)));
        }
        return this;
    }

    remove() {
        // Layer owns registry removal
        if (this.layer) this.layer.drop(this.key ?? this.id, true);
        else this._api.remove(this.handle);
        return null;
    }

    // --------------------------------------------------------
    // value() - smart for input/slider, default = text
    // --------------------------------------------------------

    value(v) {
        if (this.kind === "slider") {
            if (v === undefined) {
                if (typeof this._api.getSliderValue === "function") return +this._api.getSliderValue(this.handle) || 0;
                return 0;
            }
            if (typeof this._api.setSliderValue === "function") this._api.setSliderValue(this.handle, num(v, 0));
            return this;
        }

        if (v === undefined) {
            return this.getText();
        }

        let s;
        if (this._bindFmt) s = this._bindFmt(v);
        else s = (v == null) ? "" : String(v);

        if (this._bindPrefix != null) s = this._bindPrefix + String(s ?? "");
        this._api.setText(this.handle, String(s ?? ""));
        return this;
    }

    checked(v) {
        if (typeof this._api.setChecked !== "function" || typeof this._api.isChecked !== "function") {
            return v === undefined ? false : this;
        }
        if (v === undefined) return !!this._api.isChecked(this.handle);
        this._api.setChecked(this.handle, !!v);
        return this;
    }

    // binding helpers
    bindPrefix(prefix) {
        this._bindPrefix = (prefix == null) ? null : String(prefix);
        return this;
    }

    bindFormat(fn) {
        this._bindFmt = (typeof fn === "function") ? fn : null;
        return this;
    }

    _setPlace(place) {
        this._place = place || null;
        return this;
    }
}

class Panel extends Element {
    constructor(hud, handle, layer, parent) {
        super(hud, handle, layer, parent);
        this.kind = "panel";

        // flow stack for text
        this._flow = {
            padX: 0,
            padY: 0,
            gap: 0,
            fontSize: null
        };

        this._stackY = 0;
        this._stack = []; // stack items for relayout (points)
    }

    flow(cfg = {}) {
        const c = cfg || {};
        this._flow.padX = num(c.padX, this._flow.padX);
        this._flow.padY = num(c.padY, this._flow.padY);
        this._flow.gap = num(c.gap, this._flow.gap);
        this._flow.fontSize = (c.fontSize != null) ? num(c.fontSize, 14) : this._flow.fontSize;
        return this;
    }

    stack(id, text, cfg = {}) {
        // Layer will provide stackText(), but keep legacy: if called on panel directly
        if (!this.layer || typeof this.layer.stackText !== "function") {
            throw new Error("[HUD] Panel.stack requires Layer.stackText");
        }
        return this.layer.stackText(this, Object.assign({}, cfg || {}, {id, text}));
    }
}

module.exports = {Element, Panel};