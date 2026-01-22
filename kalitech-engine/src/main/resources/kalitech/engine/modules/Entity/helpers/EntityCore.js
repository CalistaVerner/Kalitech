"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function isObj(x) {
    return x != null && typeof x === "object";
}

class EntityCore {
    constructor() {
        this.handle = null;
        this.body = null;
        this.bodyAccess = null;

        this.uuid = "";
        this.surfaceId = 0;
        this.bodyId = 0;

        this.snapshot = null;
        this.components = Object.create(null);

        this._getPos = null;
        this._getVel = null;
        this._getRot = null;
        this._getAngVel = null;

        this._groundProbe = null;

        this.state = {
            alive: false,
            uuid: "",

            mass: 0,
            radius: 0,
            height: 0,

            x: 0, y: 0, z: 0,

            rx: 0, ry: 0, rz: 0, rw: 1,

            vx: 0, vy: 0, vz: 0,
            avx: 0, avy: 0, avz: 0,

            speed: 0,
            grounded: false,

            flags: 0
        };
    }

    configureShape(mass, radius, height) {
        this.state.mass = +mass || 0;
        this.state.radius = +radius || 0;
        this.state.height = +height || 0;
        return this;
    }

    setGroundProbe(fn) {
        if (fn != null && typeof fn !== "function") {
            throw new Error("[EntityCore] groundProbe must be a function or null");
        }
        this._groundProbe = fn || null;
        return this;
    }

    /**
     * Hydrate mirror from ECS snapshot (read-only).
     * @param {*} snapshot
     * @returns {EntityCore}
     */
    hydrate(snapshot) {
        if (!isObj(snapshot)) return this;

        this.snapshot = snapshot;

        if (isObj(snapshot.components)) {
            const c = snapshot.components;
            for (const k of Object.keys(c)) this.components[k] = c[k];
        }

        if (typeof snapshot.uuid === "string" && snapshot.uuid) {
            this.uuid = snapshot.uuid;
            this.state.uuid = this.uuid;
        }
        if (snapshot.surfaceId != null) this.surfaceId = (snapshot.surfaceId | 0);
        if (snapshot.bodyId != null) this.bodyId = (snapshot.bodyId | 0);

        return this;
    }

    attach(handle, body, bodyAccess) {
        this.handle = req(handle, "[EntityCore] handle is required");
        this.body = body || null;

        const ba = req(bodyAccess, "[EntityCore] bodyAccess is required");
        if (typeof ba.position !== "function") throw new Error("[EntityCore] bodyAccess.position() is required");
        if (typeof ba.getVel !== "function") throw new Error("[EntityCore] bodyAccess.getVel() is required");
        if (typeof ba.rotation !== "function") throw new Error("[EntityCore] bodyAccess.rotation() is required");
        if (typeof ba.getAngVel !== "function") throw new Error("[EntityCore] bodyAccess.getAngVel() is required");

        this.bodyAccess = ba;

        const u =
            (typeof handle.uuidString === "function" ? handle.uuidString() : handle.uuid) || "";

        this.uuid = String(u || "");
        if (!this.uuid) throw new Error("[EntityCore] missing uuid (UUID-only)");

        this.surfaceId = (handle.surfaceId | 0) || 0;
        this.bodyId = (handle.bodyId | 0) || 0;
        if (this.bodyId <= 0) throw new Error("[EntityCore] invalid bodyId=" + this.bodyId);

        this._getPos = ba.position;
        this._getVel = ba.getVel;
        this._getRot = ba.rotation;
        this._getAngVel = ba.getAngVel;

        this.state.uuid = this.uuid;
        this.state.alive = true;
        return this;
    }

    syncPhysics() {
        if (!this.state.alive) throw new Error("[EntityCore] syncPhysics on dead entity");

        const p = this._getPos();
        const v = this._getVel();
        const q = this._getRot();
        const av = this._getAngVel();

        const px = this._num(p.x), py = this._num(p.y), pz = this._num(p.z);
        const vx = this._num(v.x), vy = this._num(v.y), vz = this._num(v.z);

        const rx = this._comp(q, "x"), ry = this._comp(q, "y"), rz = this._comp(q, "z"), rw = this._comp(q, "w");

        const avx = this._num(av.x), avy = this._num(av.y), avz = this._num(av.z);

        const s = this.state;

        s.x = px;
        s.y = py;
        s.z = pz;

        s.vx = vx;
        s.vy = vy;
        s.vz = vz;
        s.speed = Math.hypot(vx, vy, vz);

        s.rx = rx;
        s.ry = ry;
        s.rz = rz;
        s.rw = rw;

        s.avx = avx;
        s.avy = avy;
        s.avz = avz;

        s.grounded = this._groundProbe ? !!this._groundProbe(this) : false;

        return s;
    }

    destroy() {
        if (!this.state.alive) return;

        this.handle.destroy();

        this.handle = null;
        this.body = null;
        this.bodyAccess = null;

        this.uuid = "";
        this.surfaceId = 0;
        this.bodyId = 0;

        this.snapshot = null;
        this.components = Object.create(null);

        this._getPos = null;
        this._getVel = null;
        this._getRot = null;
        this._getAngVel = null;

        this._groundProbe = null;

        this.state.uuid = "";
        this.state.alive = false;
    }

    _num(v) {
        return (typeof v === "function") ? +v() : +v;
    }

    _comp(o, k) {
        const v = o ? o[k] : undefined;
        return (typeof v === "function") ? +v.call(o) : +v;
    }
}

module.exports = {EntityCore};