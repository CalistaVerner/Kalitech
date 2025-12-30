// FILE: Scripts/player/systems/InputRouter.js
// Author: Calista Verner
"use strict";

const U = require("../util.js");

function arrHas(arr, code) {
    if (!arr) return false;
    const n = arr.length | 0;
    for (let i = 0; i < n; i++) if ((arr[i] | 0) === (code | 0)) return true;
    return false;
}

const DEFAULT_KEYS = Object.freeze({
    forward: ["W", "Z", "UP"],
    back: ["S", "DOWN"],

    left: ["A", "Q", "LEFT"],
    right: ["D", "RIGHT"],

    run: ["SHIFT", "LSHIFT", "RSHIFT"],

    // ✅ IMPORTANT: в разных системах пробел называется по-разному
    jump: ["SPACE", "SPACEBAR", "SPACE_BAR", "SPACE_KEY", " "]
});

const DEFAULT_MOUSE = Object.freeze({
    enabled: true,
    sens: 0.0002,
    invertY: false,
    pitchMin: -1.35,
    pitchMax: 1.35
});

function cloneKeys(src) {
    const out = Object.create(null);
    const k = src || DEFAULT_KEYS;
    out.forward = (k.forward || DEFAULT_KEYS.forward).slice();
    out.back = (k.back || DEFAULT_KEYS.back).slice();
    out.left = (k.left || DEFAULT_KEYS.left).slice();
    out.right = (k.right || DEFAULT_KEYS.right).slice();
    out.run = (k.run || DEFAULT_KEYS.run).slice();
    out.jump = (k.jump || DEFAULT_KEYS.jump).slice();
    return out;
}

