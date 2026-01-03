"use strict";

const CameraZoomController = require("./CameraZoomController.js");
const CameraCollisionSolver = require("./CameraCollisionSolver.js"); // Scripts/camera/...

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
    const jp = snap && (snap.justPressed || snap.justPressedKeyCodes || snap.justPressedCodes);
    if (!jp || !jp.length) return false;
    for (let i = 0, n = jp.length | 0; i < n; i++) {
        if ((jp[i] | 0) === (keyCode | 0)) return true;
    }
    return false;
}

function smoothstep01(t) { return t * t * (3 - 2 * t); }

function _metaDefaults(mode) {
    if (!mode.meta) mode.meta = {};
    if (typeof mode.meta.supportsZoom !== "boolean") mode.meta.supportsZoom = false;
    if (typeof mode.meta.hasCollision !== "boolean") mode.meta.hasCollision = false;
    if (typeof mode.meta.numRays !== "number") mode.meta.numRays = 0;
    return mode;
}

class CameraOrchestrator {
    constructor(player) {
        this.player = player;

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

        // global zoom controller (only if active mode supportsZoom)
        this.zoom = new CameraZoomController({
            steps: [2, 4, 8, 16, 32],
            index: 2,
            smooth: 18.0,
            cooldown: 0.08,
            invertWheel: false,
            min: 1.2,
            max: 60.0
        });

        // per-mode zoom state (fix "returns to old view")
        this._zoomState = Object.create(null);

        // collision post-pass (only if mode.hasCollision)
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

        // register built-ins via ctor (no hard-code "new ...()" outside)
        const First = require("./modes/first.js");
        const Third = require("./modes/third.js");
        this.register(First);
        this.register(Third);

        this.setType(this.type);

        try { if (INP && typeof INP.grabMouse === "function") INP.grabMouse(true); } catch (_) {}
    }

    // register(ModeCtor) OR register(instance)
    register(modeOrCtor) {
        let mode = modeOrCtor;

        if (typeof modeOrCtor === "function") {
            mode = new modeOrCtor(this); // <-- pass orchestrator "this"
        }

        if (!mode || typeof mode !== "object") throw new Error("[camera] register(): mode is null");
        const id = String(mode.id || "").trim().toLowerCase();
        if (!id) throw new Error("[camera] register(): mode.id is required");
        if (typeof mode.update !== "function") throw new Error("[camera] register(" + id + "): update(ctx) is required");
        if (this._byId[id]) throw new Error("[camera] register(" + id + "): duplicate id");

        _metaDefaults(mode);

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
        id = String(id).trim().toLowerCase();
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

        const prev = this._activeMode();
        this._activeIndex = idx;
        this.type = this._modes[idx].id;

        const next = this._activeMode();
        this._onModeSwitched(prev, next, /*withTransition*/false);
    }

    getType() { return this.type; }
    getYaw() { return this.look.yaw; }
    getPitch() { return this.look.pitch; }

