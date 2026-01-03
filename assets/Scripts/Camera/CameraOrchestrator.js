// FILE: Scripts/Camera/CameraOrchestrator.js
"use strict";

const CameraZoomController = require("./CameraZoomController.js");
const CameraCollisionSolver = require("../Camera/CameraCollisionSolver.js");

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
function num(v, fb) { const n = +v; return Number.isFinite(n) ? n : (fb || 0); }

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

function getDx(snap) {
    if (!snap) return 0;
    if (snap.dx !== undefined) return num(snap.dx, 0);
    if (typeof snap.mouseDx === "function") return num(snap.mouseDx(), 0);
    return 0;
}
function getDy(snap) {
    if (!snap) return 0;
    if (snap.dy !== undefined) return num(snap.dy, 0);
    if (typeof snap.mouseDy === "function") return num(snap.mouseDy(), 0);
    return 0;
}

function justPressedKey(snap, keyCode) {
    const jp = snap && snap.justPressed;
    if (!jp || !jp.length) return false;
    for (let i = 0, n = jp.length | 0; i < n; i++) {
        if ((jp[i] | 0) === (keyCode | 0)) return true;
    }
    return false;
}

function smoothstep01(t) { return t * t * (3 - 2 * t); }

// --- minimal first mode with meta ---
class FirstPersonMode {
    constructor() {
        this.id = "first";
        this.meta = { supportsZoom: false, hasCollision: false, numRays: 0 };

        this.headOffset = { x: 0.0, y: 1.65, z: 0.0 };
    }
    getPivot(ctx) {
        const p = ctx && ctx.bodyPos;
        if (!p) return { x: 0, y: 0, z: 0 };
        return { x: vx(p, 0), y: vy(p, 0), z: vz(p, 0) };
    }
    update(ctx) {
        const cam = ctx && ctx.cam;
        const p = ctx && ctx.bodyPos;
        if (!cam || !p) return;

        const x = vx(p, 0) + this.headOffset.x;
        const y = vy(p, 0) + this.headOffset.y;
        const z = vz(p, 0) + this.headOffset.z;

        cam.setLocation(num(x, 0), num(y, 0), num(z, 0));

        // for consistency
        ctx.target = { x: vx(p, 0), y: vy(p, 0) + this.headOffset.y, z: vz(p, 0) };
    }
}

// If you use external files, register them from outside instead.
// Here it's kept clean & internal.
const ThirdPersonMode = require("./modes/third.js");

class CameraOrchestrator {
    constructor() {
        this._modes = [];
        this._byId = Object.create(null);
        this._activeIndex = -1;

        this.type = "first";
        this.bodyId = 0;

        this.look = {
            yaw: 0,
            pitch: 0,
            sensitivity: 0.002,
            pitchLimit: Math.PI * 0.49,
            invertX: false,
            invertY: false
        };

        this._keyV = -1;

        // global zoom controller (used only if active mode supportsZoom)
        this.zoom = new CameraZoomController({
            steps: [2, 4, 8, 16, 32],
            index: 2,
            smooth: 18.0,
            cooldown: 0.08,
            invertWheel: false,
            min: 1.2,
            max: 60.0
        });

        // collision post-pass (only if mode hasCollision)
        this.collision = new CameraCollisionSolver();

        // transition
        this.transition = {
            enabled: true,
            duration: 0.22,
            ease: "smoothstep",
            _active: false,
            _t: 0,
            _from: { x: 0, y: 0, z: 0 },
            _to:   { x: 0, y: 0, z: 0 }
        };

        this.mouseGrab = { enabled: true };

        // register built-ins
        this.register(new FirstPersonMode());
        this.register(new ThirdPersonMode());

        this.setType(this.type);
        try { if (INP && typeof INP.grabMouse === "function") INP.grabMouse(true); } catch (_) {}
    }

