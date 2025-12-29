// Author: Calista Verner
"use strict";

const camModes = require("../Camera/CameraOrchestrator.js");

function readTextAsset(path) {
    // оставляем безопасный fallback, но без избыточных уровней
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

        free: { speed: 90, accel: 18, drag: 6.5 },

        first: { offset: { x: 0, y: 1.65, z: 0 } },

        third: { distance: 3.4, height: 1.55, side: 0.25, zoomSpeed: 1.0 },
        top: { height: 18, panSpeed: 14, zoomSpeed: 2, pitch: -Math.PI * 0.49 }
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
        keys: cfg.keys,
        look: cfg.look,
        free: cfg.free,
        first: cfg.first,
        third: cfg.third,
        top: cfg.top,
        dynamics: cfg.dynamics
    };
}

function num(v, fb) {
    const n = +v;
    return Number.isFinite(n) ? n : fb;
}

class PlayerCamera {
    constructor(player) {
        this.player = player;
        this.ready = false;

        // runtime cache
        this._attachedBodyId = 0;
        this._lastEyeHeight = NaN;
        this._lastType = null;

        // load cfg once
        this.cfg = merge(defaults(), DATA_CONFIG.camera.json());
        this.type = this.cfg.type || "third";

        // configure orchestrator ONCE
        camModes.configure(shallowPick(this.cfg));
        camModes.setType(this.type);

        this._lastType = this.type;
        this.ready = true;
    }

    _attachTo(bodyId) {
        bodyId |= 0;
        if (!bodyId || bodyId === this._attachedBodyId) return;
        this._attachedBodyId = bodyId;
        camModes.attachTo(bodyId);
    }

    attach() {
        this._attachTo(this.player ? (this.player.bodyId | 0) : 0);
        return this;
    }

    enableGameplayMouseGrab(enable) {
        enable = !!enable;
        // один try/catch достаточно
        try { INP.grabMouse(enable); }
        catch (_) {
            try { INP.cursorVisible(!enable); } catch (_) {}
        }
    }

    onJump(strength) {
        if (camModes && typeof camModes.onJump === "function") camModes.onJump(strength);
    }

    onLand(strength) {
        if (camModes && typeof camModes.onLand === "function") camModes.onLand(strength);
    }

    _calcEyeHeight() {
        const p = this.player;
        const ch = (p && p.cfg && p.cfg.character) ? p.cfg.character : null;

        // 1) прямое значение
        if (ch && ch.eyeHeight != null) {
            const eh = num(ch.eyeHeight, 0);
            if (eh > 0.5) return eh;
        }

        // 2) от роста (character.height -> spawn.height -> fallback)
        const h = (ch && ch.height != null)
            ? num(ch.height, 1.80)
            : (p && p.cfg && p.cfg.spawn && p.cfg.spawn.height != null)
                ? num(p.cfg.spawn.height, 1.80)
                : 1.80;

        // AAA: глаза чуть ниже макушки
        const eye = Math.min(h * 0.92, h - 0.08);
        return Math.max(1.20, eye);
    }

    _applyFirstPersonOffsetIfChanged() {
        const eye = this._calcEyeHeight();

        // epsilon чтобы не дергать конфиг из-за микрофлуктуаций
        const prev = this._lastEyeHeight;
        if (Number.isFinite(prev) && Math.abs(eye - prev) < 1e-3) return;

        this._lastEyeHeight = eye;

        if (!this.cfg.first) this.cfg.first = {};
        if (!this.cfg.first.offset) this.cfg.first.offset = { x: 0, y: eye, z: 0 };
        else this.cfg.first.offset.y = eye;

        // ВАЖНО: configure только при изменениях (иначе spam "camera configured ...")
        camModes.configure(shallowPick(this.cfg));
    }

    _applyTypeIfChanged() {
        const type = String((this.cfg && this.cfg.type) || this.type || "third");
        if (type === this._lastType) return;
        this._lastType = type;
        this.type = type;
        camModes.setType(type);
    }

    update(tpf, snap) {
        if (!this.ready) return;

        const bodyId = this.player ? (this.player.bodyId | 0) : 0;

        // attach только при смене id
        this._attachTo(bodyId);

        // тип — только если реально поменялся
        this._applyTypeIfChanged();

        // first-person offset — только если изменился eyeHeight
        this._applyFirstPersonOffsetIfChanged();

        camModes.update(tpf, snap);
    }

    syncDomain(dom) {
        if (!dom) return;

        const look = camModes ? camModes.look : null;
        if (look) {
            dom.view.yaw = +look._yawS || 0;
            dom.view.pitch = +look._pitchS || 0;
        }
        dom.view.type = String((camModes && camModes.type) || this.type || "third");
    }

    destroy() {
        this.ready = false;
        this._attachedBodyId = 0;

        try {
            camModes.attachTo(0);
            camModes.setType("free");
        } catch (_) {}

        this.enableGameplayMouseGrab(false);
    }
}

module.exports = PlayerCamera;