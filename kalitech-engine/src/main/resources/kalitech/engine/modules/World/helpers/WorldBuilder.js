// FILE: resources/kalitech/builtin/helpers/world/WorldBuilder.js
// (оставляем ваш файл как есть, он не конфликтует; можно позже удалить/депрекейт)
// NOTE: unchanged from current version
"use strict";

const {deepMerge, isObj, str, bool, numInt} = require("./WorldUtil.js");

class WorldBuilder {
    constructor(worldApi) {
        this._api = worldApi;

        this._desc = {
            name: "world",
            start: true,
            runtime: "world",
            orderStep: 10,
            systems: []
        };
    }

    merge(desc) {
        if (desc == null) return this;
        if (!isObj(desc)) throw new Error("[WORLD] builder.merge(desc): desc must be an object");
        this._desc = deepMerge(this._desc, desc);
        if (!Array.isArray(this._desc.systems)) this._desc.systems = [];
        return this;
    }

    name(v) {
        this._desc.name = str(v, "world");
        return this;
    }

    start(v = true) {
        this._desc.start = bool(v, true);
        return this;
    }

    runtime(v) {
        this._desc.runtime = str(v, "world");
        return this;
    }

    profile(v) {
        return this.runtime(v);
    }

    orderStep(v) {
        this._desc.orderStep = numInt(v, 10);
        return this;
    }

    system(v) {
        if (v == null) throw new Error("[WORLD] builder.system(v): v is required");
        this._desc.systems.push(v);
        return this;
    }

    jsSystem(module, config, opts) {
        const m = str(module, "");
        if (!m) throw new Error("[WORLD] jsSystem(module,...): module is required");

        const o = (opts && typeof opts === "object") ? opts : {};
        const rt = str(o.runtime ?? o.profile, this._desc.runtime);

        const sys = {
            module: m,
            runtime: rt,
            order: numInt(o.order, 0),
            stableId: (o.stableId != null) ? String(o.stableId) : null
        };

        const cfg = deepMerge({}, config || {});
        this._desc.systems.push(deepMerge(sys, cfg));
        return this;
    }

    build() {
        return this._api.normalize(this._desc);
    }

    create() {
        return this._api.create(this._desc);
    }
}

module.exports = {WorldBuilder};