    register(mode) {
        if (!mode || typeof mode !== "object") throw new Error("[camera] register(mode): mode is null");
        const id = String(mode.id || "").trim().toLowerCase();
        if (!id) throw new Error("[camera] register(mode): mode.id is required");
        if (typeof mode.update !== "function") throw new Error("[camera] register(" + id + "): update(ctx) is required");
        if (this._byId[id]) throw new Error("[camera] register(" + id + "): duplicate id");

        // meta default
        if (!mode.meta) mode.meta = {};
        if (typeof mode.meta.supportsZoom !== "boolean") mode.meta.supportsZoom = false;
        if (typeof mode.meta.hasCollision !== "boolean") mode.meta.hasCollision = false;
        if (typeof mode.meta.numRays !== "number") mode.meta.numRays = 0;

        this._modes.push(mode);
        this._byId[id] = mode;

        if (this._activeIndex < 0) {
            this._activeIndex = 0;
            this.type = this._modes[0].id;
        }
        return id;
    }

    attachTo(bodyId) { this.bodyId = bodyId | 0; }

    _indexOf(id) {
        if (!id) return -1;
        for (let i = 0; i < this._modes.length; i++) {
            if (String(this._modes[i].id).toLowerCase() === id) return i;
        }
        return -1;
    }

    _activeMode() {
        const i = this._activeIndex | 0;
        return (i >= 0 && i < this._modes.length) ? this._modes[i] : null;
    }

    setType(type) {
        const id = String(type || "").trim().toLowerCase();
        const idx = this._indexOf(id);
        if (idx < 0) return;
        this._activeIndex = idx;
        this.type = this._modes[idx].id;
    }

    getType() { return this.type; }
    getYaw() { return this.look.yaw; }
    getPitch() { return this.look.pitch; }

    next() {
        const n = this._modes.length | 0;
        if (n <= 0) return;
        this._activeIndex = (this._activeIndex + 1) % n;
        this.type = this._modes[this._activeIndex].id;
    }

    _ensureKeyCodes() {
        if (this._keyV >= 0) return;
        try { this._keyV = INP && INP.keyCode ? (INP.keyCode("V") | 0) : -1; }
        catch (_) { this._keyV = -1; }
    }

    _grabMouse(grab) {
        if (!this.mouseGrab.enabled) return;
        try { if (INP && typeof INP.grabMouse === "function") INP.grabMouse(!!grab); } catch (_) {}
    }

    _startTransition(cam, fromPos, toPos) {
        const tr = this.transition;
        tr._active = true;
        tr._t = 0;

        tr._from.x = num(fromPos.x, 0);
        tr._from.y = num(fromPos.y, 0);
        tr._from.z = num(fromPos.z, 0);

        tr._to.x = num(toPos.x, 0);
        tr._to.y = num(toPos.y, 0);
        tr._to.z = num(toPos.z, 0);

        cam.setLocation(tr._from.x, tr._from.y, tr._from.z);
    }

    _applyTransition(cam, dt) {
        const tr = this.transition;
        if (!tr._active) return false;

        const dur = Math.max(1e-4, num(tr.duration, 0.22));
        tr._t += dt;

        let a = clamp(tr._t / dur, 0, 1);
        if (tr.ease === "smoothstep") a = smoothstep01(a);

        const x = tr._from.x + (tr._to.x - tr._from.x) * a;
        const y = tr._from.y + (tr._to.y - tr._from.y) * a;
        const z = tr._from.z + (tr._to.z - tr._from.z) * a;

        cam.setLocation(x, y, z);

        if (tr._t >= dur) {
            tr._active = false;
            cam.setLocation(tr._to.x, tr._to.y, tr._to.z);
        }

        return true;
    }

