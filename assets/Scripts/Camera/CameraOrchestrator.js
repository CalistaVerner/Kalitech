"use strict";

const { validatePlayer, validateEngine, validateMode } = require("./CameraContract.js");
const CameraZoomController = require("./CameraZoomController.js");
const CameraCollisionSolver = require("./CameraCollisionSolver.js");
const U = require("./camUtil.js");

function smoothstep01(t) { return t * t * (3 - 2 * t); }
function arrHas(arr, code) {
    if (!arr) return false;
    const n = arr.length | 0;
    for (let i = 0; i < n; i++) if ((arr[i] | 0) === (code | 0)) return true;
    return false;
}


class CameraOrchestrator {
    constructor(player) {
        validatePlayer(player);
        validateEngine(engine);
        INP.grabMouse(true);

        this.player = player;

        this._vDownPrev = false;
        this._switchCd = 0;         // seconds
        this._switchCdTime = 0.18;  // seconds (debounce)
        this._modes = [];
        this._byId = Object.create(null);
        this._active = null;

        this.look = {
            yaw: 0,
            pitch: 0,
            sensitivity: 0.002,
            pitchLimit: Math.PI * 0.49,
            invertX: false,
            invertY: false
        };

        this.zoom = new CameraZoomController({
            steps: [2, 4, 8, 16, 32],
            index: 2,
            smooth: 18.0,
            cooldown: 0.08,
            invertWheel: false,
            min: 1.2,
            max: 60.0
        });

        this._zoomStateByMode = Object.create(null);

        this.collision = new CameraCollisionSolver();

        this.transition = {
            enabled: true,
            duration: 0.22,
            active: false,
            t: 0,
            from: { x: 0, y: 0, z: 0 },
            to: { x: 0, y: 0, z: 0 }
        };

        this._keyV = -1;

        // reusable ctx buffers (no garbage each frame)
        this._ctx = {
            orchestrator: this,
            mode: null,
            cam: null,
            dt: 0,
            snap: null,
            bodyId: 0,
            bodyPos: null,
            look: this.look,
            zoom: this.zoom,
            target: { x: 0, y: 0, z: 0 },
            outPos: { x: 0, y: 0, z: 0 }
        };

        // register built-ins
        this.register(require("./modes/first.js"));
        this.register(require("./modes/third.js"));

        this.setType("third");
    }

    register(modeOrCtor) {
        const mode = (typeof modeOrCtor === "function") ? new modeOrCtor(this) : modeOrCtor;
        validateMode(mode);

        const id = mode.id.toLowerCase();
        if (this._byId[id]) throw new Error("[camera] duplicate mode id: " + id);

        this._byId[id] = mode;
        this._modes.push(mode);

        if (!this._active) this._active = mode;
        return id;
    }

    getType() { return this._active ? this._active.id : "unknown"; }

    setType(type) {
        const id = String(type || "").trim().toLowerCase();
        const next = this._byId[id];
        if (!next) throw new Error("[camera] unknown mode: " + type);

        const prev = this._active;
        if (prev === next) return;

        if (prev && typeof prev.onExit === "function") prev.onExit();
        this._saveZoomState(prev);

        this._active = next;
        // persist выбранного режима в player cfg (optional, but very useful)
        const p = this.player;
        if (p && p.cfg) {
            if (!p.cfg.camera) p.cfg.camera = {};
            p.cfg.camera.type = next.id;
        }
        if (p && p.dom && p.dom.view) {
            p.dom.view.type = next.id;
        }


        this._restoreZoomState(next);
        if (next && typeof next.onEnter === "function") next.onEnter();

        if (this.collision && typeof this.collision.reset === "function") this.collision.reset();
        this.transition.active = false;

        this._applyMeta(next);
    }

    next() {
        const n = this._modes.length | 0;
        if (n <= 0) return;

        const cur = this._active;
        let idx = 0;
        for (let i = 0; i < n; i++) if (this._modes[i] === cur) { idx = i; break; }
        const next = this._modes[(idx + 1) % n];
        this.setType(next.id);
    }

    _ensureKeyV() {
        if (this._keyV >= 0) return;
        if (!INP || typeof INP.keyCode !== "function") return;
        this._keyV = INP.keyCode("V") | 0;
    }

    _saveZoomState(mode) {
        if (!mode || !mode.meta.supportsZoom) return;
        this._zoomStateByMode[mode.id.toLowerCase()] = {
            index: this.zoom.stepIndex(),
            current: this.zoom.value()
        };
    }

    _restoreZoomState(mode) {
        if (!mode || !mode.meta.supportsZoom) return;
        const st = this._zoomStateByMode[mode.id.toLowerCase()];
        if (!st) return;
        this.zoom.setIndex(st.index, true);
        this.zoom.reset(st.current);
    }

