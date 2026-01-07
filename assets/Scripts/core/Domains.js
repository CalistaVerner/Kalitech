"use strict";

function req(v, msg) {
    if (!v) throw new Error(msg);
    return v;
}

function isFn(v) {
    return typeof v === "function";
}

class Domains {
    constructor(ctx) {
        this.ctx = req(ctx, "[domains] ctx required");

        const engineDomain = req(ctx.engine, "[domains] ctx.engine required");
        this.engine = req(isFn(engineDomain.api) ? engineDomain.api() : null, "[domains] ctx.engine.api() required");

        this.physics = req(isFn(this.engine.physics) ? this.engine.physics() : null, "[domains] engine.physics() required");
        this.input = req(isFn(this.engine.input) ? this.engine.input() : null, "[domains] engine.input() required");

        this.camera = req(isFn(this.engine.camera) ? this.engine.camera() : null, "[domains] engine.camera() required");
        this.assets = req(isFn(this.engine.assets) ? this.engine.assets() : null, "[domains] engine.assets() required");

        this.hudNative = isFn(this.engine.hud) ? this.engine.hud() : null;

        this.bus = isFn(this.engine.bus) ? this.engine.bus() : null;
        this.surface = isFn(this.engine.surface) ? this.engine.surface() : null;

        this.hud = (typeof HUD !== "undefined" && HUD) ? HUD : null;
        this.log = (typeof LOG !== "undefined" && LOG) ? LOG : null;
    }
}

module.exports = Domains;
