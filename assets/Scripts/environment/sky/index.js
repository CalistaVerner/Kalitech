// FILE: Scripts/environment/sky/index.js
// Author: Calista Verner
"use strict";

// IMPORTANT: use the canonical system implementation (single source of truth)
const SkySystem = require("./SkySystem.js");
// если у тебя нет алиаса @systems → замени на "Scripts/systems/sky/SkySystem.js"

let SYS = null;

function hasFn(obj, name) {
    try { return !!obj && typeof obj[name] === "function"; } catch (_) { return false; }
}

function resolveEngine(ctx) {
    // choose the engine that actually exposes render()
    const candidates = [
        (typeof engine !== "undefined") ? engine : null,
        ctx && ctx.engineApi,
        ctx && ctx.api,
        ctx && ctx.engine,
        ctx
    ];
    for (let i = 0; i < candidates.length; i++) {
        const e = candidates[i];
        if (hasFn(e, "render")) return e;
    }
    throw new Error("[sky] cannot resolve engine with render()");
}

function ensureSys(ctx) {
    if (SYS) return SYS;
    SYS = new SkySystem(resolveEngine(ctx));
    return SYS;
}

module.exports.init = function (ctx) { ensureSys(ctx).init(ctx); };
module.exports.update = function (ctx, tpf) { ensureSys(ctx).update(ctx, tpf); };
module.exports.destroy = function () { if (SYS) { SYS.destroy(); SYS = null; } };
