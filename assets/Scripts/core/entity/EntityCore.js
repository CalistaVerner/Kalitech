"use strict";

/**
 * EntityCore — REDengine MAX
 * База для любой Entity:
 *  - ids/handles
 *  - position/rotation
 *  - linear/angular velocity
 *  - grounded (через pluggable ground-probe)
 */

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function reqFn(fn, msg) {
    if (typeof fn !== "function") throw new Error(msg);
    return fn;
}

class EntityCore {
    constructor(ctx, domains, cfg) {
        this.ctx = req(ctx, "[EntityCore] ctx is required");
        this.d = req(domains, "[EntityCore] domains is required");
        this.cfg = cfg || Object.create(null);

        this.entity = null;
        this.body = null;
        this.bodyAccess = null;

        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        // resolved accessors (ONE TIME)
        this._getPos = null;
        this._getVel = null;
        this._getRot = null;
        this._getAngVel = null;

        // optional hook: (core) => boolean
        this._groundProbe = null;

        this.state = {
            alive: false,

            mass: 0,
            radius: 0,
            height: 0,

            x: 0, y: 0, z: 0,

            // quaternion
            rx: 0, ry: 0, rz: 0, rw: 1,

            // linear vel
            vx: 0, vy: 0, vz: 0,

            // angular vel
            avx: 0, avy: 0, avz: 0,

            speed: 0,
            grounded: false,

            flags: 0
        };
    }

    configureShape(mass, radius, height) {
        this.state.mass = +mass;
        this.state.radius = +radius;
        this.state.height = +height;
        return this;
    }

    /**
     * Set ground probe hook.
     * @param {Function|null} fn (core) => boolean
     */
    setGroundProbe(fn) {
        if (fn != null && typeof fn !== "function") {
            throw new Error("[EntityCore] groundProbe must be a function or null");
        }
        this._groundProbe = fn || null;
        return this;
    }

    attach(entity, body, bodyAccess) {
        this.entity = req(entity, "[EntityCore] entity is required");
        this.body = body || null;
        this.bodyAccess = req(bodyAccess, "[EntityCore] bodyAccess is required");

        this.entityId = entity.entityId | 0;
        this.surfaceId = entity.surfaceId | 0;
        this.bodyId = entity.bodyId | 0;

        if (this.bodyId <= 0) throw new Error("[EntityCore] invalid bodyId=" + this.bodyId);

        this._getPos = reqFn(this.bodyAccess.position, "[EntityCore] bodyAccess.position() is required");
        this._getVel = reqFn(this.bodyAccess.getVel, "[EntityCore] bodyAccess.getVel() is required");

        this._getRot = this._resolveRotationAccessor(this.bodyAccess);
        this._getAngVel = this._resolveAngularVelocityAccessor(this.bodyAccess);

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

        const rx = this._qx(q), ry = this._qy(q), rz = this._qz(q), rw = this._qw(q);

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

        // grounded is CORE concern now
        if (this._groundProbe) {
            s.grounded = !!this._groundProbe(this);
        } else {
            s.grounded = false;
        }

        return s;
    }

    _resolveRotationAccessor(ba) {
        const direct = [
            "rotation", "getRotation", "getRot",
            "quat", "getQuat", "getQuaternion",
            "orientation", "getOrientation",
            "getWorldRotation", "worldRotation"
        ];
        for (let i = 0; i < direct.length; i++) {
            const fn = ba[direct[i]];
            if (typeof fn === "function") return fn.bind(ba);
        }

        const tKeys = ["transform", "getTransform", "worldTransform", "getWorldTransform"];
        for (let i = 0; i < tKeys.length; i++) {
            const tf = ba[tKeys[i]];
            if (typeof tf !== "function") continue;

            return () => {
                const t = tf.call(ba);
                if (!t) throw new Error("[EntityCore] transform is null");

                if (typeof t.rotation === "function") return t.rotation();
                if (t.rotation !== undefined) return t.rotation;

                if (typeof t.getRotation === "function") return t.getRotation();
                if (typeof t.getQuaternion === "function") return t.getQuaternion();

                throw new Error("[EntityCore] cannot extract rotation from transform");
            };
        }

        // stable baseline
        return () => ({x: 0, y: 0, z: 0, w: 1});
    }

    _resolveAngularVelocityAccessor(ba) {
        const keys = [
            "getAngVel", "angVel",
            "getAngularVelocity", "angularVelocity",
            "getOmega", "omega"
        ];
        for (let i = 0; i < keys.length; i++) {
            const fn = ba[keys[i]];
            if (typeof fn === "function") return fn.bind(ba);
        }

        const tKeys = ["transform", "getTransform", "worldTransform", "getWorldTransform"];
        for (let i = 0; i < tKeys.length; i++) {
            const tf = ba[tKeys[i]];
            if (typeof tf !== "function") continue;

            return () => {
                const t = tf.call(ba);
                if (!t) return {x: 0, y: 0, z: 0};

                if (typeof t.angularVelocity === "function") return t.angularVelocity();
                if (t.angularVelocity !== undefined) return t.angularVelocity;

                return {x: 0, y: 0, z: 0};
            };
        }

        return () => ({x: 0, y: 0, z: 0});
    }

    _num(v) {
        return (typeof v === "function") ? +v() : +v;
    }

    _comp(o, k) {
        const v = o[k];
        return (typeof v === "function") ? +v.call(o) : +v;
    }

    _qx(q) {
        return this._comp(q, "x");
    }

    _qy(q) {
        return this._comp(q, "y");
    }

    _qz(q) {
        return this._comp(q, "z");
    }

    _qw(q) {
        if (q == null) return 1;
        if (q.w === undefined && typeof q.w !== "function") return 1;
        return this._comp(q, "w");
    }

    model() {
        const e = this.entity;
        if (!e) return null;

        if (typeof e.getModel === "function") return e.getModel();
        if (e.model !== undefined) return e.model;

        return null;
    }

    destroy() {
        if (!this.state.alive) return;

        this.entity.destroy(this.d.physics);

        this.entity = null;
        this.body = null;
        this.bodyAccess = null;

        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        this._getPos = null;
        this._getVel = null;
        this._getRot = null;
        this._getAngVel = null;

        this._groundProbe = null;

        this.state.alive = false;
    }
}

module.exports = {EntityCore};