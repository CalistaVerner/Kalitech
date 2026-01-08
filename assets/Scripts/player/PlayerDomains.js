// FILE: Scripts/player/PlayerDomains.js
"use strict";

function must(x, msg) {
    if (!x) throw new Error(msg);
    return x;
}

function engineApiFrom(ctx) {
    if (ctx && typeof ctx.api === "function") return ctx.api();
    if (ctx && ctx.engine && typeof ctx.engine.api === "function") return ctx.engine.api();
    if (ctx && typeof ctx.engineApi === "function") return ctx.engineApi();
    throw new Error("[player] ctx must provide api()");
}

function resolveDomains(ctx) {
    const E = engineApiFrom(ctx);

    const physics = ENGINE.physics;
    if (!physics) throw new Error("[player] PHYSICS builtin required (API)");

    return Object.freeze({
        ctx,
        engine: E,
        physics,
        input: must(E.input(), "[player] engine.input() required"),
        camera: must(E.camera(), "[player] engine.camera() required"),
        assets: must(E.assets(), "[player] engine.assets() required"),
        entity: must(E.entity && E.entity(), "[player] engine.entity() required"),
        mesh: must(E.mesh && E.mesh(), "[player] engine.mesh() required"),
        surface: must(E.surface && E.surface(), "[player] engine.surface() required"),
        bus: (typeof E.bus === "function") ? E.bus() : null,
        hud: must((typeof HUD !== "undefined" && HUD) ? HUD : null, "[player] HUD builtin required"),
        hudNative: (typeof E.hud === "function") ? E.hud() : null
    });
}

module.exports = {resolveDomains};