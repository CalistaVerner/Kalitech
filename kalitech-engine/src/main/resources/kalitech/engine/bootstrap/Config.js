// FILE: resources/kalitech/engine/bootstrap/Config.js
"use strict";

const DEFAULT_CONFIG = {
    aliases: {
        "@core": "Scripts/core",
        "@engine": "Scripts/engine",
        "@env": "Scripts/environment"
    },

    dataConfig: {
        materials: {path: "data/materials.json"},
        camera: {path: "data/camera/camera.config.json"},
        movement: {path: "data/player/movement.config.json"},
        player: {path: "data/player.json"},
        sounds: {path: "data/sounds.json"}
    },

    builtins: {
        exposeGlobals: true,
    }
};

module.exports = {DEFAULT_CONFIG};