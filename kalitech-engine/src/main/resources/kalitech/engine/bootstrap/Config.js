// FILE: resources/kalitech/engine/bootstrap/Config.js
"use strict";

const DEFAULT_CONFIG = {
    aliases: {
        "@core": "Scripts/core",
        "@lib": "Scripts/lib",
        "@engine": "Scripts/engine",
        "@systems": "Scripts/systems",
        "@materials": "Scripts/materials",
        "@env": "Scripts/environment"
    },

    dataConfig: {
        materials: {path: "data/materials.json"},
        camera: {path: "data/camera/camera.config.json"},
        movement: {path: "data/player/movement.config.json"},
        player: {path: "data/player.json"}
    },

    builtins: {
        exposeGlobals: true,
        modules: {
            materials: "@module/Material/Material",
            mesh: "@module/Mesh/Mesh",
            sound: "@module/Sound/Sound",
            entity: "@module/Entity/Entity",
            physics: "@module/Physics/Physics",
            log: "@module/Log/Log",
            input: "@module/Input/Input",
            events: "@module/Events/Events",
            terrain: "@module/Terrain/Terrain",
            hud: "@module/Hud/Hud"
        }
    },

    // NEW: engine-level controller registrators
    controllers: {
        exposeGlobals: true,
        registrators: [
            "resources/kalitech/engine/controllers/registrators/player.controllers.js"
        ]
    }
};

module.exports = {DEFAULT_CONFIG};