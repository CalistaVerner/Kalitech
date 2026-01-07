"use strict";

const SkySystem = require("./SkySystem.js");
let SYS = null;

function req(v, msg) {
    if (!v) throw new Error(msg);
    return v;
}

module.exports.init = function (ctx) {
    const E = req(ctx && ctx.engine && typeof ctx.engine.api === "function" ? ctx.engine.api() : null, "[sky] ctx.engine.api() required");
    SYS = new SkySystem(E);
    SYS.init(ctx);
};

module.exports.update = function (ctx, tpf) {
    if (!SYS) throw new Error("[sky] not initialized");
    SYS.update(ctx, tpf);
};

module.exports.destroy = function () {
    if (!SYS) return;
    SYS.destroy();
    SYS = null;
};