// Author: Calista Verner
"use strict";

const FreeCam  = require("./modes/free.js");
const FirstCam = require("./modes/first.js");
const ThirdCam = require("./modes/third.js");
const TopCam   = require("./modes/top.js");

const Dynamics = require("../Camera/CameraDynamicsCyberpunk.js");

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
function wrapAngle(a) {
    while (a > Math.PI) a -= Math.PI * 2;
    while (a < -Math.PI) a += Math.PI * 2;
    return a;
}
function num(v, fallback) {
    const n = +v;
    return Number.isFinite(n) ? n : (fallback || 0);
}
function vx(v, fb) {
    if (!v) return fb || 0;
    try { const x = v.x; if (typeof x === "function") return num(x.call(v), fb); if (typeof x === "number") return x; } catch (_) {}
    return fb || 0;
}
function vy(v, fb) {
    if (!v) return fb || 0;
    try { const y = v.y; if (typeof y === "function") return num(y.call(v), fb); if (typeof y === "number") return y; } catch (_) {}
    return fb || 0;
}
function vz(v, fb) {
    if (!v) return fb || 0;
    try { const z = v.z; if (typeof z === "function") return num(z.call(v), fb); if (typeof z === "number") return z; } catch (_) {}
    return fb || 0;
}

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
        this.type = "third";
        this.bodyId = 0;

        this.debug = { enabled: false, everyFrames: 60, _frame: 0 };

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

        this.keys = { free: "F1", first: "F2", third: "F3", top: "F4" };

        this.modes = {
            free: new FreeCam(),
            first: new FirstCam(),
            third: new ThirdCam(),
            top: new TopCam()
        };

        this._active = null;
        this._activeKey = "";

        this.input = { dx: 0, dy: 0, mx: 0, my: 0, mz: 0, wheel: 0, dt: 0 };

        this.mouseGrab = {
            mode: "always",
            rmbButtonIndex: 1,
            _grabbed: false
        };

        this._kc = Object.create(null);

        this.dynamics = new Dynamics();

        this.motion = {
            speed: 0,
            grounded: true,
            running: false,
            _lastX: 0,
            _lastY: 0,
            _lastZ: 0,
            _hasLast: false
        };
    }

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

        if (cfg.dynamics && this.dynamics && this.dynamics.configure) {
            this.dynamics.configure(cfg.dynamics);
        }

        if (cfg.keys) {
            this.keys.free = cfg.keys.free || this.keys.free;
            this.keys.first = cfg.keys.first || this.keys.first;
            this.keys.third = cfg.keys.third || this.keys.third;
            this.keys.top = cfg.keys.top || this.keys.top;
        }

        this._kc = Object.create(null);

        LOG.info("camera configured type=" + this.type + " keys=" + JSON.stringify(this.keys));
    }

    grabMouse(grab) {
        const g = !!grab;
        try {
            if (engine.input().grabMouse) engine.input().grabMouse(g);
            else if (engine.input().cursorVisible) engine.input().cursorVisible(!g);
        } catch (_) {}
        this.mouseGrab._grabbed = g;
    }

    onJump(strength) { try { this.dynamics.onJump(strength); } catch (_) {} }
    onLand(strength) { try { this.dynamics.onLand(strength); } catch (_) {} }

    update(dt, snap) {
        this.input.dt = +dt || 0.016;
        if (!snap) return;

        this._readHotkeysFromSnapshot(snap);
        this._activateIfNeeded();

        this._updateMouseGrabPolicyFromSnapshot(snap);
        this._updateInputFromSnapshot(snap);
        this._updateLook();

        const mode = this._active;
        if (!mode) return;

        let bodyPos = null;
        if (this.bodyId) {
            try { bodyPos = engine.physics().position(this.bodyId); } catch (_) { bodyPos = null; }
        }

        this._updateMotionFromSnapshotOrPhysics(snap, bodyPos, this.input.dt);

        mode.update({
            cam: engine.camera(),
            bodyId: this.bodyId,
            bodyPos,
            look: this.look,
            input: this.input,
            motion: this.motion
        });

        const isGameplay = (this.type === "first" || this.type === "third" || this.type === "free");
        if (isGameplay && this.type !== "top") {
            try {
                this.dynamics.apply({
                    cam: engine.camera(),
                    dt: this.input.dt,
                    mouseDx: this.input.dx,
                    mouseDy: this.input.dy,
                    speed: this.motion.speed,
                    grounded: this.motion.grounded,
                    running: this.motion.running
                });
            } catch (_) {}
        }

        this.input.dx = 0;
        this.input.dy = 0;
        this.input.wheel = 0;
    }

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

        this.motion._hasLast = false;

        try { if (next && next.onEnter) next.onEnter(); } catch (_) {}

        LOG.info("camera type=" + this.type + " body=" + (this.bodyId | 0));

        if (this.type === "top") this.grabMouse(false);
    }

    _updateMouseGrabPolicyFromSnapshot(snap) {
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

        const btn = this.mouseGrab.rmbButtonIndex | 0;
        const bit = (btn >= 0 && btn < 31) ? (1 << btn) : 0;
        const rmbDown = bit ? ((snap.mouseMask & bit) !== 0) : false;

        if (rmbDown !== this.mouseGrab._grabbed) this.grabMouse(rmbDown);
    }

    _updateInputFromSnapshot(snap) {
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

        const dx = +snap.dx || 0;
        const dy = +snap.dy || 0;

        this.input.dx += dx;
        this.input.dy += -dy;

        this.input.wheel += (+snap.wheel || 0);

        if (this.debug.enabled) {
            this.debug._frame++;
            if ((this.debug._frame % this.debug.everyFrames) === 0) {
                LOG.info(
                    "camera input type=" + this.type +
                    " body=" + (this.bodyId | 0) +
                    " keys(mx,my,mz)=(" + this.input.mx + "," + this.input.my + "," + this.input.mz + ")" +
                    " snap(dx,dy)=(" + dx + "," + dy + ")" +
                    " wheel=" + (+snap.wheel || 0) +
                    " grabbed=" + (snap.grabbed ? 1 : 0) +
                    " cursorVisible=" + (snap.cursorVisible ? 1 : 0)
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
                L.pitch = clamp(+cam.pitch() || 0, -L.pitchLimit, L.pitchLimit);
                L._yawS = L.yaw;
                L._pitchS = L.pitch;
            } catch (_) {}
            L._inited = true;
        }

        const dx = L.invertX ? -this.input.dx : this.input.dx;
        const dy = L.invertY ? -this.input.dy : this.input.dy;

        L.yaw   -= dx * L.sensitivity;
        L.pitch = clamp(L.pitch + dy * L.sensitivity, -L.pitchLimit, L.pitchLimit);

        const s = clamp(L.smoothing, 0, 1);
        const a = 1 - Math.exp(-(1 - s) * 30 * dt);

        const diff = wrapAngle(L.yaw - L._yawS);
        L._yawS += diff * a;
        L._pitchS += (L.pitch - L._pitchS) * a;

        try {
            engine.camera().setYawPitch(L._yawS, L._pitchS);
        } catch (_) {}
    }

    _updateMotionFromSnapshotOrPhysics(snap, bodyPos, dt) {
        const sSpeed = +snap.speed;
        if (Number.isFinite(sSpeed)) this.motion.speed = Math.max(0, sSpeed);

        const sRun = snap.run;
        if (typeof sRun === "boolean") this.motion.running = sRun;

        const sGround = snap.grounded;
        if (typeof sGround === "boolean") this.motion.grounded = sGround;

        if ((!Number.isFinite(sSpeed) || this.motion.speed <= 0.0001) && this.bodyId) {
            try {
                const v = engine.physics().velocity(this.bodyId);
                const sp = Math.hypot(vx(v, 0), vy(v, 0), vz(v, 0));
                if (Number.isFinite(sp)) this.motion.speed = sp;
            } catch (_) {}
        }

        if ((!Number.isFinite(sSpeed) || this.motion.speed <= 0.0001) && bodyPos) {
            const x = vx(bodyPos, 0), y = vy(bodyPos, 0), z = vz(bodyPos, 0);
            if (this.motion._hasLast) {
                const sp = Math.hypot(x - this.motion._lastX, y - this.motion._lastY, z - this.motion._lastZ) / Math.max(1e-4, dt);
                const a = 1 - Math.exp(-12 * dt);
                this.motion.speed = this.motion.speed + (sp - this.motion.speed) * a;
            }
            this.motion._lastX = x; this.motion._lastY = y; this.motion._lastZ = z;
            this.motion._hasLast = true;
        }
    }
}

module.exports = new CameraOrchestrator();