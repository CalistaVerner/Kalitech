// FILE: Scripts/player/PlayerController.js
// Author: Calista Verner
"use strict";

const MovementSystem = require("./systems/MovementSystem.js");
const ShootSystem    = require("./systems/ShootSystem.js");
const InputRouter    = require("./systems/InputRouter.js");

class PlayerController {
    constructor(playerOrCfg) {
        const isPlayer = !!(playerOrCfg && typeof playerOrCfg === "object" && (playerOrCfg.cfg || playerOrCfg.getCfg || playerOrCfg.ctx));
        const player = isPlayer ? playerOrCfg : null;
        const rootCfg = isPlayer ? (player.cfg || {}) : (playerOrCfg || {});
        const movCfg = (rootCfg && rootCfg.movement) || rootCfg || {};

        this.player = player;
        this.enabled = (movCfg.enabled !== undefined) ? !!movCfg.enabled : true;

        this.bodyId = 0;

        // tiny orchestrator parts
        this.input = new InputRouter(movCfg);
        this.movement = new MovementSystem(movCfg);
        this.shoot = new ShootSystem(rootCfg);

        this._debugEvery = ((movCfg.debug && movCfg.debug.everyFrames) | 0) || 60;
        this._debugOn = (movCfg.debug && movCfg.debug.enabled) !== undefined ? !!movCfg.debug.enabled : true;
        this._f = 0;
    }

    configure(cfg) {
        cfg = cfg || {};
        if (cfg.enabled !== undefined) this.enabled = !!cfg.enabled;
        if (this.input && this.input.configure) this.input.configure(cfg);
        if (this.movement && this.movement.configure) this.movement.configure(cfg);
        if (this.shoot && this.shoot.configure) this.shoot.configure(cfg);
        return this;
    }

    _resolveBodyId(any) {
        const x = any && (any.bodyId !== undefined ? any.bodyId : any);
        if (typeof x === "number") return x | 0;

        if (x && typeof x === "object") {
            try {
                if (typeof x.id === "function") return (x.id() | 0);
                if (typeof x.getBodyId === "function") return (x.getBodyId() | 0);
                if (typeof x.bodyId === "number") return (x.bodyId | 0);
                if (typeof x.id === "number") return (x.id | 0);
            } catch (_) {}
        }
        return 0;
    }

    bind(ids) {
        if (ids == null && this.player) ids = this.player;
        this.bodyId = this._resolveBodyId(ids);

        try { if (this.input) this.input.bind(); } catch (_) {}
        try { engine.log().info("[player] bind bodyId=" + (this.bodyId | 0)); } catch (_) {}
        return this;
    }

    update(tpf, snap) {
        if (!this.enabled) return;

        // hot reload safe
        if (this.player) this.bodyId = (this.player.bodyId | 0);
        if (!(this.bodyId | 0)) return;

        const dom = this.player ? this.player.dom : null;

        // controller does not interpret input — just routes it
        const state = this.input.read(snap);

        // ✅ sync input into domain (single source of truth)
        if (dom && dom.input) {
            dom.input.ax = state.ax | 0;
            dom.input.az = state.az | 0;
            dom.input.run = !!state.run;
            dom.input.jump = !!state.jump;
            dom.input.lmbDown = !!state.lmbDown;
            dom.input.lmbJustPressed = !!state.lmbJustPressed;
        }

        // optional debug
        if (this._debugOn) {
            this._f = (this._f + 1) | 0;
            if ((this._f % (this._debugEvery | 0)) === 0) {
                try {
                    engine.log().info(
                        "[player][in] ax=" + state.ax + " az=" + state.az +
                        " run=" + (state.run ? "1" : "0") +
                        " jump=" + (state.jump ? "1" : "0") +
                        " lmb=" + (state.lmbDown ? "1" : "0") +
                        " lmbJP=" + (state.lmbJustPressed ? "1" : "0")
                    );
                } catch (_) {}
            }
        }

        // systems do all heavy logic
        // Shoot + movement consume dom.view (authoritative camera angles)
        this.shoot.update(tpf, this.bodyId | 0, dom, state);

        const yaw = (dom && dom.view) ? dom.view.yaw : state.yaw; // fallback
        this.movement.update(tpf, this.bodyId | 0, state, yaw);
    }

    warp(pos) {
        const bodyId = this.bodyId | 0;
        if (!bodyId || !pos) return;
        try { engine.physics().position(bodyId, pos); } catch (_) {}
    }

    warpXYZ(x, y, z) {
        this.warp({ x: +x || 0, y: +y || 0, z: +z || 0 });
    }

    getBodyId() { return this.bodyId | 0; }
}

module.exports = PlayerController;