// Author: Calista Verner
"use strict";

const camModes = require("../Camera/CameraOrchestrator.js");

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

        // ✅ first-person offset будет пересчитан от капсулы (eyeHeight)
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

        this.cfg = merge(defaults(), DATA_CONFIG.camera.json());
        this.type = this.cfg.type || "third";

        camModes.configure(shallowPick(this.cfg));
        camModes.setType(this.type);

        this.ready = true;
    }

    attach() {
        this.bodyId = this.player.bodyId | 0;
        camModes.attachTo(this.bodyId);
        return this;
    }

    enableGameplayMouseGrab(enable) {
        try { INP.grabMouse(!!enable); }
        catch (_) { try { INP.cursorVisible(!enable); } catch (_) {} }
    }

    onJump(strength) {
        try { if (camModes && typeof camModes.onJump === "function") camModes.onJump(strength); } catch (_) {}
    }
    onLand(strength) {
        try { if (camModes && typeof camModes.onLand === "function") camModes.onLand(strength); } catch (_) {}
    }

    _calcEyeHeight() {
        try {
            const p = this.player;

            // приоритет: cfg.character.eyeHeight
            const ch = (p && p.cfg && p.cfg.character) ? p.cfg.character : null;
            if (ch && ch.eyeHeight != null) {
                const eh = +ch.eyeHeight;
                if (Number.isFinite(eh) && eh > 0.5) return eh;
            }

            // иначе: от height (character.height -> spawn.height -> fallback)
            const h =
                (ch && ch.height != null) ? +ch.height :
                    (p && p.cfg && p.cfg.spawn && p.cfg.spawn.height != null) ? +p.cfg.spawn.height :
                        1.80;

            const hh = (Number.isFinite(h) && h > 0.5) ? h : 1.80;

            // AAA: глаза чуть ниже макушки
            const eye = Math.min(hh * 0.92, hh - 0.08);
            return Math.max(1.20, eye);
        } catch (_) {
            return 1.65;
        }
    }

    _applyFirstPersonOffset() {
        const eye = this._calcEyeHeight();

        if (!this.cfg.first) this.cfg.first = {};
        if (!this.cfg.first.offset) this.cfg.first.offset = { x: 0, y: eye, z: 0 };
        else this.cfg.first.offset.y = eye;

        // важно: orchestration должен получить новый offset
        try { camModes.configure(shallowPick(this.cfg)); } catch (_) {}
    }

    update(tpf, snap) {
        if (!this.ready) return;

        this.bodyId = this.player.bodyId | 0;
        camModes.attachTo(this.bodyId);

        // ✅ камера “прикручена” к телу: правильный eyeHeight всегда соответствует капсуле
        this._applyFirstPersonOffset();

        camModes.update(tpf, snap);
    }

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