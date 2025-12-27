// FILE: Scripts/player/systems/InputRouter.js
// Author: Calista Verner
"use strict";

function num(v, fb) { const n = +v; return Number.isFinite(n) ? n : (fb || 0); }
function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }

function arrHas(arr, code) {
    if (!arr) return false;
    const n = arr.length | 0;
    for (let i = 0; i < n; i++) if ((arr[i] | 0) === (code | 0)) return true;
    return false;
}

const DEFAULT_KEYS = Object.freeze({
    forward: ["W", "Z", "UP"],
    back:    ["S", "DOWN"],
    left:    ["D", "Q", "LEFT"],
    right:   ["A", "RIGHT"],
    run:     ["SHIFT", "LSHIFT", "RSHIFT"],
    jump:    ["SPACE"]
});

const DEFAULT_MOUSE = Object.freeze({
    enabled: true,
    sens: 0.0002,          // radians per pixel (match your camera.js config)
    invertY: false,
    pitchMin: -1.35,       // ~ -77 deg
    pitchMax:  1.35        // ~ +77 deg
});

function cloneKeys(src) {
    const out = Object.create(null);
    const k = src || DEFAULT_KEYS;
    out.forward = (k.forward || DEFAULT_KEYS.forward).slice();
    out.back    = (k.back    || DEFAULT_KEYS.back).slice();
    out.left    = (k.left    || DEFAULT_KEYS.left).slice();
    out.right   = (k.right   || DEFAULT_KEYS.right).slice();
    out.run     = (k.run     || DEFAULT_KEYS.run).slice();
    out.jump    = (k.jump    || DEFAULT_KEYS.jump).slice();
    return out;
}

function mergeMouse(src) {
    const m = src || {};
    const b = DEFAULT_MOUSE;
    return {
        enabled: (m.enabled !== undefined) ? !!m.enabled : b.enabled,
        sens: (m.sens !== undefined) ? num(m.sens, b.sens) : b.sens,
        invertY: (m.invertY !== undefined) ? !!m.invertY : b.invertY,
        pitchMin: (m.pitchMin !== undefined) ? num(m.pitchMin, b.pitchMin) : b.pitchMin,
        pitchMax: (m.pitchMax !== undefined) ? num(m.pitchMax, b.pitchMax) : b.pitchMax
    };
}

class InputRouter {
    constructor(cfg) {
        cfg = cfg || {};
        this.keys = cloneKeys(cfg.keys);
        this.mouse = mergeMouse(cfg.mouse);

        this._kc = Object.create(null);
        this._codes = null;

        this._prevLmb = false;

        // accumulated view angles (radians)
        this._yaw = 0;
        this._pitch = 0;

        // reusable state object
        this._state = {
            ax: 0, az: 0,
            run: false,
            jump: false,

            lmbDown: false,
            lmbJustPressed: false,

            dx: 0, dy: 0, wheel: 0,

            // new: view
            yaw: 0,
            pitch: 0
        };
    }

    configure(cfg) {
        cfg = cfg || {};
        if (cfg.keys) {
            this.keys = cloneKeys(cfg.keys);
            this._codes = null;
            this._kc = Object.create(null);
        }
        if (cfg.mouse) {
            this.mouse = mergeMouse(Object.assign({}, this.mouse, cfg.mouse));
        }
        return this;
    }

    bind() {
        this._ensureKeyCodes();
        // optional: initialize from camera if available
        try {
            const cam = engine.camera();
            if (cam && cam.yaw) this._yaw = num(cam.yaw(), 0);
            if (cam && cam.pitch) this._pitch = num(cam.pitch(), 0);
        } catch (_) {}
        return this;
    }

    _keyCode(name) {
        const k = String(name || "").trim().toUpperCase();
        if (!k) return -1;
        const cached = this._kc[k];
        if (cached !== undefined) return cached | 0;

        let code = -1;
        try {
            const inp = engine.input();
            code = (inp && inp.keyCode) ? (inp.keyCode(k) | 0) : -1;
        } catch (_) { code = -1; }

        this._kc[k] = code | 0;
        return code | 0;
    }