    _applyMeta(mode) {
        const meta = mode.meta;

        // collision quality from numRays
        this.collision.enabled = meta.hasCollision;
        const nr = meta.numRays | 0;
        const quality = (nr <= 4) ? "low" : (nr <= 6) ? "high" : "ultra";
        this.collision.configure({ quality });

        // ✅ ONLY orchestrator decides player model visibility
        this._applyPlayerModelVisibility(meta.playerModelVisible);
    }

    _applyPlayerModelVisibility(visible) {
        const model = this.player.getModel();
        if (!model) return;

        if (typeof model.setVisible === "function") {
            model.setVisible(!!visible);
            return;
        }

        // contract: если не умеет — это ошибка интеграции
        throw new Error("[camera] player model must support setVisible(boolean)");
    }

    _startTransition(from, to) {
        const tr = this.transition;
        tr.active = true;
        tr.t = 0;
        tr.from.x = from.x; tr.from.y = from.y; tr.from.z = from.z;
        tr.to.x = to.x; tr.to.y = to.y; tr.to.z = to.z;
    }

    _tickTransition(cam, dt) {
        const tr = this.transition;
        if (!tr.active) return false;

        const dur = Math.max(1e-4, U.num(tr.duration, 0.22));
        tr.t += dt;

        let a = U.clamp(tr.t / dur, 0, 1);
        a = smoothstep01(a);

        const x = tr.from.x + (tr.to.x - tr.from.x) * a;
        const y = tr.from.y + (tr.to.y - tr.from.y) * a;
        const z = tr.from.z + (tr.to.z - tr.from.z) * a;

        cam.setLocation(x, y, z);

        if (tr.t >= dur) {
            tr.active = false;
            cam.setLocation(tr.to.x, tr.to.y, tr.to.z);
        }
        return true;
    }

    update(dt, snap) {
        if (!snap) return;

        const cam = engine.camera();
        const ph = engine.physics();

        dt = U.clamp(U.num(dt, 1 / 60), 0, 0.05);

        this._ensureKeyV();

        // input → look
        let dx = (snap.dx !== undefined) ? U.num(snap.dx, 0) : 0;
        let dy = (snap.dy !== undefined) ? U.num(snap.dy, 0) : 0;

        if (this.look.invertX) dx = -dx;
        if (this.look.invertY) dy = -dy;

        this.look.yaw -= dx * this.look.sensitivity;
        this.look.pitch -= dy * this.look.sensitivity;
        this.look.pitch = U.clamp(this.look.pitch, -this.look.pitchLimit, this.look.pitchLimit);

        cam.setYawPitch(this.look.yaw, this.look.pitch);

        // body pos
        const bodyId = this.player.getBodyId() | 0;
        const bodyPos = ph.position(bodyId);
        if (!bodyPos) return;

        // mode switching
// debounce timer
        this._switchCd -= dt;
        if (this._switchCd < 0) this._switchCd = 0;

// EDGE detect by keysDown (most stable)
        const kd = snap && snap.keysDown;
        const vDown = (this._keyV >= 0) && arrHas(kd, this._keyV);
        const pressedV = (this._switchCd === 0) && vDown && !this._vDownPrev;
        this._vDownPrev = vDown;

        if (pressedV) this._switchCd = this._switchCdTime;


        if (pressedV) {
            if (pressedV) LOG.info("[camera] V edge detected -> switching mode");
            this._switchCd = this._switchCdTime;
            // compute from/to without mutating camera via “dry-run”
            const fromLoc = cam.location();
            const from = { x: U.vx(fromLoc), y: U.vy(fromLoc), z: U.vz(fromLoc) };

            this.next();

            const mode = this._active;
            const ctx = this._ctx;

            ctx.mode = mode;
            ctx.cam = cam;
            ctx.dt = dt;
            ctx.snap = snap;
            ctx.bodyId = bodyId;
            ctx.bodyPos = bodyPos;

            if (mode.meta.supportsZoom) this.zoom.update(dt, ctx);

            // mode writes outPos/target
            mode.update(ctx);

            const to = { x: ctx.outPos.x, y: ctx.outPos.y, z: ctx.outPos.z };

            if (this.transition.enabled) {
                this._startTransition(from, to);
                cam.setLocation(from.x, from.y, from.z);
                return;
            }
        }

        if (this.transition.active) {
            this._tickTransition(cam, dt);
            return;
        }

        const mode = this._active;
        if (!mode) return;

        const ctx = this._ctx;
        ctx.mode = mode;
        ctx.cam = cam;
        ctx.dt = dt;
        ctx.snap = snap;
        ctx.bodyId = bodyId;
        ctx.bodyPos = bodyPos;

        if (mode.meta.supportsZoom) this.zoom.update(dt, ctx);

        mode.update(ctx);

        cam.setLocation(ctx.outPos.x, ctx.outPos.y, ctx.outPos.z);

        if (this.collision.enabled) {
            this.collision.solve({
                cam,
                dt,
                target: ctx.target,
                bodyId
            });
        }
    }
}

module.exports = CameraOrchestrator;