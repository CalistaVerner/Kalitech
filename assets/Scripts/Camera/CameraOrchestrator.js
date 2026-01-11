"use strict";

const U = require("./camUtil.js");
const C = require("./CameraContract.js");
const CameraZoomController = require("./CameraZoomController.js");
const CameraCollisionSolver = require("./CameraCollisionSolver.js");
const CameraVolumeZones = require("./CameraVolumeZones.js");

function arrHas(arr, code) {
    for (let i = 0, n = arr.length | 0; i < n; i++) if ((arr[i] | 0) === code) return true;
    return false;
}

function smoothstep01(t) {
    t = t < 0 ? 0 : (t > 1 ? 1 : t);
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
        this._vPrev = false;
        this._switchCd = 0.0;
        this._switchCdTime = 0.18;

        this._tr = {active: false, t: 0.0, dur: 0.22, fromX: 0, fromY: 0, fromZ: 0};

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

        this._zoomBaseMin = this.zoom.min;
        this._zoomBaseMax = this.zoom.max;

        this.collision = new CameraCollisionSolver();

        this.zones = new CameraVolumeZones(player);
        this._lastZonesCfgRef = null;

        this.postSmooth = 22.0;
        this._sm = {x: 0, y: 0, z: 0};
        this._smInit = false;

        this._ctx = {
            orchestrator: this,
            mode: null,
            cam: this.d.camera,
            physics: this.d.physics,
            terrain: null,

            dt: 0,
            snap: null,
            input: null,

            bodyId: 0,
            bodyPos: null,

            look: this.look,
            zoom: this.zoom,

            target: { x: 0, y: 0, z: 0 },
            outPos: {x: 0, y: 0, z: 0},

            zoneState: null,
            zoneOverrides: null,

            _camMinY: -Infinity
        };

        this.register(require("./modes/first.js"));
        this.register(require("./modes/third.js"));

        const initial = (player.cfg && player.cfg.camera && player.cfg.camera.type)
            ? String(player.cfg.camera.type)
            : "third";
        this.setType(initial, true);
    }

    destroy() {
        this._active = null;
        this._modes.length = 0;
        this._byId = Object.create(null);

        this._ctx.mode = null;
        this._ctx.snap = null;
        this._ctx.input = null;
        this._ctx.bodyPos = null;
        this._ctx.zoneState = null;
        this._ctx.zoneOverrides = null;
        this._ctx.terrain = null;

        this._smInit = false;

        this.player = null;
        this.d = null;
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

    setType(type, instant) {
        const id = String(type || "").trim().toLowerCase();
        const next = this._byId[id];
        if (!next) throw new Error("[camera] unknown mode: " + type);
        if (this._active === next) return;

        const cam = this.d.camera;

        if (!instant && cam && typeof cam.location === "function") {
            const p = cam.location();
            if (p) {
                this._tr.active = true;
                this._tr.t = 0.0;
                this._tr.dur = 0.22;
                this._tr.fromX = U.vx(p, 0);
                this._tr.fromY = U.vy(p, 0);
                this._tr.fromZ = U.vz(p, 0);
            } else {
                this._tr.active = false;
            }
        } else {
            this._tr.active = false;
        }

        this._active = next;

        const cfg = this.player && this.player.cfg;
        if (cfg) {
            const c = cfg.camera || (cfg.camera = {});
            c.type = next.id;
        }
        if (this.player && this.player.dom && this.player.dom.view) this.player.dom.view.type = next.id;

        this._smInit = false;
    }

    next() {
        const n = this._modes.length | 0;
        if (n <= 1) return;

        const cur = this._active;
        let idx = 0;
        for (let i = 0; i < n; i++) if (this._modes[i] === cur) { idx = i; break; }
        this.setType(this._modes[(idx + 1) % n].id, false);
    }

    setTerrainSource(src) {
        if (src == null) {
            this._ctx.terrain = null;
            return;
        }
        if (typeof src.heightAt !== "function") throw new Error("[camera] terrain source must provide heightAt(x,z)");
        if (typeof src.normalAt !== "function") throw new Error("[camera] terrain source must provide normalAt(x,z)");
        this._ctx.terrain = src;
    }

    setTerrainHandle(terrainApi, terrainHandle, world) {
        if (!terrainApi || typeof terrainApi.heightAt !== "function") {
            throw new Error("[camera] setTerrainHandle: terrainApi.heightAt(handle,x,z,world) is required");
        }
        if (typeof terrainApi.normalAt !== "function") {
            throw new Error("[camera] setTerrainHandle: terrainApi.normalAt(handle,x,z,world) is required");
        }
        if (!terrainHandle) throw new Error("[camera] setTerrainHandle: terrainHandle is required");

        const useWorld = (world !== false);

        this._ctx.terrain = Object.freeze({
            heightAt: (x, z) => terrainApi.heightAt(terrainHandle, x, z, useWorld),
            normalAt: (x, z) => {
                const m = terrainApi.normalAt(terrainHandle, x, z, useWorld);
                return {x: +m.x, y: +m.y, z: +m.z};
            }
        });
    }

    _zonesCfgRef() {
        const c = this.player && this.player.cfg && this.player.cfg.camera ? this.player.cfg.camera : null;
        return c ? c.volumeZones : null;
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

    _smoothOutPos(out, dt, enabled, minY) {
        if (!enabled || !(this.postSmooth > 0)) {
            this._smInit = false;
            if (Number.isFinite(minY)) out.y = Math.max(out.y, minY);
            return out;
        }

        if (!this._smInit) {
            this._smInit = true;
            this._sm.x = out.x;
            this._sm.y = out.y;
            this._sm.z = out.z;
        } else {
            this._sm.x = U.expSmooth(this._sm.x, out.x, this.postSmooth, dt);
            this._sm.y = U.expSmooth(this._sm.y, out.y, this.postSmooth, dt);
            this._sm.z = U.expSmooth(this._sm.z, out.z, this.postSmooth, dt);
        }

        if (Number.isFinite(minY)) this._sm.y = Math.max(this._sm.y, minY);
        return this._sm;
    }

    _applyTransition(pos, dt) {
        const tr = this._tr;
        if (!tr.active) return pos;

        tr.t += dt > 0 ? dt : 0;
        const a = smoothstep01(tr.t / Math.max(1e-6, tr.dur));

        const x = tr.fromX + (pos.x - tr.fromX) * a;
        const y = tr.fromY + (pos.y - tr.fromY) * a;
        const z = tr.fromZ + (pos.z - tr.fromZ) * a;

        if (a >= 0.999) tr.active = false;
        this._smInit = false;

        return {x, y, z};
    }

    _applyPitchLimits(zoneOverrides) {
        const baseMin = -this.look.pitchLimit;
        const baseMax = +this.look.pitchLimit;

        const minPitch = (zoneOverrides && zoneOverrides.minPitch != null) ? +zoneOverrides.minPitch : baseMin;
        const maxPitch = (zoneOverrides && zoneOverrides.maxPitch != null) ? +zoneOverrides.maxPitch : baseMax;

        const lo = Math.min(minPitch, maxPitch);
        const hi = Math.max(minPitch, maxPitch);
        this.look.pitch = U.clamp(this.look.pitch, lo, hi);
    }

    _applyZoomLimits(zoneOverrides) {
        // restore baseline every frame (so zones can't "stick")
        this.zoom.min = this._zoomBaseMin;
        this.zoom.max = this._zoomBaseMax;

        if (!zoneOverrides) return;

        const hasMin = (zoneOverrides.zoomMin != null);
        const hasMax = (zoneOverrides.zoomMax != null);
        if (!hasMin && !hasMax) return;

        const zmin = hasMin ? +zoneOverrides.zoomMin : this.zoom.min;
        const zmax = hasMax ? +zoneOverrides.zoomMax : this.zoom.max;

        this.zoom.min = zmin;
        this.zoom.max = Math.max(zmin, zmax);
    }

    _handleModeSwitch(dt, snap) {
        this._switchCd = Math.max(0, this._switchCd - dt);

        const kd = snap.keysDown;
        if (!kd) throw new Error("[camera] snap.keysDown required");

        const vDown = (this._keyV > 0) && arrHas(kd, this._keyV);
        const pressed = (this._switchCd === 0) && vDown && !this._vPrev;
        this._vPrev = vDown;

        if (pressed) {
            this._switchCd = this._switchCdTime;
            this.next();
        }
    }

    update(dt, frame) {
        if (!frame || !frame.snap) return;

        dt = U.clamp(U.num(dt, 1 / 60), 0, 0.05);

        const snap = frame.snap;
        this._applyLook(snap);

        const phys = this.d.physics;
        const bodyId = this.player.getBodyId() | 0;
        const bodyPos = phys.position(bodyId);
        if (!bodyPos) throw new Error("[camera] physics.position(bodyId) returned null bodyId=" + bodyId);

        this._syncZonesIfNeeded();
        const zoneState = this.zones.update(bodyPos);
        const zoneOverrides = this.zones.blendedOverrides(null);

        this._applyPitchLimits(zoneOverrides);

        const cam = this.d.camera;
        cam.setYawPitch(this.look.yaw, this.look.pitch);

        this._handleModeSwitch(dt, snap);

        const mode = this._active;
        const ctx = this._ctx;

        ctx.mode = mode;
        ctx.dt = dt;
        ctx.snap = snap;
        ctx.input = frame.input;

        ctx.bodyId = bodyId;
        ctx.bodyPos = bodyPos;

        ctx.zoneState = zoneState;
        ctx.zoneOverrides = zoneOverrides;

        ctx._camMinY = -Infinity;

        if (mode.meta.supportsZoom) {
            this._applyZoomLimits(zoneOverrides);
            this.zoom.update(dt, ctx);
        } else {
            // keep zoom stable even in non-zoom modes
            this._applyZoomLimits(null);
        }

        mode.update(ctx);

        if (mode.meta.hasCollision && this.collision.enabled) {
            this.collision.solve(ctx);
        }

        const sm = this._smoothOutPos(ctx.outPos, dt, mode.id === "third", ctx._camMinY);
        const p = this._applyTransition(sm, dt);

        cam.setLocation(p.x, p.y, p.z);
    }
}

module.exports = CameraOrchestrator;