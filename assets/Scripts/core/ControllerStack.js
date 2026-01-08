"use strict";

const {EntityController} = require("./EntityController.js");

class ControllerStack extends EntityController {
    constructor(controllers) {
        super();
        this.controllers = controllers || [];
    }

    bind(ctx, entity) {
        super.bind(ctx, entity);
        for (let i = 0; i < this.controllers.length; i++) {
            this.controllers[i].bind(ctx, entity);
        }
        return this;
    }

    // ВАЖНО: НЕ трогаем детей тут. Пусть стартуют сами при первом _tick().
    onStart() {
    }

    onUpdate(dt) {
        for (let i = 0; i < this.controllers.length; i++) {
            this.controllers[i]._tick(dt); // _tick гарантирует _start перед onUpdate
        }
    }

    onStop() {
        for (let i = this.controllers.length - 1; i >= 0; i--) {
            this.controllers[i]._shutdown();
        }
    }
}

module.exports = {ControllerStack};