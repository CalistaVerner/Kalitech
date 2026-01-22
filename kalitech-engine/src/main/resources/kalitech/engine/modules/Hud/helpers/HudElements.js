// FILE: resources/kalitech/builtin/helpers/hud/HudElements.js
"use strict";

const {num, bool, idOf} = require("./HudUtil.js");

function isFn(v) {
    return typeof v === "function";
}

function readPath(root, path) {
    if (root == null) return undefined;
    const p = String(path || "").trim();
    if (!p) return undefined;

    let v = root;
    const parts = p.split(".");
    for (let i = 0; i < parts.length; i++) {
        const k = parts[i];
        if (!k) continue;
        if (v == null) return undefined;
        v = v[k];
    }
    return v;
}

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

        // value() formatting helpers
        this._bindPrefix = null;
        this._bindFmt = null;

        // view-model binding (optional)
        this._bindModel = null;
        this._bindPath = null;
        this._bindRead = null; // custom getter fn(model)->value
    }

    // --------------------------------------------------------
    // base ops
    // --------------------------------------------------------

    text(v) {
        this._api.setText(this.handle, String(v ?? ""));
        return this;
    }

    getText() {
        if (isFn(this._api.getText)) {
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
        if (this.layer && isFn(this.layer._markDirty)) this.layer._markDirty();
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
        if (isFn(this._api.setFontSize)) {
            this._api.setFontSize(this.handle, Math.max(6, num(px, 14)));
            if (this.layer && isFn(this.layer._markDirty)) this.layer._markDirty();
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
    // binding helpers
    // --------------------------------------------------------

    bindPrefix(prefix) {
        this._bindPrefix = (prefix == null) ? null : String(prefix);
        return this;
    }

    bindFormat(fn) {
        this._bindFmt = (typeof fn === "function") ? fn : null;
        return this;
    }

    /**
     * Bind this element to a view-model path.
     * Usage:
     *  el.bind(model, "cam.yaw", v => "CAM(yaw): " + v)
     * Then call el.pull() per tick, or layer.pullAll().
     */
    bind(model, path, fmtOrNull) {
        this._bindModel = model || null;
        this._bindPath = (path == null) ? null : String(path);
        this._bindRead = null;
        if (typeof fmtOrNull === "function") this._bindFmt = fmtOrNull;
        return this;
    }

    /**
     * Bind via custom getter: fn(model)->value
     */
    bindRead(model, fn, fmtOrNull) {
        this._bindModel = model || null;
        this._bindPath = null;
        this._bindRead = (typeof fn === "function") ? fn : null;
        if (typeof fmtOrNull === "function") this._bindFmt = fmtOrNull;
        return this;
    }

    /**
     * Pull value from bound model into this element.
     */
    pull() {
        if (!this._bindModel) return this;

        let v;
        if (this._bindRead) v = this._bindRead(this._bindModel);
        else if (this._bindPath) v = readPath(this._bindModel, this._bindPath);
        else return this;

        this.value(v);
        return this;
    }

    _formatValue(v) {
        let s;
        if (this._bindFmt) s = this._bindFmt(v);
        else s = (v == null) ? "" : String(v);

        if (this._bindPrefix != null) s = this._bindPrefix + String(s ?? "");
        return String(s ?? "");
    }

    // --------------------------------------------------------
    // value() - smart for input/slider, default = text
    // --------------------------------------------------------

    value(v) {
        if (this.kind === "slider") {
            if (v === undefined) {
                if (isFn(this._api.getSliderValue)) return +this._api.getSliderValue(this.handle) || 0;
                return 0;
            }
            if (isFn(this._api.setSliderValue)) this._api.setSliderValue(this.handle, num(v, 0));
            return this;
        }

        if (v === undefined) {
            return this.getText();
        }

        const s = this._formatValue(v);
        this._api.setText(this.handle, s);
        return this;
    }

    checked(v) {
        if (!isFn(this._api.setChecked) || !isFn(this._api.isChecked)) {
            return v === undefined ? false : this;
        }
        if (v === undefined) return !!this._api.isChecked(this.handle);
        this._api.setChecked(this.handle, !!v);
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

        this._flow = {
            padX: 0,
            padY: 0,
            gap: 0,
            fontSize: null
        };

        this._stack = []; // stack items for relayout
    }

    flow(cfg = {}) {
        const c = cfg || {};
        this._flow.padX = num(c.padX, this._flow.padX);
        this._flow.padY = num(c.padY, this._flow.padY);
        this._flow.gap = num(c.gap, this._flow.gap);
        this._flow.fontSize = (c.fontSize != null) ? num(c.fontSize, 14) : this._flow.fontSize;
        if (this.layer && isFn(this.layer._markDirty)) this.layer._markDirty();
        return this;
    }

    stack(id, text, cfg = {}) {
        if (!this.layer || typeof this.layer.stackText !== "function") {
            throw new Error("[HUD] Panel.stack requires Layer.stackText");
        }
        return this.layer.stackText(this, Object.assign({}, cfg || {}, {id, text}));
    }
}

module.exports = {Element, Panel};