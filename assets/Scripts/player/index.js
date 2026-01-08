// FILE: Scripts/player/index.js
"use strict";

const {PlayerEntityController} = require("./PlayerEntityController.js");

let _player = null;

module.exports.create = function create(ctx, cfg) {
    return new PlayerEntityController(ctx, cfg || null);
};

module.exports.init = function init(ctx, cfg) {
    if (_player) return _player;
    _player = new PlayerEntityController(ctx, cfg || null);
    return _player;
};

module.exports.update = function update(ctx, tpf) {
    if (_player) _player.update(tpf);
};

module.exports.destroy = function destroy(ctx) {
    if (_player) _player.dispose();
    _player = null;
};

module.exports.PlayerEntityController = PlayerEntityController;