// Author: KΛYLΛ
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
    // 1) прямой api
    let e = ctx && (ctx.api || ctx.engine || ctx.engineApi || ctx.engine?.api?.());
    // 2) если engine — домен с api()
    if (e && typeof e.api === "function") e = e.api();
    // 3) если render-домен
    if (!e && ctx && ctx.render && typeof ctx.render.api === "function") e = ctx.render.api();
    // 4) глобальный fallback
    if (!e && typeof engine !== "undefined") e = engine;

    // проверка render()
    if (!e || typeof e.render !== "function") {
        throw Error("[sky] cannot resolve engine with render()");
    }
    return e;
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