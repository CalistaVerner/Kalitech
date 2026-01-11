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

function smoothstep01(t) {
    return t * t * (3 - 2 * t);
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

        // config pointer cache (no per-frame rebuild)
        this._lastZonesCfgRef = null;

        this.transition = {
            enabled: true,
            duration: 0.22,
            active: false,
            t: 0,
            from: { x: 0, y: 0, z: 0 },
            to: { x: 0, y: 0, z: 0 }
        };

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

        const initial = (player.cfg && player.cfg.camera && player.cfg.camera.type)
            ? String(player.cfg.camera.type)
            : "third";
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
        return this._active ? this._active.id : "third";
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

        this.transition.active = false;

        const model = this.player.getModel();
        if (model && typeof model.setVisible === "function") {
            model.setVisible(!!next.meta.playerModelVisible);
        }
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

    _syncZonesIfNeeded() {
        const ref = this._zonesCfgRef();
        if (ref === this._lastZonesCfgRef) return;
        this._lastZonesCfgRef = ref;
        this.zones.configureFromPlayerCfg(); // strict validation inside
    }

    _applyLook(dt, snap) {
        let dx = U.num(snap.dx, 0);
        let dy = U.num(snap.dy, 0);

        if (this.look.invertX) dx = -dx;
        if (this.look.invertY) dy = -dy;

        this.look.yaw -= dx * this.look.sensitivity;
        this.look.pitch -= dy * this.look.sensitivity;
    }

    _tickTransition(dt) {
        const cam = this.d.camera;
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

    update(dt, frame) {
        if (!frame || !frame.snap) return;

        dt = U.clamp(U.num(dt, 1 / 60), 0, 0.05);

        const cam = this.d.camera;
        const phys = this.d.physics;

        this._applyLook(dt, frame.snap);

        const bodyId = this.player.getBodyId() | 0;
        const bodyPos = phys.position(bodyId);
        if (!bodyPos) throw new Error("[camera] physics.position(bodyId) returned null bodyId=" + bodyId);

        this._syncZonesIfNeeded();
        const zoneState = this.zones.update(bodyPos);
        const zoneOverrides = this.zones.blendedOverrides(null);

        // pitch limits (zone override, else base)
        const baseMinPitch = -this.look.pitchLimit;
        const baseMaxPitch = +this.look.pitchLimit;
        const minPitch = (zoneOverrides && zoneOverrides.minPitch != null) ? +zoneOverrides.minPitch : baseMinPitch;
        const maxPitch = (zoneOverrides && zoneOverrides.maxPitch != null) ? +zoneOverrides.maxPitch : baseMaxPitch;

        this.look.pitch = U.clamp(this.look.pitch, Math.min(minPitch, maxPitch), Math.max(minPitch, maxPitch));
        cam.setYawPitch(this.look.yaw, this.look.pitch);

        // mode switch
        this._switchCd = Math.max(0, this._switchCd - dt);

        const kd = frame.snap.keysDown;
        if (!kd) throw new Error("[camera] snap.keysDown required");

        const vDown = (this._keyV > 0) && arrHas(kd, this._keyV);
        const pressedV = (this._switchCd === 0) && vDown && !this._vDownPrev;
        this._vDownPrev = vDown;

        if (pressedV) {
            this._switchCd = this._switchCdTime;

            const loc = cam.location();
            this.transition.from.x = U.vx(loc, 0);
            this.transition.from.y = U.vy(loc, 0);
            this.transition.from.z = U.vz(loc, 0);

            this.next();
        }

        if (this.transition.active) {
            this._tickTransition(dt);
            return;
        }

        // build ctx once
        const ctx = this._ctx;
        const mode = this._active;

        ctx.mode = mode;
        ctx.dt = dt;
        ctx.snap = frame.snap;
        ctx.bodyId = bodyId;
        ctx.bodyPos = bodyPos;
        ctx.input = frame.input;
        ctx.zoneState = zoneState;
        ctx.zoneOverrides = zoneOverrides;

        // zoom (zone clamps)
        if (mode.meta.supportsZoom) {
            if (zoneOverrides && (zoneOverrides.zoomMin != null || zoneOverrides.zoomMax != null)) {
                const zmin = (zoneOverrides.zoomMin != null) ? +zoneOverrides.zoomMin : this.zoom.min;
                const zmax = (zoneOverrides.zoomMax != null) ? +zoneOverrides.zoomMax : this.zoom.max;
                this.zoom.min = zmin;
                this.zoom.max = Math.max(zmin, zmax);
            }
            this.zoom.update(dt, ctx);
        }

        // mode computes target/outPos (third keeps target centered on player)
        mode.update(ctx);

        // collision (strict)
        if (mode.meta.hasCollision && this.collision.enabled) {
            this.collision.solve(ctx);
        }

        if (pressedV && this.transition.enabled) {
            this.transition.to.x = ctx.outPos.x;
            this.transition.to.y = ctx.outPos.y;
            this.transition.to.z = ctx.outPos.z;

            this.transition.active = true;
            this.transition.t = 0;

            cam.setLocation(this.transition.from.x, this.transition.from.y, this.transition.from.z);
            return;
        }

        cam.setLocation(ctx.outPos.x, ctx.outPos.y, ctx.outPos.z);
    }
}

module.exports = CameraOrchestrator;