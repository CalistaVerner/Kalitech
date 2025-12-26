// FILE: Scripts/camera/CameraOrchestrator.js
// Author: Calista Verner
"use strict";

/**
 * CameraOrchestrator (Main JS camera brain) — AAA++ input
 *
 * Now uses:
 *  - input.consumeSnapshot() (one call per frame)
 *  - input.keyCode(name) (no JS key tables)
 *  - snapshot.justPressed / snapshot.justReleased (edge events)
 *
 * Keeps:
 *  - grab/cursor policy (top = cursor visible, gameplay = grabbed)
 *  - modes API unchanged: mode.update({ cam, bodyId, bodyPos, look, input })
 */

const FreeCam  = require("./modes/free.js");
const FirstCam = require("./modes/first.js");
const ThirdCam = require("./modes/third.js");
const TopCam   = require("./modes/top.js");

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
function safeJson(v) { try { return JSON.stringify(v); } catch (_) { return String(v); } }

function wrapAngle(a) {
    while (a > Math.PI) a -= Math.PI * 2;
    while (a < -Math.PI) a += Math.PI * 2;
    return a;
}

// --- helpers for Graal Java int[] arrays ---
function arrHas(arr, code) {
    if (!arr) return false;
    const n = arr.length | 0;
    for (let i = 0; i < n; i++) {
        if ((arr[i] | 0) === (code | 0)) return true;
    }
    return false;
}

class CameraOrchestrator {
    constructor() {
        this.type = "third";   // "free" | "first" | "third" | "top"
        this.bodyId = 0;

        // debug
        this.debug = {
            enabled: false,
            everyFrames: 60,
            _frame: 0
        };

        // shared look state (yaw/pitch in radians)
        this.look = {
            yaw: 0,
            pitch: 0,
            invertX: false,
            invertY: false,
            sensitivity: 0.002,
            pitchLimit: Math.PI * 0.49,
            smoothing: 0.15,
            _yawS: 0,
            _pitchS: 0,
            _inited: false
        };

        // shared hotkeys
        this.keys = {
            free: "F1",
            first: "F2",
            third: "F3",
            top: "F4"
        };

        // mode instances
        this.modes = {
            free: new FreeCam(),
            first: new FirstCam(),
            third: new ThirdCam(),
            top: new TopCam()
        };

        this._active = null;
        this._activeKey = "";

        // per-frame input fed to modes
        this.input = {
            dx: 0, dy: 0,
            mx: 0, my: 0, mz: 0,
            wheel: 0,
            dt: 0
        };

        // --- mouse-grab policy ---
        this.mouseGrab = {
            mode: "always",     // "always" | "rmb" | "never"
            rmbButtonIndex: 1,  // jME: 0=LMB, 1=RMB, 2=MMB
            _grabbed: false
        };

        // keyCode cache (avoid host calls every frame)
        this._kc = Object.create(null);
    }

    // ---------------- public API ----------------

    setType(type) {
        const t = String(type || "").trim().toLowerCase();
        if (!t) return;

        const norm =
            (t === "firstperson" || t === "first_person" || t === "first") ? "first" :
                (t === "thirdperson" || t === "third_person" || t === "third") ? "third" :
                    (t === "top" || t === "topdown" || t === "top_down" || t === "overhead") ? "top" :
                        (t === "free" || t === "fly") ? "free" :
                            "";

        if (!norm) return;
        if (norm === this.type) return;

        this.type = norm;
    }

    attachTo(bodyId) {
        this.bodyId = bodyId | 0;
    }

    configure(cfg) {
        if (!cfg) return;

        if (cfg.debug) {
            const d = cfg.debug;
            if (typeof d.enabled === "boolean") this.debug.enabled = d.enabled;
            if (typeof d.everyFrames === "number") this.debug.everyFrames = Math.max(1, d.everyFrames | 0);
        }

        if (cfg.look) {
            const L = cfg.look;
            if (typeof L.sensitivity === "number") this.look.sensitivity = L.sensitivity;
            if (typeof L.invertX === "boolean") this.look.invertX = L.invertX;
            if (typeof L.invertY === "boolean") this.look.invertY = L.invertY;
            if (typeof L.pitchLimit === "number") this.look.pitchLimit = clamp(L.pitchLimit, 0.1, Math.PI * 0.499);
            if (typeof L.smoothing === "number") this.look.smoothing = clamp(L.smoothing, 0.0, 1.0);
        }

        if (cfg.mouseGrab) {
            const mg = cfg.mouseGrab;
            if (typeof mg.mode === "string") {
                const m = mg.mode.trim().toLowerCase();
                if (m === "always" || m === "rmb" || m === "never") this.mouseGrab.mode = m;
            }
            if (typeof mg.rmbButtonIndex === "number") this.mouseGrab.rmbButtonIndex = mg.rmbButtonIndex | 0;
        }

        for (const k in this.modes) {
            if (Object.prototype.hasOwnProperty.call(cfg, k) && this.modes[k] && this.modes[k].configure) {
                this.modes[k].configure(cfg[k]);
            }
        }

        if (cfg.keys) {
            this.keys.free = cfg.keys.free || this.keys.free;
            this.keys.first = cfg.keys.first || this.keys.first;
            this.keys.third = cfg.keys.third || this.keys.third;
            this.keys.top = cfg.keys.top || this.keys.top;
        }

        // if key names changed, drop cached codes for those names
        this._kc = Object.create(null);
    }