    _compileKeys() {
        const pack = (names) => {
            const out = [];
            const n = names ? (names.length | 0) : 0;
            for (let i = 0; i < n; i++) {
                const c = this._keyCode(names[i]);
                if (c >= 0) out.push(c | 0);
            }
            return out;
        };

        this._codes = {
            forward: pack(this.keys.forward),
            back:    pack(this.keys.back),
            left:    pack(this.keys.left),
            right:   pack(this.keys.right),
            run:     pack(this.keys.run),
            jump:    pack(this.keys.jump)
        };
    }

    _ensureKeyCodes() {
        if (!this._codes) this._compileKeys();
    }

    _anyDown(snap, codes) {
        const kd = snap && snap.keysDown;
        if (!kd) return false;
        for (let i = 0; i < codes.length; i++) if (arrHas(kd, codes[i] | 0)) return true;
        return false;
    }

    _anyJustPressed(snap, codes) {
        const jp = snap && snap.justPressed;
        if (!jp) return false;
        for (let i = 0; i < codes.length; i++) if (arrHas(jp, codes[i] | 0)) return true;
        return false;
    }

    _axis(snap, negCodes, posCodes) {
        const pos = this._anyDown(snap, posCodes) ? 1 : 0;
        const neg = this._anyDown(snap, negCodes) ? 1 : 0;
        return pos - neg;
    }

    _readLmbDown() {
        try {
            const inp = engine.input();
            if (inp && typeof inp.mouseDown === "function") return !!inp.mouseDown(0); // 0 = LMB
        } catch (_) {}
        return false;
    }

    _applyMouseLook(dx, dy) {
        const m = this.mouse;
        if (!m.enabled) return;

        const s = m.sens;
        this._yaw += dx * s;

        const ddy = (m.invertY ? -dy : dy);
        this._pitch = clamp(this._pitch + ddy * s, m.pitchMin, m.pitchMax);
    }

    /**
     * Single explicit hook to rotate player body.
     * Replace internals with your real API call.
     */
    _setPlayerYaw(bodyId, yaw) {
        const phy = engine.physics();
        if (phy && typeof phy.yaw === "function") {
            phy.yaw(bodyId, yaw);
            return;
        }
    }


    /**
     * Call this each frame after read(snap) if you want player to rotate with mouse.
     */
    applyToPlayer(bodyId) {
        if (!bodyId) return;

        // rotate player (yaw only)
        this._setPlayerYaw(bodyId, this._yaw);

        // keep camera angles aligned too (so center-of-screen aiming matches)
        try {
            const cam = engine.camera();
            if (cam) {
                if (typeof cam.yaw === "function") cam.yaw(this._yaw);
                if (typeof cam.pitch === "function") cam.pitch(this._pitch);
            }
        } catch (_) {}
    }

    read(snap) {
        this._ensureKeyCodes();

        const s = this._state;
        const c = this._codes;

        s.ax = this._axis(snap, c.left, c.right);
        s.az = this._axis(snap, c.back, c.forward);

        s.run = this._anyDown(snap, c.run);
        s.jump = this._anyJustPressed(snap, c.jump);

        // mouse deltas from snapshot
        try { s.dx = num(snap && (snap.dx !== undefined ? snap.dx : (snap.mouseDx && snap.mouseDx())), 0); } catch (_) { s.dx = 0; }
        try { s.dy = num(snap && (snap.dy !== undefined ? snap.dy : (snap.mouseDy && snap.mouseDy())), 0); } catch (_) { s.dy = 0; }
        try { s.wheel = num(snap && (snap.wheel !== undefined ? snap.wheel : (snap.wheelDelta && snap.wheelDelta())), 0); } catch (_) { s.wheel = 0; }

        // update look angles
        this._applyMouseLook(s.dx, s.dy);

        s.yaw = this._yaw;
        s.pitch = this._pitch;

        // LMB from Java API (not snapshot)
        const down = this._readLmbDown();
        s.lmbDown = down;
        s.lmbJustPressed = down && !this._prevLmb;
        this._prevLmb = down;

        return s;
    }
}

module.exports = InputRouter;