    // map meta.numRays -> collision quality
    _applyModeMeta(mode) {
        const m = mode && mode.meta ? mode.meta : null;
        if (!m) return;

        // hasCollision toggles solver
        this.collision.enabled = !!m.hasCollision;

        // numRays hint -> quality bucket
        const nr = num(m.numRays, 0);
        let q = "high";
        if (nr <= 4) q = "low";
        else if (nr <= 6) q = "high";
        else q = "ultra";

        // configure only those fields (don’t override your tuned params)
        this.collision.configure({
            quality: q
        });
    }

    update(dt, snap) {
        if (!snap) return;

        this._ensureKeyCodes();
        dt = clamp(num(dt, 1 / 60), 0, 0.05);

        const cam = engine.camera();

        // mouse look
    // mouse look (FIX: Y axis)
            let dx = getDx(snap);
            let dy = getDy(snap);

    // user inversion toggles
            if (this.look.invertX) dx = -dx;
            if (this.look.invertY) dy = -dy;

    // yaw: standard
            this.look.yaw -= dx * this.look.sensitivity;

    // pitch: standard FPS = mouse up -> look up
    // For most input backends dy>0 means mouse moved DOWN, so we subtract.
        this.look.pitch -= dy * this.look.sensitivity;

        this.look.pitch = clamp(this.look.pitch, -this.look.pitchLimit, this.look.pitchLimit);


        cam.setYawPitch(this.look.yaw, this.look.pitch);

        // body pos
        if (!this.bodyId) return;
        let bodyPos = null;
        try { bodyPos = engine.physics().position(this.bodyId); } catch (_) { bodyPos = null; }
        if (!bodyPos) return;

        // build ctx for mode
        const mode = this._activeMode();
        if (!mode) return;

        // apply meta each frame is ok (cheap), or you can apply on switch only
        this._applyModeMeta(mode);

        const ctx = {
            cam,
            dt,
            snap,
            bodyPos,
            bodyId: this.bodyId,
            look: this.look,
            zoom: this.zoom,
            target: null,      // mode should set ctx.target (pivot) for collision solver
            input: null        // optional future (zoomIn/zoomOut keys)
        };

        // toggle (cycle) on V with transition
        const pressedV = (this._keyV >= 0 && justPressedKey(snap, this._keyV));
        if (pressedV) {
            let cp = null;
            try { cp = cam.location(); } catch (_) { cp = null; }
            const fromPos = cp ? { x: vx(cp, 0), y: vy(cp, 0), z: vz(cp, 0) } : null;

            this.next();
            const newMode = this._activeMode();
            this._applyModeMeta(newMode);

            this._grabMouse(true);

            if (this.transition.enabled && fromPos) {
                // compute "to" by doing a dry update into ctx then reading cam.location
                // safer: ask mode for desired by running update once and capturing, then restoring fromPos
                cam.setLocation(fromPos.x, fromPos.y, fromPos.z);

                // update zoom for new mode BEFORE computing desired (if it uses zoom)
                if (newMode && newMode.meta && newMode.meta.supportsZoom) this.zoom.update(dt, ctx);

                // run mode update to set desired position
                try { newMode.update(ctx); } catch (_) {}

                let toLoc = null;
                try { toLoc = cam.location(); } catch (_) { toLoc = null; }
                const toPos = toLoc ? { x: vx(toLoc, 0), y: vy(toLoc, 0), z: vz(toLoc, 0) } : fromPos;

                // restore and start transition
                this._startTransition(cam, fromPos, toPos);
            }
        }

        // drive transition
        if (this.transition._active) {
            this._applyTransition(cam, dt);
            return;
        }

        // update zoom only if supported by active mode
        if (mode.meta && mode.meta.supportsZoom) this.zoom.update(dt, ctx);

        // mode update (sets desired cam location + ctx.target)
        mode.update(ctx);

        // collision post-pass (if enabled and we have target)
        if (this.collision.enabled && ctx.target) {
            this.collision.solve({
                cam,
                dt,
                target: ctx.target,
                bodyId: this.bodyId
            });
        }
    }
}

module.exports = new CameraOrchestrator();