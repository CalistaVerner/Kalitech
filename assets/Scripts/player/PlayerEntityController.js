// FILE: Scripts/player/PlayerEntityController.js
"use strict";

const {EntityControllerLink} = require("../core/EntityControllerLink.js");
const {ControllerStack} = require("../core/ControllerStack.js");

const {PlayerPawn} = require("./PlayerPawn.js");

const {PlayerController} = require("./PlayerController.js");
const {PlayerCameraController} = require("./controllers/PlayerCameraController.js");
const {PlayerUIController} = require("./controllers/PlayerUIController.js");
const {PlayerEventsController} = require("./controllers/PlayerEventsController.js");

class PlayerEntityController {
    constructor(ctx, cfg) {
        this.entity = new PlayerPawn(ctx, cfg).init();

        const stack = new ControllerStack([
            new PlayerEventsController(),
            new PlayerController(),
            new PlayerCameraController(),
            new PlayerUIController()
        ]);

        this.link = new EntityControllerLink(ctx, this.entity, stack);
    }

    update(dt) {
        this.link.update(dt);
    }

    dispose() {
        this.link.dispose();
        this.entity.destroy();
    }
}

module.exports = {PlayerEntityController};