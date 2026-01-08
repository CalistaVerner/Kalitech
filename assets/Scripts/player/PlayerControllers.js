// FILE: Scripts/player/PlayerControllers.js
"use strict";

const {PlayerEventsController} = require("./controllers/PlayerEventsController.js");
const {PlayerGameplayController} = require("./controllers/PlayerGameplayController.js");
const {PlayerCameraController} = require("./controllers/PlayerCameraController.js");
const {PlayerUIController} = require("./controllers/PlayerUIController.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function createPlayerRegistry() {
    const ENGINE = req(globalThis.ENGINE, "[PlayerControllers] ENGINE is required");
    const C = req(ENGINE.controllers, "[PlayerControllers] ENGINE.controllers is required");

    if (typeof C.registry !== "function") {
        throw new Error("[PlayerControllers] ENGINE.controllers.registry(name) is required");
    }

    const R = C.registry("player");

    // Сначала события/инпут/синхронизация
    R.register("player.events", PlayerEventsController, {order: 10});

    // Геймплей зависит от событий (инпут, action state, etc.)
    R.register("player.gameplay", PlayerGameplayController, {
        order: 20,
        deps: ["player.events"]
    });

    // Камера зависит от gameplay (поза/aim/third-person state)
    R.register("player.camera", PlayerCameraController, {
        order: 30,
        deps: ["player.gameplay"]
    });

    // UI обычно самый последний
    R.register("player.ui", PlayerUIController, {
        order: 40,
        deps: ["player.events", "player.gameplay"]
    });

    return R;
}

module.exports = {createPlayerRegistry};