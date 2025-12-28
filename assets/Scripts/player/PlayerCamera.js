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

        // --- cyberpunk dynamics defaults (safe + subtle) ---
        dynamics: {
            bob: {
                walkFreq: 7.2,
                runFreq: 10.2,
                walkAmpY: 0.030,
                runAmpY: 0.060,
                walkAmpX: 0.020,
                runAmpX: 0.040,
                smooth: 14.0
            },
            sway: {
                yawMul: 0.045,
                pitchMul: 0.035,
                smooth: 18.0
            },
            handheld: {
                amp: 0.006,
                freq: 1.2,
                smooth: 6.0
            },
            spring: {
                stiffness: 55.0,
                damping: 11.0
            },
            kick: {
                stiffness: 45.0,
                damping: 10.0
            },
            fov: {
                enabled: true,
                runAdd: 8.0,
                smooth: 10.0
            }
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
    // отдаём orchestrator только ожидаемые секции
    return {
        debug: cfg.debug,
        keys:  cfg.keys,
        look:  cfg.look,
        free:  cfg.free,
        first: cfg.first,
        third: cfg.third,
        top:   cfg.top,
        dynamics: cfg.dynamics
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

    // ✅ gameplay hooks (forwarded into orchestrator)
    onJump(strength) {
        try { if (camModes && typeof camModes.onJump === "function") camModes.onJump(strength); } catch (_) {}
    }
    onLand(strength) {
        try { if (camModes && typeof camModes.onLand === "function") camModes.onLand(strength); } catch (_) {}
    }

    update(tpf, snap) {
        if (!this.ready) return;

        this.bodyId = this.player.bodyId | 0;
        camModes.attachTo(this.bodyId);

        camModes.update(tpf, snap);
    }

    /**
     * Copies the authoritative view (smoothed yaw/pitch) from CameraOrchestrator into PlayerDomain.
     * This is the single source of truth for aiming/movement direction for all subsystems.
     */
    syncDomain(dom) {
        if (!dom) return;

        try {
            if (camModes && camModes.look) {
                dom.view.yaw = +camModes.look._yawS || 0;
                dom.view.pitch = +camModes.look._pitchS || 0;
            }
        } catch (_) {}

        try { dom.view.type = String(camModes.type || this.type || "third"); }
        catch (_) { dom.view.type = this.type || "third"; }
    }

    destroy() {
        this.ready = false;
        try { camModes.attachTo(0); camModes.setType("free"); } catch (_) {}
        this.enableGameplayMouseGrab(false);
    }
}

module.exports = PlayerCamera;