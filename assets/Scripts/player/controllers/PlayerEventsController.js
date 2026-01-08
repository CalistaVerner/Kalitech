// FILE: Scripts/player/controllers/PlayerEventsController.js
"use strict";

const {EntityController} = require("../../core/EntityController.js");
const PlayerEvents = require("../PlayerEvents.js");

class PlayerEventsController extends EntityController {
    constructor() {
        super();
        this.impl = null;
    }

    onStart() {
        this.impl = new PlayerEvents(this.entity);

        // spawn event (как у тебя было)
        if (this.impl && typeof this.impl.emit === "function") {
            this.impl.emit("player.spawn", {
                entityId: this.entity.entityId,
                bodyId: this.entity.bodyId
            });
        }

        // state export (как было)
        const ctx = this.entity.ctx;
        if (ctx && typeof ctx.state === "function") {
            ctx.state().set("player", {
                alive: true,
                entityId: this.entity.entityId,
                surfaceId: this.entity.surfaceId,
                bodyId: this.entity.bodyId
            });
        }
    }

    onUpdate(dt) {
        if (this.impl && typeof this.impl.tick === "function") {
            this.impl.tick(this.entity.frame);
        }
    }

    onStop() {
        if (this.impl && typeof this.impl.destroy === "function") this.impl.destroy();
        this.impl = null;

        const ctx = this.entity.ctx;
        if (ctx && typeof ctx.state === "function") ctx.state().remove("player");
    }
}

module.exports = {PlayerEventsController};