"use strict";

const SkySystem = require("./SkySystem.js");

let SYS = null;

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function isFn(f) {
    return typeof f === "function";
}

function resolveEngineApi(ctx) {
    // STRICT: world provides EngineDomain, real API is ctx.engine.api()
    if (ctx && ctx.engine && isFn(ctx.engine.api)) {
        const api = ctx.engine.api();
        if (api) return api;
    }

    // fallback (NOT silent): allow global ENGINE if it already is api-like
    if (typeof ENGINE !== "undefined" && ENGINE) {
        if (isFn(ENGINE.render)) return ENGINE;        // already API
        if (isFn(ENGINE.api)) return ENGINE.api();     // wrapper with api()
    }

    throw new Error("[sky] ctx.engine.api() required (EngineApi not found)");
}

module.exports = {
    init(ctx) {
        const log = ENGINE && ENGINE.log ? ENGINE.log : null;

        const E = resolveEngineApi(ctx);

        if (log && log.debug) {
            log.debug("[sky/index] init: engineApi resolved. keys=" + Object.keys(E).join(","));
            log.debug("[sky/index] ctx.engine keys=" + Object.keys((ctx && ctx.engine) || {}).join(","));
        }

        SYS = new SkySystem(E);
        SYS.init(ctx);
    },

    update(ctx, tpf) {
        if (!SYS) throw new Error("[sky] not initialized");
        SYS.update(ctx, tpf);
    },

    destroy() {
        if (!SYS) return;
        SYS.destroy();
        SYS = null;

        const log = ENGINE && ENGINE.log ? ENGINE.log : null;
        if (log && log.debug) log.debug("[sky/index] destroy");
    }
};