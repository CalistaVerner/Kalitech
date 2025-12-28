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

        // legacy ids kept for logs/debug only
        this.bodyId = 0;

        // systems
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

    bind(ids) {
        // in new world we bind via player.body; keep bodyId only for debug
        if (ids == null && this.player) ids = this.player;

        try { this.bodyId = (ids && typeof ids.bodyId === "number") ? (ids.bodyId | 0) : (ids | 0); } catch (_) { this.bodyId = 0; }

        try { if (this.input) this.input.bind(); } catch (_) {}
        try { engine.log().info("[player] bind bodyId=" + (this.bodyId | 0)); } catch (_) {}
        return this;
    }

    update(tpf, snap) {
        if (!this.enabled) return;

        const p = this.player;
        if (!p) return;

        // hot reload safe: refresh id + wrapper existence handled by Player (index.js)
        this.bodyId = (p.bodyId | 0);
        const body = p.body; // ✅ PHYS.ref(bodyId)
        if (!body) return;

        const dom = p.dom;

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

        // ✅ optional: rotate player body to match yaw (if you want)
        // (InputRouter only computes yaw/pitch; body rotation lives here)
        try { if (dom && dom.view) body.yaw(dom.view.yaw); } catch (_) {}

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
        this.movement.update(tpf, body, state, yaw);
    }

    warp(pos) {
        const p = this.player;
        if (!p || !p.body || !pos) return;
        try { p.body.warp(pos); } catch (_) {}
    }

    warpXYZ(x, y, z) {
        this.warp({ x: +x || 0, y: +y || 0, z: +z || 0 });
    }
}

module.exports = PlayerController;