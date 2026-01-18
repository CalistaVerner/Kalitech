// FILE: resources/kalitech/builtin/helpers/world/WorldSession.js
"use strict";

const {deepMerge, isObj, str, bool, numInt} = require("./WorldUtil.js");

function normalizeTimeDesc(time) {
    if (!isObj(time)) return null;

    const out = {};

    if (time.worldTime != null) out.worldTime = +time.worldTime;
    if (time.timeRate != null) out.timeRate = +time.timeRate;
    if (time.paused != null) out.paused = !!time.paused;
    if (time.fixedStep != null) out.fixedStep = +time.fixedStep;
    if (time.maxDelta != null) out.maxDelta = +time.maxDelta;

    return out;
}

class WorldSession {
    constructor(worldApi, seed) {
        this._api = worldApi;
        this._desc = isObj(seed) ? deepMerge({}, seed) : {};
        if (!Array.isArray(this._desc.systems)) this._desc.systems = [];
    }

    merge(v) {
        if (v == null) return this;
        if (!isObj(v)) throw new Error("[WORLD] session.merge(v): v must be an object");
        this._desc = deepMerge(this._desc, v);
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

    systems(list) {
        this._desc.systems = Array.isArray(list) ? list : [];
        return this;
    }

    addSystem(v) {
        if (v == null) throw new Error("[WORLD] addSystem(v): v is required");
        if (!Array.isArray(this._desc.systems)) this._desc.systems = [];
        this._desc.systems.push(v);
        return this;
    }

    time(v) {
        const t = normalizeTimeDesc(v);
        if (t && Object.keys(t).length) {
            this._desc.time = t;
        } else {
            delete this._desc.time;
        }
        return this;
    }

    build() {
        return this._api.normalize(this._desc);
    }

    create() {
        return this._api.create(this._desc);
    }

    run() {
        return this.create();
    }
}

module.exports = {WorldSession};