    grabMouse(grab) {
        const g = !!grab;
        try {
            if (engine.input().grabMouse) engine.input().grabMouse(g);
            else if (engine.input().cursorVisible) engine.input().cursorVisible(!g);
        } catch (_) {}
        this.mouseGrab._grabbed = g;
    }

    update(dt) {
        this.input.dt = +dt || 0.016;

        // ✅ ONE CALL PER FRAME
        let snap = null;
        try { snap = engine.input().consumeSnapshot(); } catch (_) { snap = null; }
        if (!snap) return;

        this._readHotkeysFromSnapshot(snap);
        this._activateIfNeeded();

        this._updateMouseGrabPolicy(); // may use mouseDown() (host call), ok
        this._updateInputFromSnapshot(snap);
        this._updateLook();

        const mode = this._active;
        if (!mode) return;

        let bodyPos = null;
        if (this.bodyId) {
            try { bodyPos = engine.physics().position(this.bodyId); } catch (_) { bodyPos = null; }
        }

        mode.update({
            cam: engine.camera(),
            bodyId: this.bodyId,
            bodyPos,
            look: this.look,
            input: this.input
        });

        // clear one-frame deltas for mode inputs
        this.input.dx = 0;
        this.input.dy = 0;
        this.input.wheel = 0;

        // keep java-side motion flag correct for next frame
        try { if (engine.input().endFrame) engine.input().endFrame(); } catch (_) {}
    }

    // ---------------- internal ----------------

    _keyCode(name) {
        const k = String(name || "").trim().toUpperCase();
        if (!k) return -1;
        if (this._kc[k] !== undefined) return this._kc[k] | 0;
        let code = -1;
        try { code = engine.input().keyCode ? (engine.input().keyCode(k) | 0) : -1; } catch (_) { code = -1; }
        this._kc[k] = code | 0;
        return code | 0;
    }

    _readHotkeysFromSnapshot(snap) {
        // Prefer AAA++ edge list
        const jp = snap.justPressed;

        const kFree  = this._keyCode(this.keys.free);
        const kFirst = this._keyCode(this.keys.first);
        const kThird = this._keyCode(this.keys.third);
        const kTop   = this._keyCode(this.keys.top);

        if (arrHas(jp, kFree))  this.setType("free");
        if (arrHas(jp, kFirst)) this.setType("first");
        if (arrHas(jp, kThird)) this.setType("third");
        if (arrHas(jp, kTop))   this.setType("top");
    }

    _activateIfNeeded() {
        if (this.type === this._activeKey && this._active) return;

        const next = this.modes[this.type] || this.modes.third;
        const prev = this._active;

        try { if (prev && prev.onExit) prev.onExit(); } catch (_) {}
        this._active = next;
        this._activeKey = this.type;

        // init yaw/pitch from current camera (so switching doesn't snap)
        try {
            const cam = engine.camera();
            const y = +cam.yaw() || 0;
            const p = +cam.pitch() || 0;

            this.look.yaw = y;
            this.look.pitch = clamp(p, -this.look.pitchLimit, this.look.pitchLimit);
            this.look._yawS = this.look.yaw;
            this.look._pitchS = this.look.pitch;
            this.look._inited = true;
        } catch (_) {
            this.look._inited = false;
        }

        try { if (next && next.onEnter) next.onEnter(); } catch (_) {}

        engine.log().info("[camera.js] type=" + this.type + " body=" + (this.bodyId | 0));

        // top mode: always release grab
        if (this.type === "top") this.grabMouse(false);
    }

