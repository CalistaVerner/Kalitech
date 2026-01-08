"use strict";

const MovementSystem = require("./systems/MovementSystem.js");
const ShootSystem = require("./systems/ShootSystem.js");
const InputRouter = require("./systems/InputRouter.js");

const MOVEMENT_CFG_JSON = "data/player/movement.config.json";

function readJsonStrict(domains, path) {
    const assets = domains.assets;
    const txt = assets.readText(path);
    if (!txt) throw new Error("[player] movement config not found: " + path);
    const obj = JSON.parse(String(txt));
    if (!obj || typeof obj !== "object") throw new Error("[player] movement config must be JSON object: " + path);
    return obj;
}

function movementPath(rootCfg) {
    if (rootCfg && typeof rootCfg.movementConfigPath === "string" && rootCfg.movementConfigPath.length > 0) return rootCfg.movementConfigPath;
    return MOVEMENT_CFG_JSON;
}

class PlayerController {
    constructor(player) {
        this.player = player;

        this.enabled = true;
        this._movementCfg = Object.create(null);

        this.input = null;
        this.movement = null;
        this.shoot = null;
    }

    ensure() {
        this._ensureSystems();
        return this;
    }

    getMovementCfg() {
        return this._movementCfg;
    }

    _ensureSystems() {
        if (this.input && this.movement && this.shoot) return;

        const p = this.player;
        const rootCfg = p.cfg || Object.create(null);

        const movOverrides = (rootCfg.movement && typeof rootCfg.movement === "object") ? rootCfg.movement : null;
        const movCfg = movOverrides || readJsonStrict(p.d, movementPath(rootCfg));

        this.enabled = (movCfg.enabled !== undefined) ? !!movCfg.enabled : true;
        this._movementCfg = movCfg;

        this.input = new InputRouter(p.d.input, movCfg);
        this.movement = new MovementSystem(movCfg);
        this.shoot = new ShootSystem(p);
    }

    update(frame) {
        const p = this.player;
        if (!p.bodyAccess) return;

        this._ensureSystems();
        if (!this.enabled) return;

        this.input.read(frame);

        const yaw = +frame.view.yaw || 0;
        if (p.bodyAccess && typeof p.bodyAccess.yaw === "function") p.bodyAccess.yaw(yaw);

        this.shoot.update(frame, p.bodyId | 0);

        // MovementSystem читает frame.bodyAccess сам
        this.movement.update(frame, p.characterCfg);
    }

    destroy() {
        if (this.shoot) this.shoot.destroy();
        this.input = null;
        this.movement = null;
        this.shoot = null;
    }
}

module.exports = PlayerController;