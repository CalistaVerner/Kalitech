"use strict";

const U = require("./util.js");

const MovementSystem = require("./systems/MovementSystem.js");
const ShootSystem = require("./systems/ShootSystem.js");
const InputRouter = require("./systems/InputRouter.js");

const MOVEMENT_CFG_JSON = "data/player/movement.config.json";

function readJsonStrict(player, path) {
    const E = player && player.engine;
    if (!E) throw new Error("[player] engine not ready (call player.init() first)");

    const assets = E.assets && E.assets();
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
        if (!player) throw new Error("[player] PlayerController requires player");
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
        if (!this.input) return this;
        this.input.bind();
        return this;
    }

    _ensureSystems() {
        if (this.input && this.movement && this.shoot) return;

        const rootCfg = this.player.cfg || Object.create(null);
        const movOverrides = (rootCfg.movement && U.isPlainObj(rootCfg.movement)) ? rootCfg.movement : null;
        const movCfg = movOverrides || readJsonStrict(this.player, movementPath(rootCfg));

        this.enabled = (movCfg.enabled !== undefined) ? !!movCfg.enabled : true;
        this._movementCfg = movCfg;

        this.input = new InputRouter(movCfg);
        this.movement = new MovementSystem(movCfg);
        this.shoot = new ShootSystem(rootCfg);
    }

    update(frame) {
        if (!frame || !this.player || !this.player.body) return;

        this._ensureSystems();
        if (!this.enabled) return;

        const st = this.input.read(frame);

        const dom = this.player.dom;
        if (dom && dom.input) {
            dom.input.ax = st.ax | 0;
            dom.input.az = st.az | 0;
            dom.input.run = !!st.run;
            dom.input.jump = !!st.jump;
            dom.input.lmbDown = !!st.lmbDown;
            dom.input.lmbJustPressed = !!st.lmbJustPressed;
        }

        const yaw = (dom && dom.view) ? U.num(dom.view.yaw, 0) : (frame.view ? U.num(frame.view.yaw, 0) : 0);

        if (typeof this.player.body.yaw !== "function") throw new Error("[player] body.yaw(yaw) required");
        this.player.body.yaw(yaw);

        this.shoot.update(frame, this.player.bodyId | 0);
        this.movement.update(frame, this.player.body);
    }
}

module.exports = PlayerController;