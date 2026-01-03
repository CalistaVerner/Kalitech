// FILE: Scripts/Camera/CameraOrchestrator.js
"use strict";

const { validatePlayer, validateEngine, validateMode } = require("./CameraContract.js");
const CameraZoomController = require("./CameraZoomController.js");
const CameraCollisionSolver = require("./CameraCollisionSolver.js");
const U = require("./camUtil.js");

function smoothstep01(t) { return t * t * (3 - 2 * t); }
function arrHas(arr, code) {
    const n = arr.length | 0;
    for (let i = 0; i < n; i++) if ((arr[i] | 0) === code) return true;
    return false;
}

class CameraOrchestrator {
    constructor(player) {
        validatePlayer(player);
        validateEngine(engine);

        this.player = player;

        this._modes = [];
        this._byId = Object.create(null);
        this._active = null;

        this._keyV = INP.keyCode("V") | 0;
        this._vDownPrev = false;
        this._switchCd = 0;
        this._switchCdTime = 0.18;

        this.look = {
            yaw: 0,
            pitch: 0,
            sensitivity: 0.0002,
            pitchLimit: Math.PI * 0.49,
            invertX: false,
            invertY: false
        };

        this.zoom = new CameraZoomController({
            steps: [2, 4, 8, 16, 32],
            index: 2,
            smooth: 18.0,
            cooldown: 0.08,
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

        this._tmpFrom = { x: 0, y: 0, z: 0 };
        this._tmpTo = { x: 0, y: 0, z: 0 };

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

        this.register(require("./modes/first.js"));
        this.register(require("./modes/third.js"));

        // ✅ Initial mode is authoritative: cfg.camera.type if provided, else "third".
        // Fixes “switch then revert” when orchestrator is recreated.
        const initial = (player && player.cfg && player.cfg.camera && player.cfg.camera.type)
            ? String(player.cfg.camera.type)
            : "third";
        this.setType(initial);
    }

    destroy() {
        // INP.grabMouse(false);
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
        const id = String(type).trim().toLowerCase();
        const next = this._byId[id];
        if (!next) throw new Error("[camera] unknown mode: " + type);

        const prev = this._active;
        if (prev === next) return;

        if (prev && prev.meta.supportsZoom) {
            this._zoomStateByMode[prev.id] = { index: this.zoom.stepIndex(), current: this.zoom.value() };
        }

        this._active = next;

        // Persist selection for UI/debug, but NEVER drive mode selection from cfg on update.
        if (this.player && this.player.cfg) {
            if (!this.player.cfg.camera) this.player.cfg.camera = {};
            this.player.cfg.camera.type = next.id;
        }
        if (this.player && this.player.dom && this.player.dom.view) this.player.dom.view.type = next.id;

        if (next.meta.supportsZoom) {
            const st = this._zoomStateByMode[next.id];
            if (st) {
                this.zoom.setIndex(st.index, true);
                this.zoom.reset(st.current);
            }
        }

        this.transition.active = false;
        this.collision.reset();

        this._applyMeta(next);
    }

    next() {
        const n = this._modes.length | 0;
        if (n === 0) return;

        const cur = this._active;
        let idx = 0;
        for (let i = 0; i < n; i++) if (this._modes[i] === cur) { idx = i; break; }

        this.setType(this._modes[(idx + 1) % n].id);
    }

    _applyMeta(mode) {
        const meta = mode.meta;

        this.collision.enabled = meta.hasCollision;
        const nr = meta.numRays | 0;
        this.collision.configure({ quality: (nr <= 4) ? "low" : (nr <= 6) ? "high" : "ultra" });

        // ✅ Единственное место, где решается видимость модели игрока.
        const model = this.player.getModel();
        if (!model || typeof model.setVisible !== "function") {
            throw new Error("[camera] player.getModel() must return handle with setVisible(boolean)");
        }
        model.setVisible(!!meta.playerModelVisible);
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
        const dur = Math.max(1e-4, U.num(tr.duration, 0.22));

        tr.t += dt;
        let a = U.clamp(tr.t / dur, 0, 1);
        a = smoothstep01(a);

        cam.setLocation(
            tr.from.x + (tr.to.x - tr.from.x) * a,
            tr.from.y + (tr.to.y - tr.from.y) * a,
            tr.from.z + (tr.to.z - tr.from.z) * a
        );

        if (tr.t >= dur) {
            tr.active = false;
            cam.setLocation(tr.to.x, tr.to.y, tr.to.z);
        }
    }

    update(dt, snap) {
        if (!snap) return;

        const cam = engine.camera();
        const ph = engine.physics();

        dt = U.clamp(U.num(dt, 1 / 60), 0, 0.05);

        // look
        let dx = U.num(snap.dx, 0);
        let dy = U.num(snap.dy, 0);

        if (this.look.invertX) dx = -dx;
        if (this.look.invertY) dy = -dy;

        this.look.yaw -= dx * this.look.sensitivity;
        this.look.pitch -= dy * this.look.sensitivity;
        this.look.pitch = U.clamp(this.look.pitch, -this.look.pitchLimit, this.look.pitchLimit);

        cam.setYawPitch(this.look.yaw, this.look.pitch);

        // body pos
        const bodyId = this.player.getBodyId() | 0;
        const bodyPos = ph.position(bodyId);
        if (!bodyPos) throw new Error("[camera] physics.position(bodyId) returned null bodyId=" + bodyId);

        // mode switch edge
        this._switchCd = Math.max(0, this._switchCd - dt);

        const kd = snap.keysDown;
        if (!kd) throw new Error("[camera] snap.keysDown required");

        const vDown = (this._keyV > 0) && arrHas(kd, this._keyV);
        const pressedV = (this._switchCd === 0) && vDown && !this._vDownPrev;
        this._vDownPrev = vDown;

        if (pressedV) {
            this._switchCd = this._switchCdTime;

            const loc = cam.location();
            this._tmpFrom.x = U.vx(loc); this._tmpFrom.y = U.vy(loc); this._tmpFrom.z = U.vz(loc);

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
            mode.update(ctx);

            this._tmpTo.x = ctx.outPos.x; this._tmpTo.y = ctx.outPos.y; this._tmpTo.z = ctx.outPos.z;

            if (this.transition.enabled) {
                this._startTransition(this._tmpFrom, this._tmpTo);
                cam.setLocation(this._tmpFrom.x, this._tmpFrom.y, this._tmpFrom.z);
                return;
            }
        }

        if (this.transition.active) {
            this._tickTransition(cam, dt);
            return;
        }

        const mode = this._active;
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
            this.collision.solve({ cam, dt, target: ctx.target, bodyId });
        }
    }
}

module.exports = CameraOrchestrator;