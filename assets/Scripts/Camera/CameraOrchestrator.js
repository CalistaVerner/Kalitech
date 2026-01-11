"use strict";

const U = require("./camUtil.js");
const C = require("./CameraContract.js");
const CameraZoomController = require("./CameraZoomController.js");
const CameraCollisionSolver = require("./CameraCollisionSolver.js");
const CameraVolumeZones = require("./CameraVolumeZones.js");

function arrHas(arr, code) {
    const n = arr.length | 0;
    for (let i = 0; i < n; i++) if ((arr[i] | 0) === code) return true;
    return false;
}

class CameraOrchestrator {
    constructor(player) {
        C.validatePlayer(player);

        this.player = player;
        this.d = player.d;

        this._byId = Object.create(null);
        this._modes = [];
        this._active = null;

        this._keyV = this.d.input.keyCode("V") | 0;
        this._vDownPrev = false;
        this._switchCd = 0.0;
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

        this.collision = new CameraCollisionSolver();

        this.zones = new CameraVolumeZones(player);
        this._lastZonesCfgRef = null;

        // post-solve smoothing (fixes “jerky third-person”)
        this.postSmooth = 22.0;
        this._sm = {x: 0, y: 0, z: 0};
        this._smInit = false;

        this._ctx = {
            orchestrator: this,
            mode: null,
            cam: this.d.camera,
            physics: this.d.physics,
            dt: 0,
            snap: null,
            bodyId: 0,
            bodyPos: null,
            look: this.look,
            zoom: this.zoom,
            input: null,
            target: { x: 0, y: 0, z: 0 },
            outPos: {x: 0, y: 0, z: 0},
            zoneState: null,
            zoneOverrides: null
        };

        this.register(require("./modes/first.js"));
        this.register(require("./modes/third.js"));

        const initial = (player.cfg && player.cfg.camera && player.cfg.camera.type) ? String(player.cfg.camera.type) : "third";
        this.setType(initial);
    }

    register(modeOrCtor) {
        const m = (typeof modeOrCtor === "function") ? new modeOrCtor(this) : modeOrCtor;
        const mode = C.validateMode(m);

        const id = String(mode.id).trim().toLowerCase();
        if (this._byId[id]) throw new Error("[camera] duplicate mode id: " + id);

        this._byId[id] = mode;
        this._modes.push(mode);
        if (!this._active) this._active = mode;
        return id;
    }

    getType() {
        const m = this._active;
        if (!m || !m.id) throw new Error("[camera] active mode is not set");
        return m.id;
    }

    setType(type) {
        const id = String(type || "").trim().toLowerCase();
        const next = this._byId[id];
        if (!next) throw new Error("[camera] unknown mode: " + type);

        if (this._active === next) return;
        this._active = next;

        if (this.player.cfg) {
            if (!this.player.cfg.camera) this.player.cfg.camera = {};
            this.player.cfg.camera.type = next.id;
        }
        if (this.player.dom && this.player.dom.view) this.player.dom.view.type = next.id;

        // Correct shadow-safe policy (no more “t shadows disappeared in third”)
        //const model = this.player.getModel();
        //model.setFirstPerson(next.id === "first");

        // reset post smoothing on mode switch
        this._smInit = false;
    }

    next() {
        const n = this._modes.length | 0;
        if (n <= 1) return;

        const cur = this._active;
        let idx = 0;
        for (let i = 0; i < n; i++) if (this._modes[i] === cur) { idx = i; break; }
        this.setType(this._modes[(idx + 1) % n].id);
    }

    _zonesCfgRef() {
        const c = this.player.cfg && this.player.cfg.camera ? this.player.cfg.camera : null;
        return c ? c.volumeZones : null;
    }

    _applyModelPolicy() {
        const model = this.player.getModel();
        if (model == null) return; // модель может появиться позже (spawn lifecycle)

        if (typeof model.setFirstPerson !== "function") {
            throw new Error("[camera] model.setFirstPerson(isFirstPerson) is required");
        }

        model.setFirstPerson(this.getType() === "first");
    }

    _syncZonesIfNeeded() {
        const ref = this._zonesCfgRef();
        if (ref === this._lastZonesCfgRef) return;
        this._lastZonesCfgRef = ref;
        this.zones.configureFromPlayerCfg();
    }

