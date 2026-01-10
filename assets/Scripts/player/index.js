"use strict";

const {PlayerController} = require("./PlayerController.js");
const {createPlayerRegistry} = require("./PlayerControllers.js");

let _player = null;
let _registered = false;

function ensureRegistered() {
    if (_registered) return;

    const ENGINE = globalThis.ENGINE;
    if (!ENGINE || !ENGINE.controllers) throw new Error("[player] ENGINE.controllers required");
    if (typeof ENGINE.controllers.registerRegistry !== "function") {
        throw new Error("[player] ENGINE.controllers.registerRegistry(registry) required");
    }

    ENGINE.controllers.registerRegistry(createPlayerRegistry());
    _registered = true;
}

module.exports.create = function create(ctx, cfg) {
    ensureRegistered();
    return new PlayerController(ctx, cfg || null);
};

module.exports.init = function init(ctx, cfg) {
    ensureRegistered();
    if (_player) return _player;
    _player = new PlayerController(ctx, cfg || null);
    return _player;
};

module.exports.update = function update(ctx, tpf) {
    if (_player) _player.update(tpf);
};

module.exports.destroy = function destroy(ctx) {
    if (_player) _player.dispose();
    _player = null;
    _registered = false;
};

module.exports.PlayerController = PlayerController;