    _updateMouseGrabPolicy() {
        const gameplay = (this.type === "first" || this.type === "third" || this.type === "free");

        if (!gameplay) {
            if (this.mouseGrab._grabbed) this.grabMouse(false);
            return;
        }

        const mode = this.mouseGrab.mode;

        if (mode === "never") {
            if (this.mouseGrab._grabbed) this.grabMouse(false);
            return;
        }

        if (mode === "always") {
            if (!this.mouseGrab._grabbed) this.grabMouse(true);
            return;
        }

        // mode === "rmb"
        let rmb = false;
        try { rmb = !!engine.input().mouseDown(this.mouseGrab.rmbButtonIndex); } catch (_) { rmb = false; }
        if (rmb !== this.mouseGrab._grabbed) this.grabMouse(rmb);
    }

    _updateInputFromSnapshot(snap) {
        // movement axes (camera free mode uses these; player controller has its own input)
        const kd = snap.keysDown;

        const KW = this._keyCode("W");
        const KS = this._keyCode("S");
        const KA = this._keyCode("A");
        const KD = this._keyCode("D");
        const KQ = this._keyCode("Q");
        const KE = this._keyCode("E");

        const W = arrHas(kd, KW);
        const S = arrHas(kd, KS);
        const A = arrHas(kd, KA);
        const D = arrHas(kd, KD);
        const Q = arrHas(kd, KQ);
        const E = arrHas(kd, KE);

        this.input.mx = (D ? 1 : 0) + (A ? -1 : 0);
        this.input.my = (E ? 1 : 0) + (Q ? -1 : 0);
        this.input.mz = (W ? 1 : 0) + (S ? -1 : 0);

        // mouse look deltas (already consumed in Java)
        const dx = +snap.dx || 0;
        const dy = +snap.dy || 0;

        this.input.dx += dx;
        // invert screen Y: mouse up => look up
        this.input.dy += -dy;

        // wheel (already consumed in Java)
        this.input.wheel += (+snap.wheel || 0);

        // optional: keep absolute in case someone wants it
        // (not used by modes directly right now)
        // snap.mx / snap.my

        if (this.debug.enabled) {
            this.debug._frame++;
            if ((this.debug._frame % this.debug.everyFrames) === 0) {
                engine.log().info(
                    "[camera.js][input] " +
                    "type=" + this.type +
                    " body=" + (this.bodyId | 0) +
                    " keys(mx,my,mz)=(" + this.input.mx + "," + this.input.my + "," + this.input.mz + ")" +
                    " snap(dx,dy)=(" + dx + "," + dy + ")" +
                    " wheel=" + (+snap.wheel || 0) +
                    " grabbed=" + (snap.grabbed ? 1 : 0) +
                    " cursorVisible=" + (snap.cursorVisible ? 1 : 0) +
                    " mx,my=(" + (+snap.mx || 0).toFixed(1) + "," + (+snap.my || 0).toFixed(1) + ")"
                );
            }
        }
    }

    _updateLook() {
        const L = this.look;
        const dt = this.input.dt;

        if (!L._inited) {
            try {
                const cam = engine.camera();
                L.yaw = +cam.yaw() || 0;
                L.pitch = clamp((+cam.pitch() || 0), -L.pitchLimit, L.pitchLimit);
                L._yawS = L.yaw;
                L._pitchS = L.pitch;
                L._inited = true;
            } catch (_) {
                L._inited = true;
            }
        }

        const invX = L.invertX ? -1 : 1;
        const invY = L.invertY ? -1 : 1;

        // integrate raw (unsmoothed) targets
        L.yaw += this.input.dx * L.sensitivity * invX;
        L.pitch = clamp(L.pitch + this.input.dy * L.sensitivity * invY, -L.pitchLimit, L.pitchLimit);

        // smoothing
        const s = clamp(L.smoothing, 0, 1);
        const k = 1 - s;
        const a = 1 - Math.exp(-k * 30 * dt);

        const diff = wrapAngle(L.yaw - L._yawS);
        L._yawS = L._yawS + diff * a;
        L._pitchS = L._pitchS + (L.pitch - L._pitchS) * a;

        // apply
        try {
            const cam = engine.camera();
            cam.setYawPitch(L._yawS, L._pitchS);

            if (this.debug.enabled) {
                this.debug._frame++;
                if ((this.debug._frame % this.debug.everyFrames) === 0) {
                    const yNow = +cam.yaw() || 0;
                    const pNow = +cam.pitch() || 0;
                    engine.log().info(
                        "[camera.js][apply] " +
                        "target(y,p)=(" + L._yawS.toFixed(4) + "," + L._pitchS.toFixed(4) + ")" +
                        " actual(y,p)=(" + yNow.toFixed(4) + "," + pNow.toFixed(4) + ")" +
                        " rawInput(dx,dy)=(" + this.input.dx.toFixed(3) + "," + this.input.dy.toFixed(3) + ")"
                    );
                }
            }
        } catch (_) {}
    }
}

module.exports = new CameraOrchestrator();