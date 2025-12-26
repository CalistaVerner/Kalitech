// FILE: Scripts/player/PlayerCamera.js
// Author: Calista Verner
"use strict";

const camModes = require("../Camera/CameraOrchestrator.js");

const CAMERA_CFG_JSON = "data/camera/camera.config.json";

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
        const io = engine.io && engine.io();
        if (io && io.readText) return io.readText(path);
    } catch (_) {}

    return null;
}

function loadJson(path) {
    const txt = readTextAsset(path);
    if (txt) return JSON.parse(txt);
    try { return require(path); } catch (_) {}
    return {};
}

function defaults() {
    return {
        type: "third",
        debug: { enabled: true, everyFrames: 60 },
        keys: { free: "F1", first: "F2", third: "F3", top: "F4" },
        look: {
            sensitivity: 0.002,
            smoothing: 0.12,
            pitchLimit: Math.PI * 0.49,
            invertX: false,
            invertY: false
        },
        free:  { speed: 90, accel: 18, drag: 6.5 },
        first: { offset: { x: 0, y: 1.65, z: 0 } },
        third: { distance: 3.4, height: 1.55, side: 0.25, zoomSpeed: 1.0 },
        top:   { height: 18, panSpeed: 14, zoomSpeed: 2, pitch: -Math.PI * 0.49 }
    };
}

function merge(dst, src) {
    if (!src) return dst;
    for (const k in src) {
        const v = src[k];
        if (v && typeof v === "object" && !Array.isArray(v)) dst[k] = merge(dst[k] || {}, v);
        else dst[k] = v;
    }
    return dst;
}

function shallowPick(cfg) {
    // отдаем orchestrator только ожидаемые секции
    return {
        debug: cfg.debug,
        keys:  cfg.keys,
        look:  cfg.look,
        free:  cfg.free,
        first: cfg.first,
        third: cfg.third,
        top:   cfg.top
    };
}

class PlayerCamera {
    constructor(player) {
        this.player = player;
        this.bodyId = 0;

        // load + apply once
        const raw = loadJson(CAMERA_CFG_JSON);
        this.cfg = merge(defaults(), raw);
        this.type = this.cfg.type || "third";

        camModes.configure(shallowPick(this.cfg));
        camModes.setType(this.type);

        // ✅ single log: config applied
        const look = this.cfg.look || {};
        const keys = this.cfg.keys || {};
        engine.log().info(
            "[camera] cfg applied path=" + CAMERA_CFG_JSON +
            " type=" + this.type +
            " sens=" + (+look.sensitivity || 0) +
            " smooth=" + (+look.smoothing || 0) +
            " invertX=" + (!!look.invertX ? "1" : "0") +
            " invertY=" + (!!look.invertY ? "1" : "0") +
            " keys=" + JSON.stringify(keys)
        );

        this.ready = true;
    }

    attach() {
        this.bodyId = this.player.bodyId | 0;
        camModes.attachTo(this.bodyId);
        return this;
    }

    enableGameplayMouseGrab(enable) {
        try { engine.input().grabMouse(!!enable); }
        catch (_) { try { engine.input().cursorVisible(!enable); } catch (_) {} }
    }

    update(tpf, snap) {
        if (!this.ready) return;

        this.bodyId = this.player.bodyId | 0;
        camModes.attachTo(this.bodyId);

        // временный фикс: инверсия на входе (пока не поправим orchestrator)
        const look = this.cfg.look;
        if (snap) {
            //if (look.invertX) snap.dx = -(snap.dx || 0);
            //if (look.invertY) snap.dy = -(snap.dy || 0);
        }

        camModes.update(tpf, snap);
    }

    destroy() {
        this.ready = false;
        try { camModes.attachTo(0); camModes.setType("free"); } catch (_) {}
        this.enableGameplayMouseGrab(false);
    }
}

module.exports = PlayerCamera;