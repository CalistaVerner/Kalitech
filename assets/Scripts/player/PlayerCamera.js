// FILE: Scripts/player/PlayerCamera.js
// Author: Calista Verner
"use strict";

const U = require("./util.js");
const camModes = require("../Camera/CameraOrchestrator.js");

function defaults() {
    return {
        type: "third",
        debug: { enabled: false, everyFrames: 120 },

        keys: { free: "F1", first: "F2", third: "F3", top: "F4" },

        look: {
            sensitivity: 0.002,
            smoothing: 0.12,

            // filter raw mouse deltas before camera sees them
            mouseFilter: 18.0,

            // smooth yaw/pitch published to domain
            outputSmooth: 14.0,

            pitchLimit: Math.PI * 0.49,
            invertX: false,
            invertY: false
        },

        dynamics: {
            bob: { walkFreq: 7.2, runFreq: 10.2, walkAmpY: 0.030, runAmpY: 0.060, walkAmpX: 0.020, runAmpX: 0.040, smooth: 14.0 },
            sway: { yawMul: 0.045, pitchMul: 0.035, smooth: 18.0 },
            handheld: { amp: 0.006, freq: 1.2, smooth: 6.0 },
            spring: { stiffness: 55.0, damping: 11.0 },
            kick: { stiffness: 45.0, damping: 10.0 },
            fov: { enabled: true, runAdd: 8.0, smooth: 10.0 }
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

function expAlpha(dt, smooth) {
    smooth = U.num(smooth, 0);
    if (!(smooth > 0)) return 1;
    dt = U.num(dt, 0);
    if (!(dt > 0)) return 1;
    return 1 - Math.exp(-smooth * dt);
}

function lerp(a, b, t) { return a + (b - a) * t; }

function angDiff(a, b) {
    let d = b - a;
    while (d > Math.PI) d -= Math.PI * 2;
    while (d < -Math.PI) d += Math.PI * 2;
    return d;
}

function angLerp(a, b, t) {
    return a + angDiff(a, b) * t;
}

class PlayerCamera {
    constructor(player) {
        this.player = player;

        this.cfg = merge(defaults(), (typeof DATA_CONFIG !== "undefined" && DATA_CONFIG.camera && DATA_CONFIG.camera.json) ? DATA_CONFIG.camera.json() : {});
        if (player && player.cfg && player.cfg.camera) this.cfg = merge(this.cfg, player.cfg.camera);

        this.type = String(this.cfg.type || "third");

        this._attachedBodyId = 0;
        this._configured = false;

        this._lastEye = -1;

        this._dxF = 0;
        this._dyF = 0;

        this._yawOut = 0;
        this._pitchOut = 0;
        this._outInit = false;

        this._warned = Object.create(null);
    }

    _warnOnce(key, msg) {
        if (this._warned[key]) return;
        this._warned[key] = true;
        if (typeof LOG !== "undefined" && LOG && LOG.warn) LOG.warn(msg);
    }

    enableGameplayMouseGrab(enable) {
        if (!INP) {
            this._warnOnce("no_inp", "[cam] INP missing; cannot grab mouse");
            return;
        }
        try {
            if (INP.grabMouse) INP.grabMouse(!!enable);
            else if (INP.cursorVisible) INP.cursorVisible(!enable);
            else this._warnOnce("no_grab", "[cam] INP.grabMouse/INP.cursorVisible missing");
        } catch (e) {
            this._warnOnce("grab_throw", "[cam] mouse grab failed: " + (e && (e.message || e.stack) || String(e)));
        }
    }

    _ensureConfigured() {
        if (this._configured) return;
        camModes.configure(shallowPick(this.cfg));
        camModes.setType(this.type);
        this._configured = true;
    }

    attach(bodyId) {
        this._ensureConfigured();
        const id = bodyId | 0;
        if (!id || id === this._attachedBodyId) return;
        this._attachedBodyId = id;
        camModes.attachTo(id);
    }

    _applyFirstPersonEyeHeight(eyeHeight) {
        const eye = U.num(eyeHeight, 1.65);
        if (Math.abs(eye - this._lastEye) < 1e-4) return;
        this._lastEye = eye;

        if (!this.cfg.first) this.cfg.first = {};
        if (!this.cfg.first.offset) this.cfg.first.offset = { x: 0, y: eye, z: 0 };
        else this.cfg.first.offset.y = eye;

        camModes.configure(shallowPick(this.cfg));
    }

    _filterMouseInSnapshot(dt, snap) {
        if (!snap) return;

        let dx = (snap.dx !== undefined) ? snap.dx : (snap.mouseDx ? snap.mouseDx() : 0);
        let dy = (snap.dy !== undefined) ? snap.dy : (snap.mouseDy ? snap.mouseDy() : 0);

        dx = U.num(dx, 0);
        dy = U.num(dy, 0);

        const mf = (this.cfg && this.cfg.look) ? U.num(this.cfg.look.mouseFilter, 18.0) : 18.0;
        const a = expAlpha(dt, mf);

        this._dxF = lerp(this._dxF, dx, a);
        this._dyF = lerp(this._dyF, dy, a);

        snap.dx = this._dxF;
        snap.dy = this._dyF;
    }

    _readCameraYawPitch() {
        let yaw = 0, pitch = 0;

        if (camModes.getYaw && camModes.getPitch) {
            try {
                yaw = U.num(camModes.getYaw(), 0);
                pitch = U.num(camModes.getPitch(), 0);
                return { yaw, pitch };
            } catch (e) {
                this._warnOnce("getYawPitch_throw", "[cam] camModes.getYaw/getPitch threw: " + (e && (e.message || e.stack) || String(e)));
            }
        }

        if (camModes.look) {
            const lk = camModes.look;
            yaw = U.num(lk.yaw !== undefined ? lk.yaw : (lk._yawS !== undefined ? lk._yawS : 0), 0);
            pitch = U.num(lk.pitch !== undefined ? lk.pitch : (lk._pitchS !== undefined ? lk._pitchS : 0), 0);
            return { yaw, pitch };
        }

        return { yaw: 0, pitch: 0 };
    }

    update(frame) {
        if (!frame) return;

        this._ensureConfigured();

        let dt = U.num(frame.dt, 0);
        // Clamp dt to avoid camera jitter on long frames / tab out.
        dt = U.clamp(dt, 0, 0.05);
        const bodyId = frame.ids ? (frame.ids.bodyId | 0) : 0;

        this.attach(bodyId);

        this._applyFirstPersonEyeHeight(frame.character ? frame.character.eyeHeight : 1.65);

        this._filterMouseInSnapshot(dt, frame.snap);

        camModes.update(dt, frame.snap);
    }

    syncDomain(dom, frame) {
        if (!dom) return;

        let dt = frame ? U.num(frame.dt, 0) : 0;
        dt = U.clamp(dt, 0, 0.05);
        const outSmooth = (this.cfg && this.cfg.look) ? U.num(this.cfg.look.outputSmooth, 14.0) : 14.0;
        const a = expAlpha(dt, outSmooth);

        const yp = this._readCameraYawPitch();
        const yawT = U.num(yp.yaw, 0);
        const pitchT = U.num(yp.pitch, 0);

        const dbg = this.cfg && this.cfg.debug;
        if (dbg && dbg.enabled && frame) {
            dbg._t = ((dbg._t || 0) + 1) | 0;
            const every = (dbg.everyFrames | 0) || 120;
            if ((dbg._t % every) === 0 && typeof LOG !== "undefined" && LOG && LOG.info) {
                LOG.info("[cam] yaw raw=" + yawT.toFixed(4) + " out=" + this._yawOut.toFixed(4) +
                         " pitch raw=" + pitchT.toFixed(4) + " out=" + this._pitchOut.toFixed(4) +
                         " dt=" + dt.toFixed(4));
            }
        }

        if (!this._outInit) {
            this._yawOut = yawT;
            this._pitchOut = pitchT;
            this._outInit = true;
        } else {
            this._yawOut = angLerp(this._yawOut, yawT, a);
            this._pitchOut = lerp(this._pitchOut, pitchT, a);
        }

        dom.view.yaw = this._yawOut;
        dom.view.pitch = this._pitchOut;

        dom.view.type = String(camModes.type || this.type || "third");

        if (frame && frame.view) {
            frame.view.yaw = dom.view.yaw;
            frame.view.pitch = dom.view.pitch;
            frame.view.type = dom.view.type;
        }
    }

    destroy() {
        try { camModes.attachTo(0); camModes.setType("free"); }
        catch (e) { this._warnOnce("destroy_throw", "[cam] destroy detach failed: " + (e && (e.message || e.stack) || String(e))); }
        this.enableGameplayMouseGrab(false);
        this._configured = false;
        this._attachedBodyId = 0;

        this._dxF = 0;
        this._dyF = 0;
        this._outInit = false;
    }
}

module.exports = PlayerCamera;