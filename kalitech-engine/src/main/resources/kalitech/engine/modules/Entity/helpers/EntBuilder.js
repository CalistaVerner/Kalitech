// FILE: resources/kalitech/builtin/helpers/entity/EntBuilder.js
"use strict";

const {deepMerge} = require("./EntUtil.js");

class EntBuilder {
    constructor(entApi, presetName) {
        this._ent = entApi;
        this._presetName = presetName || "";
        this._cfg = {};
    }

    merge(cfg) {
        this._cfg = deepMerge(this._cfg, cfg || {});
        return this;
    }

    name(v) {
        this._cfg.name = String(v || "entity");
        return this;
    }

    debug(v = true) {
        this._cfg.debug = !!v;
        return this;
    }

    surface(v) {
        this._cfg.surface = deepMerge(this._cfg.surface || {}, v || {});
        return this;
    }

    body(v) {
        this._cfg.body = deepMerge(this._cfg.body || {}, v || {});
        return this;
    }

    attachSurface(v = true) {
        this._cfg.attachSurface = !!v;
        return this;
    }

    component(name, dataOrFn) {
        const n = String(name || "");
        if (!n) throw new Error("[ENT] builder.component(name,data): name required");
        if (!this._cfg.components) this._cfg.components = Object.create(null);
        this._cfg.components[n] = dataOrFn;
        return this;
    }

    create() {
        let base = {};
        if (this._presetName) {
            const p = this._ent._presets[this._presetName];
            if (p) base = deepMerge({}, p);
        }
        const finalCfg = deepMerge(base, this._cfg);
        return this._ent.create(finalCfg);
    }
}

module.exports = {EntBuilder};