// FILE: Scripts/player/PlayerController.js
// Author: Calista Verner
"use strict";

const MovementSystem = require("./systems/MovementSystem.js");
const ShootSystem    = require("./systems/ShootSystem.js");
const InputRouter    = require("./systems/InputRouter.js");

const MOVEMENT_CFG_JSON = "data/player/movement.config.json";

function isPlainObj(x) {
    if (!x || typeof x !== "object") return false;
    const p = Object.getPrototypeOf(x);
    return p === Object.prototype || p === null;
}

function deepMerge(dst, src) {
    if (!isPlainObj(src)) return dst;
    const out = isPlainObj(dst) ? dst : Object.create(null);
    const keys = Object.keys(src);
    for (let i = 0; i < keys.length; i++) {
        const k = keys[i];
        const sv = src[k];
        const dv = out[k];
        if (isPlainObj(sv) && isPlainObj(dv)) out[k] = deepMerge(dv, sv);
        else if (isPlainObj(sv)) out[k] = deepMerge(Object.create(null), sv);
        else out[k] = sv;
    }
    return out;
}

function readTextAsset(path) {
    try {
        const a = engine.assets && engine.assets();
        if (a) {
            if (a.readText) return a.readText(path);
            if (a.text) return a.text(path);
            if (a.getText) return a.getText(path);
        }
    } catch (_) {}
    try {
        // fallback if your runtime exposes fs-like API
        const fs = engine.fs && engine.fs();
        if (fs && fs.readText) return fs.readText(path);
    } catch (_) {}
    return null;
}

function readJsonAsset(path, fb) {
    const txt = readTextAsset(path);
    if (!txt) return fb || Object.create(null);
    try {
        const obj = JSON.parse(String(txt));
        return isPlainObj(obj) ? obj : (fb || Object.create(null));
    } catch (_) {
        return fb || Object.create(null);
    }
}

function resolveMovementPath(rootCfg, movCfg) {
    // Highest priority: explicit path in cfg
    try {
        if (movCfg && typeof movCfg.configPath === "string" && movCfg.configPath.length > 0) return movCfg.configPath;
    } catch (_) {}
    try {
        if (rootCfg && typeof rootCfg.movementConfigPath === "string" && rootCfg.movementConfigPath.length > 0) return rootCfg.movementConfigPath;
    } catch (_) {}
    return MOVEMENT_CFG_JSON;
}

class PlayerController {
    constructor(playerOrCfg) {
        const isPlayer = !!(playerOrCfg && typeof playerOrCfg === "object" && (playerOrCfg.cfg || playerOrCfg.getCfg || playerOrCfg.ctx));
        const player = isPlayer ? playerOrCfg : null;
        const rootCfg = isPlayer ? (player.cfg || {}) : (playerOrCfg || {});

        // legacy behavior: allow movement config to be in rootCfg.movement
        const movOverrides = (rootCfg && rootCfg.movement) || Object.create(null);

        // load JSON config (like camera.config.json)
        const movPath = resolveMovementPath(rootCfg, movOverrides);
        const movFromJson = readJsonAsset(movPath, Object.create(null));

        // effective movement cfg = JSON base + overrides from rootCfg.movement
        // (overrides win; deep merge keeps nested sections like speed/air/jump/ground)
        const movCfg = deepMerge(deepMerge(Object.create(null), movFromJson), (isPlainObj(movOverrides) ? movOverrides : Object.create(null)));

        this.player = player;
        this.enabled = (movCfg.enabled !== undefined) ? !!movCfg.enabled : true;

        // legacy ids kept for logs/debug only
        this.bodyId = 0;

        // keep for hot reload / inspection
        this._movementCfgPath = movPath;
        this._movementCfg = movCfg;

        // systems (now receive JSON cfg)
        this.input = new InputRouter(movCfg);
        this.movement = new MovementSystem(movCfg);
        this.shoot = new ShootSystem(rootCfg);

        this._debugEvery = ((movCfg.debug && movCfg.debug.everyFrames) | 0) || 60;
        this._debugOn = (movCfg.debug && movCfg.debug.enabled) !== undefined ? !!movCfg.debug.enabled : true;
        this._f = 0;
    }

    // optional: reload JSON at runtime (e.g., on hot-reload key)
    reloadMovementConfig() {
        const fresh = readJsonAsset(this._movementCfgPath || DATA_CONFIG.materials.json(), Object.create(null));
        const overrides = (this.player && this.player.cfg && this.player.cfg.movement) ? this.player.cfg.movement : Object.create(null);
        this._movementCfg = deepMerge(deepMerge(Object.create(null), fresh), (isPlainObj(overrides) ? overrides : Object.create(null)));

        const cfg = this._movementCfg;
        if (cfg.enabled !== undefined) this.enabled = !!cfg.enabled;
        if (this.input && this.input.configure) this.input.configure(cfg);
        if (this.movement && this.movement.configure) this.movement.configure(cfg);

        this._debugEvery = ((cfg.debug && cfg.debug.everyFrames) | 0) || this._debugEvery || 60;
        this._debugOn = (cfg.debug && cfg.debug.enabled) !== undefined ? !!cfg.debug.enabled : this._debugOn;

        return this;
    }

    configure(cfg) {
        cfg = cfg || Object.create(null);
        if (cfg.enabled !== undefined) this.enabled = !!cfg.enabled;

        // allow runtime patching (still supports old style)
        this._movementCfg = deepMerge(deepMerge(Object.create(null), this._movementCfg || Object.create(null)), cfg);

        if (this.input && this.input.configure) this.input.configure(this._movementCfg);
        if (this.movement && this.movement.configure) this.movement.configure(this._movementCfg);
        if (this.shoot && this.shoot.configure) this.shoot.configure(cfg);

        // refresh debug knobs from effective cfg
        const mc = this._movementCfg;
        this._debugEvery = ((mc.debug && mc.debug.everyFrames) | 0) || this._debugEvery || 60;
        this._debugOn = (mc.debug && mc.debug.enabled) !== undefined ? !!mc.debug.enabled : this._debugOn;

        return this;
    }

    bind(ids) {
        // in new world we bind via player.body; keep bodyId only for debug
        if (ids == null && this.player) ids = this.player;

        try { this.bodyId = (ids && typeof ids.bodyId === "number") ? (ids.bodyId | 0) : (ids | 0); } catch (_) { this.bodyId = 0; }

        try { if (this.input) this.input.bind(); } catch (_) {}
        try { LOG.info("[player] bind bodyId=" + (this.bodyId | 0) + " movementCfg=" + (this._movementCfgPath || MOVEMENT_CFG_JSON)); } catch (_) {}
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
                    LOG.debug(
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
