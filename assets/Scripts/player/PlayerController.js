// FILE: Scripts/player/PlayerController.js
// Author: Calista Verner
"use strict";

const U = require("./util.js");

const MovementSystem = require("./systems/MovementSystem.js");
const ShootSystem = require("./systems/ShootSystem.js");
const InputRouter = require("./systems/InputRouter.js");

const MOVEMENT_CFG_JSON = "data/player/movement.config.json";

function readTextAsset(path) {
    const a = engine.assets && engine.assets();
    if (a) {
        if (a.readText) return a.readText(path);
        if (a.text) return a.text(path);
        if (a.getText) return a.getText(path);
    }
    const fs = engine.fs && engine.fs();
    if (fs && fs.readText) return fs.readText(path);
    return null;
}

function readJsonAsset(path, fb) {
    const txt = readTextAsset(path);
    if (!txt) {
        if (typeof LOG !== "undefined" && LOG && LOG.warn) LOG.warn("[player] movement config asset missing: " + path + " (using defaults/overrides)");
        return fb || Object.create(null);
    }
    try {
        const obj = JSON.parse(String(txt));
        return U.isPlainObj(obj) ? obj : (fb || Object.create(null));
    } catch (e) {
        if (typeof LOG !== "undefined" && LOG && LOG.error) LOG.error("[player] movement config JSON parse failed: " + path + " err=" + U.errStr(e));
        throw e;
    }
}

function resolveMovementPath(rootCfg, movCfg) {
    if (movCfg && typeof movCfg.configPath === "string" && movCfg.configPath.length > 0) return movCfg.configPath;
    if (rootCfg && typeof rootCfg.movementConfigPath === "string" && rootCfg.movementConfigPath.length > 0) return rootCfg.movementConfigPath;
    return MOVEMENT_CFG_JSON;
}

class PlayerController {
    constructor(player) {
        this.player = player;

        const rootCfg = (player && player.cfg) ? player.cfg : {};
        const movOverrides = (rootCfg && rootCfg.movement) || Object.create(null);

        const movPath = resolveMovementPath(rootCfg, movOverrides);
        const movFromJson = readJsonAsset(movPath, Object.create(null));
        const movCfg = U.deepMerge(
            U.deepMerge(Object.create(null), movFromJson),
            (U.isPlainObj(movOverrides) ? movOverrides : Object.create(null))
        );

        this.enabled = (movCfg.enabled !== undefined) ? !!movCfg.enabled : true;

        this._movementCfgPath = movPath;
        this._movementCfg = movCfg;

        this.input = new InputRouter(movCfg);
        this.movement = new MovementSystem(movCfg);
        this.shoot = new ShootSystem(rootCfg);

        const ms = this.movement;
        LOG.info(
            "[player] controller movementCfg=" + (this._movementCfgPath || MOVEMENT_CFG_JSON) +
            " walk=" + ms.walkSpeed +
            " run=" + ms.runSpeed +
            " accel=" + ms.accel +
            " decel=" + ms.decel
        );

        this._dbg = { t: 0, every: 120 };
    }

    getMovementCfg() { return this._movementCfg || Object.create(null); }

    bind() {
        this.input.bind();
        return this;
    }

    update(frame) {
        if (!this.enabled) return;
        const p = this.player;
        if (!p || !frame) return;

        const body = p.body;
        if (!body) return;

        // read inputs into frame.input
        const st = this.input.read(frame);

        // lightweight diagnostics (helps detect when run/jump/axes not coming in)
        const dbg = this._dbg || (this._dbg = { t: 0, every: 120 });
        dbg.t = (dbg.t + 1) | 0;
        if ((dbg.t % dbg.every) === 0) {
            LOG.info("[player] input ax=" + st.ax + " az=" + st.az + " run=" + !!st.run + " jump=" + !!st.jump + " lmb=" + !!st.lmbDown);
        }

        // mirror into domain (single source for gameplay systems)
        const dom = p.dom;
        if (dom && dom.input) {
            dom.input.ax = st.ax | 0;
            dom.input.az = st.az | 0;
            dom.input.run = !!st.run;
            dom.input.jump = !!st.jump;
            dom.input.lmbDown = !!st.lmbDown;
            dom.input.lmbJustPressed = !!st.lmbJustPressed;
        }

        // rotate body to camera yaw (3rd/1st person “CDPR-like”)
        const yaw = (dom && dom.view) ? U.num(dom.view.yaw, 0) : (frame.view ? U.num(frame.view.yaw, 0) : 0);
        if (typeof body.yaw !== "function") {
            throw new Error("[player] body.yaw is not a function (physics wrapper mismatch)");
        }
        body.yaw(yaw);

        const bodyId = p.bodyId | 0;
        this.shoot.update(frame, bodyId);
        this.movement.update(frame, body, p.characterCfg);
    }
}

module.exports = PlayerController;