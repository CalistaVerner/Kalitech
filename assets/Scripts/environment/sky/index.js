// Author: Calista Verner
"use strict";

const SkySystem = require("./SkySystem.js");

let SYS = null;

function hasFn(obj, name) {
    try { return !!obj && typeof obj[name] === "function"; } catch (_) { return false; }
}

function safeType(x) {
    try {
        if (x === null) return "null";
        if (x === undefined) return "undefined";
        if (Array.isArray(x)) return "array";
        return typeof x;
    } catch (_) { return "unknown"; }
}

function safeKeys(obj, limit) {
    const out = [];
    const n = (limit | 0) || 40;
    try {
        if (!obj || (typeof obj !== "object" && typeof obj !== "function")) return out;
        // Value/HostObject иногда не любит Object.keys
        const ks = Object.keys(obj);
        for (let i = 0; i < ks.length && i < n; i++) out.push(ks[i]);
        return out;
    } catch (_) {
        // fallback: best effort
        try {
            for (const k in obj) { out.push(k); if (out.length >= n) break; }
        } catch (_) {}
        return out;
    }
}

function resolveEngine(ctx) {
    if (ctx && ctx.api && typeof ctx.api.render === "function") return ctx.api;
    if (typeof engine !== "undefined" && engine && typeof engine.render === "function") return engine;
    if (ctx && typeof ctx.render === "function") return ctx; // fallback
    throw new Error("[sky] cannot resolve engine with render()");
}


function ensureSys(ctx) {
    if (SYS) return SYS;
    const eng = resolveEngine(ctx);
    SYS = new SkySystem(eng);
    return SYS;
}

module.exports.init = function (ctx) {
    ensureSys(ctx).init(ctx);
};

module.exports.update = function (ctx, tpf) {


    ensureSys(ctx).update(ctx, tpf);
};

module.exports.destroy = function () {
    if (SYS) { try { SYS.destroy(); } catch (_) {} SYS = null; }
};