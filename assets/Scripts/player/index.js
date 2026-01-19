"use strict";

const { PlayerController } = require("./PlayerController.js");
const { createPlayerRegistryFromConfig } = require("./PlayerControllers.js");

let _player = null;
let _registered = false;

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function ensureRegistered(cfg) {
    if (_registered) return;

    const ENGINE = req(globalThis.ENGINE, "[player] ENGINE required");
    req(ENGINE.controllers, "[player] ENGINE.controllers required");
    console.log(JSON.stringify(cfg));

    const registry = createPlayerRegistryFromConfig(cfg);
    ENGINE.controllers.registerRegistry(registry);

    _registered = true;
}

module.exports.create = function create(ctx, cfg) {
    ensureRegistered(cfg);
    return new PlayerController(ctx, cfg || null);
};

module.exports.init = function init(ctx, cfg) {
    ensureRegistered(cfg);
    if (_player) return _player;
    _player = new PlayerController(ctx, cfg || null);
    return _player;
};

module.exports.update = function update(ctx, tpf) {
    if (_player) _player.update(tpf);
};

module.exports.destroy = function destroy(ctx) {
    try {
        if (_player) _player.dispose();
    } catch (_) {
    }
    _player = null;
    _registered = false;
};

module.exports.PlayerController = PlayerController;