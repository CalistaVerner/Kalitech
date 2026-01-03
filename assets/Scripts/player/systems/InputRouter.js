// FILE: Scripts/player/systems/InputRouter.js
"use strict";

const U = require("../util.js");

function arrHas(arr, code) {
    const n = arr.length | 0;
    for (let i = 0; i < n; i++) if ((arr[i] | 0) === code) return true;
    return false;
}

const DEFAULT_KEYS = Object.freeze({
    forward: ["W", "UP"],
    back: ["S", "DOWN"],
    left: ["A", "LEFT"],
    right: ["D", "RIGHT"],
    run: ["SHIFT"],
    jump: ["SPACE"]
});

class InputRouter {
    constructor(cfg) {
        cfg = cfg || {};
        const keys = (cfg.keys && typeof cfg.keys === "object") ? cfg.keys : DEFAULT_KEYS;

        if (!INP || typeof INP.keyCode !== "function") throw new Error("[input] INP.keyCode required");
        if (typeof INP.mouseDown !== "function") throw new Error("[input] INP.mouseDown required");

        this._codes = {
            forward: this._pack(keys.forward || DEFAULT_KEYS.forward),
            back: this._pack(keys.back || DEFAULT_KEYS.back),
            left: this._pack(keys.left || DEFAULT_KEYS.left),
            right: this._pack(keys.right || DEFAULT_KEYS.right),
            run: this._pack(keys.run || DEFAULT_KEYS.run),
            jump: this._pack(keys.jump || DEFAULT_KEYS.jump)
        };

        this._prevJumpDown = false;
        this._prevLmb = false;

        this._state = {
            ax: 0, az: 0,
            run: false,
            jump: false,
            lmbDown: false,
            lmbJustPressed: false,
            dx: 0, dy: 0, wheel: 0
        };
    }

    _pack(names) {
        const out = [];
        const n = names.length | 0;
        for (let i = 0; i < n; i++) {
            const c = INP.keyCode(String(names[i]).trim().toUpperCase()) | 0;
            if (c > 0) out.push(c);
        }
        return out;
    }

    bind() { return this; }

    _anyDown(keysDown, codes) {
        for (let i = 0; i < codes.length; i++) if (arrHas(keysDown, codes[i])) return true;
        return false;
    }

    read(frame) {
        const snap = frame.snap;
        if (!snap) return this._state;

        const kd = snap.keysDown;
        if (!kd) throw new Error("[input] snap.keysDown required");

        const c = this._codes;
        const s = this._state;

        const fwd = this._anyDown(kd, c.forward) ? 1 : 0;
        const back = this._anyDown(kd, c.back) ? 1 : 0;
        const right = this._anyDown(kd, c.right) ? 1 : 0;
        const left = this._anyDown(kd, c.left) ? 1 : 0;

        s.az = fwd - back;
        s.ax = right - left;

        s.run = this._anyDown(kd, c.run);

        const jumpDown = this._anyDown(kd, c.jump);
        s.jump = jumpDown && !this._prevJumpDown;
        this._prevJumpDown = jumpDown;

        s.dx = U.num(snap.dx, 0);
        s.dy = U.num(snap.dy, 0);
        s.wheel = U.num(snap.wheel, 0);

        const lmb = !!INP.mouseDown(0);
        s.lmbDown = lmb;
        s.lmbJustPressed = lmb && !this._prevLmb;
        this._prevLmb = lmb;

        // копируем в frame.input (единый формат)
        const fi = frame.input;
        fi.ax = s.ax | 0;
        fi.az = s.az | 0;
        fi.run = !!s.run;
        fi.jump = !!s.jump;
        fi.lmbDown = !!s.lmbDown;
        fi.lmbJustPressed = !!s.lmbJustPressed;
        fi.dx = s.dx;
        fi.dy = s.dy;
        fi.wheel = s.wheel;

        return s;
    }
}

module.exports = InputRouter;