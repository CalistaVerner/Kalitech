"use strict";

const U = require("./util.js");

const MovementSystem = require("./systems/MovementSystem.js");
const ShootSystem = require("./systems/ShootSystem.js");
const InputRouter = require("./systems/InputRouter.js");

const MOVEMENT_CFG_JSON = "data/player/movement.config.json";

function readJsonStrict(domains, path) {
    const assets = domains.assets;
    if (!assets || typeof assets.readText !== "function") throw new Error("[player] engine.assets().readText required");

    const txt = assets.readText(path);
    if (!txt) throw new Error("[player] movement config not found: " + path);

    const obj = JSON.parse(String(txt));
    if (!U.isPlainObj(obj)) throw new Error("[player] movement config must be a JSON object: " + path);
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

    getMovementCfg() {
        return this._movementCfg;
    }

    bind() {
        if (this.input) this.input.bind();
        return this;
    }

    _ensureSystems() {
        if (this.input && this.movement && this.shoot) return;

        const rootCfg = this.player.cfg || Object.create(null);
        const movOverrides = (rootCfg.movement && U.isPlainObj(rootCfg.movement)) ? rootCfg.movement : null;
        const movCfg = movOverrides || readJsonStrict(this.player.d, movementPath(rootCfg));

        this.enabled = (movCfg.enabled !== undefined) ? !!movCfg.enabled : true;
        this._movementCfg = movCfg;

        this.input = new InputRouter(this.player.d.input, movCfg);
        this.movement = new MovementSystem(movCfg);
        this.shoot = new ShootSystem(this.player, rootCfg);
    }

    update(frame) {
        const p = this.player;
        if (!frame || !p || !p.body) return;

        this._ensureSystems();
        if (!this.enabled) return;

        const st = this.input.read(frame);

        const dom = p.dom;
        dom.input.ax = st.ax | 0;
        dom.input.az = st.az | 0;
        dom.input.run = !!st.run;
        dom.input.jump = !!st.jump;
        dom.input.lmbDown = !!st.lmbDown;
        dom.input.lmbJustPressed = !!st.lmbJustPressed;
        dom.input.wheel = st.wheel;

        const yaw = dom.view ? U.num(dom.view.yaw, 0) : 0;

        if (typeof p.body.yaw !== "function") throw new Error("[player] body.yaw(yaw) required");
        p.body.yaw(yaw);

        this.shoot.update(frame, p.bodyId | 0);
        this.movement.update(frame, p.body, p.characterCfg);
    }

    destroy() {
        if (this.shoot) this.shoot.destroy();
        this.input = null;
        this.movement = null;
        this.shoot = null;
    }
}

module.exports = PlayerController;