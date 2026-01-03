// FILE: Scripts/main.js
// Author: Calista Verner
"use strict";

const worldMod = require("./environment");
exports.meta = {
    id: "kalitech.world.main",
    version: "1.1.0",
    apiMin: "0.1.0",
    name: "Main World Entrypoint"
};

exports.world = worldMod.world;
//exports.bootstrap = bootMod.bootstrap;

class MainWorldEntrypoint {
    constructor(worldModule) {
        this.world = this._instantiate(worldModule, "world");
        this.state = { started: false };
    }

    _instantiate(mod, legacyKey) {
        try {
            if (mod && mod.create) return mod.create();
        } catch (_) {}

        try {
            if (mod && mod[legacyKey]) return mod[legacyKey];
        } catch (_) {}

        return {};
    }

    _call(obj, method, arg, where) {
        try {
            const fn = obj && obj[method];
            if (fn) return fn.call(obj, arg);
            return undefined;
        } catch (e) {
            LOG.error("[main] " + where + " failed: " + e);
            throw e;
        }
    }

    init(apiOrCtx) {
        this.state.started = true;
    }

    update(tpfOrCtx) {

        const p = ctx.state().get("player");
        if (p) p.update(tpf);

        const PH = EVENTS.scope("engine.physics");

        PH.once("postStep", () => {
            LOG.info('Player Snap to ground!');
            player.snapToGround(); // твоя функция/логика
        });

        // слушаем всё по физике
        const t = EVENTS.onPattern("engine.physics.**", (topic, payload) => {
            LOG.info("[EVT]", topic, payload);
        });

// или точечно
        EVENTS.on("engine.surface.registered", (p) => LOG.info("surface+", p));

// снять подписку
// EVENTS.off("engine.physics.**", t);


    }

    destroy(reason) {

    }

    serialize() {
        let worldState = null;


        return { started: this.state.started, world: worldState };
    }

    deserialize(restored) {
        if (restored && typeof restored === "object") {
            this.state = deepMerge(this.state, restored);

            try {
                if (this.world && this.world.deserialize) this.world.deserialize(restored.world);
            } catch (_) {}
        }
    }
}

exports.create = function () {
    return new MainWorldEntrypoint(worldMod);
};