    next() {
        const n = this._modes.length | 0;
        if (n <= 0) return;

        const prev = this._activeMode();
        this._activeIndex = (this._activeIndex + 1) % n;
        this.type = this._modes[this._activeIndex].id;
        const next = this._activeMode();

        this._onModeSwitched(prev, next, /*withTransition*/true);
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

    _applyModeMeta(mode) {
        const m = mode && mode.meta ? mode.meta : null;
        if (!m) return;

        this.collision.enabled = !!m.hasCollision;

        const nr = num(m.numRays, 0);
        let q = "high";
        if (nr <= 4) q = "low";
        else if (nr <= 6) q = "high";
        else q = "ultra";

        this.collision.configure({ quality: q });
    }

    _saveZoomState(mode) {
        if (!mode || !mode.meta || !mode.meta.supportsZoom) return;
        const id = String(mode.id).toLowerCase();
        this._zoomState[id] = {
            index: this.zoom.stepIndex(),
            current: this.zoom.value(),
            target: this.zoom.targetValue()
        };
    }

    _restoreZoomState(mode) {
        if (!mode || !mode.meta || !mode.meta.supportsZoom) return;
        const id = String(mode.id).toLowerCase();
        const st = this._zoomState[id];
        if (!st) return;
        try {
            this.zoom.setIndex(st.index, true);
            this.zoom.reset(st.current);
        } catch (_) {}
    }

    _onModeSwitched(prev, next, withTransition) {
        // 1) per-mode zoom (fix "returns old view")
        this._saveZoomState(prev);
        this._restoreZoomState(next);

        // 2) collision stability
        try { if (this.collision && typeof this.collision.reset === "function") this.collision.reset(); } catch (_) {}

        // 3) hooks (model mask etc.)
        try { if (prev && typeof prev.onExit === "function") prev.onExit({ orchestrator: this }); } catch (_) {}
        try { if (next && typeof next.onEnter === "function") next.onEnter({ orchestrator: this }); } catch (_) {}

        // 4) transition intent handled in update() (needs cam/bodyPos)
        if (!withTransition) return;
    }

    // Centralized "best effort" model visibility toggle for FirstPerson
    setPlayerModelVisible(visible) {
        const model = this.player.factory.modelHandle;

        if (!model) return false;

        // Strategy A: engine surface wrapper with setCullHint / setVisible
        try {
            if (typeof model.setVisible === "function") { model.setVisible(!!visible); return true; }
        } catch (_) {}

        // Strategy B: jME Spatial
        try {
            if (typeof model.setCullHint === "function") {
                if (typeof Java !== "undefined" && Java && typeof Java.type === "function") {
                    const CullHint = Java.type("com.jme3.scene.Spatial$CullHint");
                    model.setCullHint(visible ? CullHint.Inherit : CullHint.Always);
                    return true;
                }
            }
        } catch (_) {}

        return false;
    }

    update(dt, snap) {
        if (!snap) return;

        this._ensureKeyCodes();
        dt = clamp(num(dt, 1 / 60), 0, 0.05);

        const cam = engine.camera();

        // mouse look (FIX: pitch direction)
        let dx = getDx(snap);
        let dy = getDy(snap);

        if (this.look.invertX) dx = -dx;
        if (this.look.invertY) dy = -dy;

        this.look.yaw -= dx * this.look.sensitivity;

        // dy>0 usually means mouse moved DOWN -> to look up we subtract dy
        this.look.pitch -= dy * this.look.sensitivity;
        this.look.pitch = clamp(this.look.pitch, -this.look.pitchLimit, this.look.pitchLimit);

        cam.setYawPitch(this.look.yaw, this.look.pitch);

        // body pos
        if (!this.bodyId) return;
        let bodyPos = null;
        try { bodyPos = engine.physics().position(this.bodyId); } catch (_) { bodyPos = null; }
        if (!bodyPos) return;

        // build ctx
        let mode = this._activeMode();
        if (!mode) return;

        this._applyModeMeta(mode);

        const ctx = {
            orchestrator: this,   // <--- important: pass orchestrator
            mode,
            cam,
            dt,
            snap,
            bodyPos,
            bodyId: this.bodyId,
            look: this.look,
            zoom: this.zoom,
            target: null,
            input: null
        };

        // cycle on V (with transition)
        const pressedV = (this._keyV >= 0 && justPressedKey(snap, this._keyV));
        if (pressedV) {
            // capture from
            let cp = null;
            try { cp = cam.location(); } catch (_) { cp = null; }
            const fromPos = cp ? { x: vx(cp, 0), y: vy(cp, 0), z: vz(cp, 0) } : null;

            // switch
            const prev = mode;
            this.next();
            mode = this._activeMode();           // <--- IMPORTANT: refresh local var
            ctx.mode = mode;                     // <--- IMPORTANT: refresh ctx
            this._applyModeMeta(mode);

            this._grabMouse(true);

            if (this.transition.enabled && fromPos) {
                // compute toPos by running the NEW mode once
                cam.setLocation(fromPos.x, fromPos.y, fromPos.z);

                if (mode && mode.meta && mode.meta.supportsZoom) this.zoom.update(dt, ctx);

                try { mode.update(ctx); } catch (_) {}

                let toLoc = null;
                try { toLoc = cam.location(); } catch (_) { toLoc = null; }
                const toPos = toLoc ? { x: vx(toLoc, 0), y: vy(toLoc, 0), z: vz(toLoc, 0) } : fromPos;

                // start transition
                this._startTransition(cam, fromPos, toPos);

                // reset collision AFTER we picked toPos
                try { if (this.collision && typeof this.collision.reset === "function") this.collision.reset(); } catch (_) {}

                // NOTE: no further mode updates this frame
                return;
            }
        }

        // drive transition
        if (this.transition._active) {
            this._applyTransition(cam, dt);
            return;
        }

        // update zoom only if supported
        if (mode.meta && mode.meta.supportsZoom) this.zoom.update(dt, ctx);

        // mode update
        mode.update(ctx);

        // collision post-pass
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

module.exports = CameraOrchestrator;