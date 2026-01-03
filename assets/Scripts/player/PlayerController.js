// FILE: Scripts/player/PlayerController.js
// Author: Calista Verner
"use strict";

const U = require("./util.js");

const MovementSystem = require("./systems/MovementSystem.js");
const ShootSystem = require("./systems/ShootSystem.js");
const InputRouter = require("./systems/InputRouter.js");

const MOVEMENT_CFG_JSON = "data/player/movement.config.json";

function readJsonAssetStrict(path) {
    const assets = engine.assets && engine.assets();
    if (!assets || typeof assets.readText !== "function") throw new Error("[player] engine.assets().readText required");
    const txt = assets.readText(path);
    if (!txt) throw new Error("[player] movement config not found: " + path);
    const obj = JSON.parse(String(txt));
    if (!U.isPlainObj(obj)) throw new Error("[player] movement config must be a JSON object: " + path);
    return obj;
}

function resolveMovementPath(rootCfg) {
    if (rootCfg && typeof rootCfg.movementConfigPath === "string" && rootCfg.movementConfigPath.length > 0) return rootCfg.movementConfigPath;
    return MOVEMENT_CFG_JSON;
}

class PlayerController {
    constructor(player) {
        if (!player) throw new Error("[player] PlayerController requires player");
        this.player = player;

        const rootCfg = player.cfg || Object.create(null);
        const movOverrides = (rootCfg.movement && U.isPlainObj(rootCfg.movement)) ? rootCfg.movement : null;

        // Strict pipeline:
        // - If movement is an object -> use it directly
        // - Else load JSON by path (movementConfigPath)
        const movCfg = movOverrides || readJsonAssetStrict(resolveMovementPath(rootCfg));

        this.enabled = (movCfg.enabled !== undefined) ? !!movCfg.enabled : true;

        this._movementCfg = movCfg;

        this.input = new InputRouter(movCfg);
        this.movement = new MovementSystem(movCfg);
        this.shoot = new ShootSystem(rootCfg);

        const ms = this.movement;
        if (LOG && LOG.info) LOG.info("[player] movement walk=" + ms.walkSpeed + " run=" + ms.runSpeed + " accel=" + ms.accel + " decel=" + ms.decel);
    }

    getMovementCfg() { return this._movementCfg; }

    bind() {
        this.input.bind();
        return this;
    }

    update(frame) {
        if (!this.enabled || !frame || !this.player.body) return;

        // Read input -> state
        const st = this.input.read(frame);

        // Mirror into domain (single source of truth)
        if (this.player.dom && this.player.dom.input) {
            this.player.dom.input.ax = st.ax | 0;
            this.player.dom.input.az = st.az | 0;
            this.player.dom.input.run = !!st.run;
            this.player.dom.input.jump = !!st.jump;
            this.player.dom.input.lmbDown = !!st.lmbDown;
            this.player.dom.input.lmbJustPressed = !!st.lmbJustPressed;
        }

        // Rotate body to view yaw
        const yaw = (this.player.dom && this.player.dom.view)
            ? U.num(this.player.dom.view.yaw, 0)
            : (frame.view ? U.num(frame.view.yaw, 0) : 0);

        if (typeof this.player.body.yaw !== "function") throw new Error("[player] body.yaw(yaw) required");
        this.player.body.yaw(yaw);

        this.shoot.update(frame, this.player.bodyId | 0);
        this.movement.update(frame, this.player.body);
    }
}

module.exports = PlayerController;