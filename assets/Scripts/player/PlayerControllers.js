// FILE: Scripts/player/PlayerControllers.js
"use strict";

const { PlayerEventsController } = require("./controllers/PlayerEventsController.js");
const { PlayerGameplayController } = require("./controllers/PlayerGameplayController.js");
const { PlayerCameraController } = require("./controllers/PlayerCameraController.js");
const { PlayerHandsRigController } = require("./controllers/PlayerHandsRigController.js");
const { PlayerUIController } = require("./controllers/PlayerUIController.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

/**
 * Creates controller instances for the given player.
 * Controllers are ordered deterministically (no registry magic).
 *
 * @param {object} player PlayerController instance
 * @returns {Array<object>} controllers
 */
function createPlayerControllers(player) {
    req(player, "[PlayerControllers] player is required");

    // Strict instantiation with dependency injection (player -> controller).
    // Order is the only dependency mechanism here (simple + stable).
    return [
        new PlayerEventsController(player),     // order: 10
        new PlayerGameplayController(player),   // order: 20
        new PlayerHandsRigController(player),   // order: 25
        new PlayerCameraController(player),     // order: 30
        new PlayerUIController(player)          // order: 40
    ];
}

module.exports = { createPlayerControllers };