    _applyLook(snap) {
        let dx = U.num(snap.dx, 0);
        let dy = U.num(snap.dy, 0);

        if (this.look.invertX) dx = -dx;
        if (this.look.invertY) dy = -dy;

        this.look.yaw -= dx * this.look.sensitivity;
        this.look.pitch -= dy * this.look.sensitivity;
    }

    _smoothOutPos(out, dt, enabled) {
        if (!enabled || this.postSmooth <= 0) {
            this._smInit = false;
            return out;
        }

        if (!this._smInit) {
            this._smInit = true;
            this._sm.x = out.x;
            this._sm.y = out.y;
            this._sm.z = out.z;
            return this._sm;
        }

        this._sm.x = U.expSmooth(this._sm.x, out.x, this.postSmooth, dt);
        this._sm.y = U.expSmooth(this._sm.y, out.y, this.postSmooth, dt);
        this._sm.z = U.expSmooth(this._sm.z, out.z, this.postSmooth, dt);
        return this._sm;
    }

    update(dt, frame) {
        if (!frame || !frame.snap) return;

        dt = U.clamp(U.num(dt, 1 / 60), 0, 0.05);

        const cam = this.d.camera;
        const phys = this.d.physics;
        const snap = frame.snap;

        this._applyLook(snap);
        this._applyModelPolicy();

        const bodyId = this.player.getBodyId() | 0;
        const bodyPos = phys.position(bodyId);
        if (!bodyPos) throw new Error("[camera] physics.position(bodyId) returned null bodyId=" + bodyId);

        this._syncZonesIfNeeded();
        const zoneState = this.zones.update(bodyPos);
        const zoneOverrides = this.zones.blendedOverrides(null);

        const baseMinPitch = -this.look.pitchLimit;
        const baseMaxPitch = +this.look.pitchLimit;
        const minPitch = (zoneOverrides && zoneOverrides.minPitch != null) ? +zoneOverrides.minPitch : baseMinPitch;
        const maxPitch = (zoneOverrides && zoneOverrides.maxPitch != null) ? +zoneOverrides.maxPitch : baseMaxPitch;

        this.look.pitch = U.clamp(this.look.pitch, Math.min(minPitch, maxPitch), Math.max(minPitch, maxPitch));
        cam.setYawPitch(this.look.yaw, this.look.pitch);

        this._switchCd = Math.max(0, this._switchCd - dt);

        const kd = snap.keysDown;
        if (!kd) throw new Error("[camera] snap.keysDown required");

        const vDown = (this._keyV > 0) && arrHas(kd, this._keyV);
        const pressedV = (this._switchCd === 0) && vDown && !this._vDownPrev;
        this._vDownPrev = vDown;

        if (pressedV) {
            this._switchCd = this._switchCdTime;
            this.next();
        }

        const mode = this._active;
        const ctx = this._ctx;

        ctx.mode = mode;
        ctx.dt = dt;
        ctx.snap = snap;
        ctx.bodyId = bodyId;
        ctx.bodyPos = bodyPos;
        ctx.input = frame.input;
        ctx.zoneState = zoneState;
        ctx.zoneOverrides = zoneOverrides;

        if (mode.meta.supportsZoom) {
            if (zoneOverrides && (zoneOverrides.zoomMin != null || zoneOverrides.zoomMax != null)) {
                const zmin = (zoneOverrides.zoomMin != null) ? +zoneOverrides.zoomMin : this.zoom.min;
                const zmax = (zoneOverrides.zoomMax != null) ? +zoneOverrides.zoomMax : this.zoom.max;
                this.zoom.min = zmin;
                this.zoom.max = Math.max(zmin, zmax);
            }
            this.zoom.update(dt, ctx);
        }

        mode.update(ctx);

        if (mode.meta.hasCollision && this.collision.enabled) {
            this.collision.solve(ctx);
        }

        // post-solve smoothing only for third-person (first-person must be instant)
        const sm = this._smoothOutPos(ctx.outPos, dt, mode.id === "third");
        cam.setLocation(sm.x, sm.y, sm.z);
    }
}

module.exports = CameraOrchestrator;