"use strict";

const PlayerEvents = require("../PlayerEvents.js");

class PlayerEventsController {
    constructor() {
        this.ctx = null;
        this.entity = null;
        this.impl = null;
    }

    bind(ctx, entity) {
        this.ctx = ctx;
        this.entity = entity;
        return this;
    }

    onStart() {
        this.impl = new PlayerEvents(this.entity);

        if (this.impl && typeof this.impl.emit === "function") {
            this.impl.emit("player.spawn", {
                entityId: this.entity.entityId,
                bodyId: this.entity.bodyId
            });
        }

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