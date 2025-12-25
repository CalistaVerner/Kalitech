// FILE: Scripts/main.js
// Author: Calista Verner
"use strict";

const worldMod = require("./world/main.world.js");
const bootMod = require("./world/main.bootstrap.js");

exports.meta = {
    id: "kalitech.world.main",
    version: "1.1.0",
    apiMin: "0.1.0",
    name: "Main World Entrypoint"
};

exports.world = worldMod.world;
exports.bootstrap = bootMod.bootstrap;

class MainWorldEntrypoint {
    constructor(worldModule, bootModule) {
        this.world = this._instantiate(worldModule, "world");
        this.boot = this._instantiate(bootModule, "bootstrap");
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
            engine.log().error("[main] " + where + " failed: " + e);
            throw e;
        }
    }

    init(apiOrCtx) {
        this._call(this.boot, "init", apiOrCtx, "boot.init");
        this.state.started = true;
    }

    update(tpfOrCtx) {
        this._call(this.boot, "update", tpfOrCtx, "boot.update");
    }

    destroy(reason) {
        try {
            const fn = this.boot && this.boot.destroy;
            if (fn) fn.call(this.boot, reason);
        } catch (e) {
            engine.log().error("[main] boot.destroy failed: " + e);
        }
    }

    serialize() {
        let bootState = null;
        let worldState = null;

        try {
            if (this.boot && this.boot.serialize) bootState = this.boot.serialize();
        } catch (_) {}

        try {
            if (this.world && this.world.serialize) worldState = this.world.serialize();
        } catch (_) {}

        return { started: this.state.started, boot: bootState, world: worldState };
    }

    deserialize(restored) {
        if (restored && typeof restored === "object") {
            this.state = deepMerge(this.state, restored);

            try {
                if (this.boot && this.boot.deserialize) this.boot.deserialize(restored.boot);
            } catch (_) {}

            try {
                if (this.world && this.world.deserialize) this.world.deserialize(restored.world);
            } catch (_) {}
        }
    }
}

exports.create = function () {
    return new MainWorldEntrypoint(worldMod, bootMod);
};