function mergeMouse(src, base) {
    const m = src || {};
    const b = base || DEFAULT_MOUSE;
    return {
        enabled: (m.enabled !== undefined) ? !!m.enabled : b.enabled,
        sens: (m.sens !== undefined) ? U.num(m.sens, b.sens) : b.sens,
        invertY: (m.invertY !== undefined) ? !!m.invertY : b.invertY,
        pitchMin: (m.pitchMin !== undefined) ? U.num(m.pitchMin, b.pitchMin) : b.pitchMin,
        pitchMax: (m.pitchMax !== undefined) ? U.num(m.pitchMax, b.pitchMax) : b.pitchMax
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
        this._prevJumpDown = false;

        this._dbg = { t: 0, every: 120, didDumpCodes: false };

        this._state = {
            ax: 0, az: 0,
            run: false,
            jump: false,
            lmbDown: false,
            lmbJustPressed: false,
            dx: 0, dy: 0, wheel: 0
        };
    }

    configure(cfg) {
        cfg = cfg || {};
        if (cfg.keys) {
            this.keys = cloneKeys(cfg.keys);
            this._codes = null;
            this._kc = Object.create(null);
            this._dbg.didDumpCodes = false;
        }
        if (cfg.mouse) this.mouse = mergeMouse(cfg.mouse, this.mouse);
        return this;
    }

    bind() {
        this._ensureKeyCodes();
        this._dumpResolvedCodesOnce();
        return this;
    }

    _dumpResolvedCodesOnce() {
        if (this._dbg.didDumpCodes) return;
        this._dbg.didDumpCodes = true;

        const c = this._codes;
        const fmt = (a) => "[" + (a ? a.join(",") : "") + "]";
        if (typeof LOG !== "undefined" && LOG && LOG.info) {
            LOG.info(
                "[input] resolved key codes: " +
                "jump=" + fmt(c.jump) + " " +
                "run=" + fmt(c.run) + " " +
                "fwd=" + fmt(c.forward) + " back=" + fmt(c.back) + " left=" + fmt(c.left) + " right=" + fmt(c.right)
            );
        }
    }

    _keyCode(name) {
        const k = String(name || "").trim().toUpperCase();
        if (!k) return -1;

        const cached = this._kc[k];
        if (cached !== undefined) return cached | 0;

        if (!INP || typeof INP.keyCode !== "function") {
            throw new Error("[input] INP.keyCode missing; cannot resolve key: " + k);
        }

        const code = (INP.keyCode(k) | 0);
        this._kc[k] = code | 0;

        // ✅ важный лог: если движок не знает имя клавиши — узнаем сразу
        if (code <= 0 && typeof LOG !== "undefined" && LOG && LOG.warn) {
            LOG.warn("[input] INP.keyCode('" + k + "') returned " + code + " (unknown key name?)");
        }
        return code | 0;
    }

    _compileKeys() {
        const pack = (names) => {
            const out = [];
            const n = names ? (names.length | 0) : 0;
            for (let i = 0; i < n; i++) {
                const c = this._keyCode(names[i]);
                if (c > 0) out.push(c | 0);
            }
            return out;
        };

        this._codes = {
            forward: pack(this.keys.forward),
            back: pack(this.keys.back),
            left: pack(this.keys.left),
            right: pack(this.keys.right),
            run: pack(this.keys.run),
            jump: pack(this.keys.jump)
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

    _anyJustPressedEngine(snap, codes) {
        const jp = snap && (snap.justPressed || snap.justPressedKeyCodes || snap.justPressedCodes);
        if (!jp) return false;
        for (let i = 0; i < codes.length; i++) if (arrHas(jp, codes[i] | 0)) return true;
        return false;
    }


    _anyJustReleasedEngine(snap, codes) {
        const jr = snap && (snap.justReleased || snap.justReleasedKeyCodes || snap.justReleasedCodes);
        if (!jr) return false;
        for (let i = 0; i < codes.length; i++) if (arrHas(jr, codes[i] | 0)) return true;
        return false;
    }


    _axis(snap, negCodes, posCodes) {
        const pos = this._anyDown(snap, posCodes) ? 1 : 0;
        const neg = this._anyDown(snap, negCodes) ? 1 : 0;
        return pos - neg;
    }

    _readMouseDeltas(snap, s) {
        const dx = snap ? (snap.dx !== undefined ? snap.dx : (snap.mouseDx && snap.mouseDx())) : 0;
        const dy = snap ? (snap.dy !== undefined ? snap.dy : (snap.mouseDy && snap.mouseDy())) : 0;
        const wh = snap ? (snap.wheel !== undefined ? snap.wheel : (snap.wheelDelta && snap.wheelDelta())) : 0;
        s.dx = U.num(dx, 0);
        s.dy = U.num(dy, 0);
        s.wheel = U.num(wh, 0);
    }

    read(frame) {
        this._ensureKeyCodes();

        const snap = frame ? frame.snap : null;
        const s = this._state;
        const c = this._codes;

        s.ax = this._axis(snap, c.left, c.right);
        s.az = this._axis(snap, c.back, c.forward);
        s.run = this._anyDown(snap, c.run);

        // Jump: prefer engine justPressed, fallback to edge detect.
        // Some platforms report SPACE as justReleased due to low-level event quirks; we detect that too (and warn once).
        const jumpDown = this._anyDown(snap, c.jump);
        const jumpJP = this._anyJustPressedEngine(snap, c.jump);
        const jumpJR = this._anyJustReleasedEngine(snap, c.jump);

        let jump = jumpJP || (jumpDown && !this._prevJumpDown);
        if (!jump && jumpJR) {
            jump = true;
            U.warnOnce("jump_via_justReleased", "[input] jump resolved via justReleased (engine event mapping seems inverted for SPACE)");
        }

        s.jump = jump;
        this._prevJumpDown = jumpDown;

        this._readMouseDeltas(snap, s);

        if (!INP || typeof INP.mouseDown !== "function") {
            throw new Error("[input] INP.mouseDown missing");
        }
        const down = !!INP.mouseDown(0);
        s.lmbDown = down;
        s.lmbJustPressed = down && !this._prevLmb;
        this._prevLmb = down;

        // ✅ диагностика: раз в N кадров покажем первые коды keysDown,
        // чтобы увидеть, есть ли вообще SPACE в снапшоте и какой код у него.
        const dbg = this._dbg;
        dbg.t = (dbg.t + 1) | 0;
        if ((dbg.t % dbg.every) === 0 && snap && snap.keysDown && typeof LOG !== "undefined" && LOG && LOG.info) {
            const kd = snap.keysDown;
            const show = [];
            const n = Math.min((kd.length | 0), 12);
            for (let i = 0; i < n; i++) show.push(kd[i] | 0);
            LOG.info("[input] snapshot.keysDown(first " + n + ")=" + JSON.stringify(show) + " jumpResolved=" + JSON.stringify(c.jump));
        }

        if (frame && frame.input) {
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
        }

        return s;
    }
}

module.exports = InputRouter;