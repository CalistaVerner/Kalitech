// FILE: Scripts/player/index.js
"use strict";

const {PlayerController} = require("./PlayerController.js");

let _player = null;

module.exports.create = function create(ctx, cfg) {
    return new PlayerController(ctx, cfg || null);
};

module.exports.init = function init(ctx, cfg) {
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
};

module.exports.PlayerController = PlayerController;