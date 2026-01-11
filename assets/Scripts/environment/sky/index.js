"use strict";

const SkySystem = require("./SkySystem.js");

let SYS = null;

function isFn(f) {
    return typeof f === "function";
}

function resolveEngineApi(ctx) {
    if (!ctx || !ctx.engine || !isFn(ctx.engine.api)) {
        throw new Error("[sky] ctx.engine.api() is required");
    }
    const api = ctx.engine.api();
    if (!api) throw new Error("[sky] ctx.engine.api() returned null");
    return api;
}

module.exports = {
    init(ctx) {
        const E = resolveEngineApi(ctx);
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
    }
};