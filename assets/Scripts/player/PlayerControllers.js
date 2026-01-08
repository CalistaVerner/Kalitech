// FILE: Scripts/player/PlayerControllers.js
"use strict";

const {ControllerRegistry} = require("../core/controller/ControllerRegistry.js");

const {PlayerEventsController} = require("./controllers/PlayerEventsController.js");
const {PlayerGameplayController} = require("./controllers/PlayerGameplayController.js");
const {PlayerCameraController} = require("./controllers/PlayerCameraController.js");
const {PlayerUIController} = require("./controllers/PlayerUIController.js");

function createPlayerRegistry() {
    const R = new ControllerRegistry("player");

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