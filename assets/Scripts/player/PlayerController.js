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
        LOG.warn("[player] movement config asset missing: " + path + " (using defaults/overrides)");
        return fb || Object.create(null);
    }
    const obj = JSON.parse(String(txt));
    return U.isPlainObj(obj) ? obj : (fb || Object.create(null));
}

function resolveMovementPath(rootCfg, movCfg) {
    if (movCfg && typeof movCfg.configPath === "string" && movCfg.configPath.length > 0) return movCfg.configPath;
    if (rootCfg && typeof rootCfg.movementConfigPath === "string" && rootCfg.movementConfigPath.length > 0) return rootCfg.movementConfigPath;
    return MOVEMENT_CFG_JSON;
}

class PlayerController {
    constructor(player) {
        if (!player) throw new Error("[player] PlayerController requires player");
        this.player = player;

        const rootCfg = player.cfg || Object.create(null);
        const movOverrides = rootCfg.movement || Object.create(null);

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

        // light debug, can be disabled by setting every=0
        this._dbg = { t: 0, every: 120 };
    }

    getMovementCfg() { return this._movementCfg; }

    bind() {
        this.input.bind();
        return this;
    }

    update(frame) {
        if (!this.enabled) return;
        if (!frame) return;

        const p = this.player;
        const body = p.body;
        if (!body) return;

        // Read input -> state
        const st = this.input.read(frame);

        // Optional diagnostics
        const dbg = this._dbg;
        if (dbg && dbg.every > 0) {
            dbg.t = (dbg.t + 1) | 0;
            if ((dbg.t % dbg.every) === 0) {
                LOG.info("[player] input ax=" + st.ax + " az=" + st.az + " run=" + !!st.run + " jump=" + !!st.jump + " lmb=" + !!st.lmbDown);
            }
        }

        // Mirror into domain (single source of truth)
        const dom = p.dom;
        if (dom && dom.input) {
            dom.input.ax = st.ax | 0;
            dom.input.az = st.az | 0;
            dom.input.run = !!st.run;
            dom.input.jump = !!st.jump;
            dom.input.lmbDown = !!st.lmbDown;
            dom.input.lmbJustPressed = !!st.lmbJustPressed;
        }

        // Rotate body to view yaw
        const yaw = (dom && dom.view)
            ? U.num(dom.view.yaw, 0)
            : (frame.view ? U.num(frame.view.yaw, 0) : 0);

        if (typeof body.yaw !== "function") {
            throw new Error("[player] body.yaw is not a function (physics wrapper mismatch)");
        }
        body.yaw(yaw);

        const bodyId = p.bodyId | 0;

        this.shoot.update(frame, bodyId);

        // ✅ MovementSystem already reads what it needs from frame/dom/body
        this.movement.update(frame, body);
    }
}

module.exports = PlayerController;