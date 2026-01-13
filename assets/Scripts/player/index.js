// FILE: Scripts/player/index.js
"use strict";

const {PlayerController} = require("./PlayerController.js");
const {createPlayerRegistry} = require("./PlayerControllers.js");

let _player = null;
let _registered = false;

function getHotReloadDomain(ctx) {
    if (!ctx) return null;
    try {
        if (typeof ctx.hotReload === "function") return ctx.hotReload();
    } catch (_) {
    }
    try {
        if (ctx.hotReload && typeof ctx.hotReload.register === "function") return ctx.hotReload;
    } catch (_) {
    }
    return null;
}

function getStateDomain(ctx) {
    if (!ctx) return null;
    try {
        if (ctx.stateDomain && typeof ctx.stateDomain.get === "function") return ctx.stateDomain;
    } catch (_) {
    }
    try {
        if (typeof ctx.state === "function") return ctx.state();
    } catch (_) {
    }
    try {
        if (ctx.state && typeof ctx.state.get === "function") return ctx.state;
    } catch (_) {
    }
    return null;
}

function ensureHotReloadHook(ctx) {
    const hr = getHotReloadDomain(ctx);
    if (!hr || typeof hr.register !== "function") return false;

    const sd = getStateDomain(ctx);
    const FLAG = "__player_hot_reload_hook__";
    if (sd && typeof sd.get === "function" && sd.get(FLAG) === true) return true;

    hr.register((reason) => {
        try {
            // important: kill controller + entity
            module.exports.destroy(ctx);
        } catch (_) {
        }
    });

    if (sd && typeof sd.set === "function") sd.set(FLAG, true);
    return true;
}

function ensureRegistered(ctx) {
    if (_registered) return;

    const ENGINE = globalThis.ENGINE;
    if (!ENGINE || !ENGINE.controllers) throw new Error("[player] ENGINE.controllers required");
    if (typeof ENGINE.controllers.registerRegistry !== "function") {
        throw new Error("[player] ENGINE.controllers.registerRegistry(registry) required");
    }

    // hot reload hook (best effort, zero-crash)
    ensureHotReloadHook(ctx);

    ENGINE.controllers.registerRegistry(createPlayerRegistry());
    _registered = true;
}

module.exports.create = function create(ctx, cfg) {
    ensureRegistered(ctx);
    return new PlayerController(ctx, cfg || null);
};

module.exports.init = function init(ctx, cfg) {
    ensureRegistered(ctx);
    if (_player) return _player;
    _player = new PlayerController(ctx, cfg || null);
    return _player;
};

module.exports.update = function update(ctx, tpf) {
    if (_player) _player.update(tpf);
};

module.exports.destroy = function destroy(ctx) {
    // allow calling multiple times safely
    try {
        if (_player) _player.dispose();
    } catch (_) {
    }
    _player = null;

    // allow registries to be re-registered next time
    _registered = false;
};

module.exports.PlayerController = PlayerController;