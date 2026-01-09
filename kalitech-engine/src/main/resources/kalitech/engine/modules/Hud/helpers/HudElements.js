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

        this.kind = "element";
        this.key = null;

        this._place = null;
        this._w = 0;
        this._h = 0;

        this._bindPrefix = null;
        this._bindFmt = null;
    }

    text(v) {
        this._api.setText(this.handle, String(v ?? ""));
        return this;
    }

    value(v) {
        let s;
        if (this._bindFmt) s = this._bindFmt(v);
        else s = (v == null) ? "" : String(v);

        if (this._bindPrefix != null) s = this._bindPrefix + String(s ?? "");
        return this.text(String(s ?? ""));
    }

    visible(v) {
        this._api.setVisible(this.handle, !!v);
        return this;
    }

    pos(x, y) {
        this._api.setPosition(this.handle, num(x), num(y));
        return this;
    }

    size(w, h) {
        w = num(w);
        h = num(h);
        this._w = w;
        this._h = h;
        this._api.setSize(this.handle, w, h);
        return this;
    }

    remove() {
        this._api.remove(this.handle);
        return this;
    }

    fontSize(px) {
        if (typeof this._api.setFontSize === "function") this._api.setFontSize(this.handle, num(px, 16));
        return this;
    }

    _setPlace(place) {
        this._place = place;
        return this;
    }
}

class Panel extends Element {
    constructor(hud, handle, layer, meta) {
        super(hud, handle, layer, null);
        this.kind = "panel";
        this.meta = meta;

        this.content = null;

        this.flow = {y: 0, gap: meta.gap, fontSize: meta.fontSize};

        this._kids = Object.create(null);
    }

    get(id) {
        return this._kids[String(id)] || null;
    }

    has(id) {
        return !!this._kids[String(id)];
    }

    drop(id, remove = false) {
        const k = String(id);
        const el = this._kids[k];
        if (el) {
            if (remove) this._api.remove(el.handle);
            delete this._kids[k];
        }
        return el || null;
    }

    text(id, text, cfg) {
        const el = this.layer.text(Object.assign({}, cfg || {}, {parent: this, id, text}));
        if (id != null) this._kids[String(id)] = el;
        return el;
    }

    stack(id, text, cfg) {
        const el = this.layer.stackText(this, Object.assign({}, cfg || {}, {id, text}));
        if (id != null) this._kids[String(id)] = el;
        return el;
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

    resetFlow(y = 0) {
        this.flow.y = num(y, 0);
        return this;
    }
}

module.exports = {Element, Panel};