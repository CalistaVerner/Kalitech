// FILE: resources/kalitech/builtin/helpers/entity/EntityHandle.js
"use strict";

const {req, num, errCtx, subsystem} = require("./EntUtil.js");

class EntityHandle {
    constructor(engine, ctx) {
        this._engine = engine;

        this.entityId = (ctx.entityId | 0);
        this.surface = ctx.surface || null;
        this.body = ctx.body || null;
        this.surfaceId = (ctx.surfaceId | 0);
        this.bodyId = (ctx.bodyId | 0);

        this._destroyers = Array.isArray(ctx._destroyers) ? ctx._destroyers : [];

        this._bodyRef = null;
        this._refId = 0;

        req(engine && engine.log && typeof engine.log === "function", "[ENT] engine.log() is required");
        this._log = engine.log();
        req(this._log && this._log.info && this._log.warn && this._log.error, "[ENT] engine.log() must provide info/warn/error");
    }

    id() {
        return (this.entityId | 0);
    }

    surfaceHandleId() {
        return (this.surfaceId | 0);
    }

    bodyHandleId() {
        return (this.bodyId | 0);
    }

    valueOf() {
        return (this.entityId | 0);
    }

    toString() {
        return String(this.entityId | 0);
    }

    [Symbol.toPrimitive](hint) {
        if (hint === "number") return (this.entityId | 0);
        return String(this.entityId | 0);
    }

    setVisible(v) {
        const sid = this.surfaceId | 0;
        if (!sid) throw new Error("[ENT] setVisible: surfaceId=0 entityId=" + (this.entityId | 0));

        const s = subsystem(this._engine, "surface");
        req(typeof s.setVisible === "function", "[ENT] setVisible: engine.surface().setVisible(surfaceId,bool) missing");

        s.setVisible(sid, !!v);
        return this;
    }

    setCull(hint) {
        const sid = this.surfaceId | 0;
        if (!sid) throw new Error("[ENT] setCull: surfaceId=0 entityId=" + (this.entityId | 0));

        const s = subsystem(this._engine, "surface");
        req(typeof s.setCull === "function", "[ENT] setCull: engine.surface().setCull(surfaceId,string) missing");

        s.setCull(sid, String(hint));
        return this;
    }

    hasBody() {
        return (this.bodyId | 0) > 0;
    }

    requireBodyId(opName) {
        const id = (this.bodyId | 0);
        if (id <= 0) throw new Error("[ENT] " + opName + ": entity has no bodyId (entityId=" + (this.entityId | 0) + ")");
        return id;
    }

    physApi() {
        const p = subsystem(this._engine, "physics");
        req(p, "[ENT] engine.physics() returned null");
        return p;
    }

    bodyRef() {
        const id = this.requireBodyId("bodyRef()");
        if (this._bodyRef && (this._refId | 0) === id) return this._bodyRef;

        if (globalThis.PHYS && typeof globalThis.PHYS.ref === "function") {
            this._bodyRef = globalThis.PHYS.ref(id);
            this._refId = id;
            return this._bodyRef;
        }

        const phys = this.physApi();
        const self = Object.freeze({
            id: () => id,
            position: (v) => (v === undefined ? phys.position(id) : phys.warp(id, v)),
            warp: (v) => phys.warp(id, v),
            velocity: (v) => (v === undefined ? phys.velocity(id) : phys.velocity(id, v)),
            yaw: (yawRad) => phys.yaw(id, +yawRad || 0),
            applyImpulse: (imp) => phys.applyImpulse(id, imp),
            applyCentralForce: (f) => phys.applyCentralForce(id, f),
            applyTorque: (t) => phys.applyTorque(id, t),
            angularVelocity: (v) => (v === undefined ? phys.angularVelocity(id) : phys.angularVelocity(id, v)),
            clearForces: () => phys.clearForces(id),
            lockRotation: (lock) => phys.lockRotation(id, !!lock),
            collisionGroups: (g, m) => phys.collisionGroups(id, g | 0, m | 0),
            remove: () => phys.remove(id)
        });

        this._bodyRef = self;
        this._refId = id;
        return self;
    }

    position(v) {
        const id = this.requireBodyId("position()");
        const phys = this.physApi();
        try {
            if (v === undefined) return phys.position(id);
            return phys.warp(id, v);
        } catch (e) {
            throw new Error(errCtx("[ENT] position failed bodyId=" + id, e));
        }
    }

    warp(pos) {
        const id = this.requireBodyId("warp()");
        const phys = this.physApi();
        try {
            return phys.warp(id, pos);
        } catch (e) {
            throw new Error(errCtx("[ENT] warp failed bodyId=" + id, e));
        }
    }

    velocity(v) {
        const id = this.requireBodyId("velocity()");
        const phys = this.physApi();
        try {
            if (v === undefined) return phys.velocity(id);
            return phys.velocity(id, v);
        } catch (e) {
            throw new Error(errCtx("[ENT] velocity failed bodyId=" + id, e));
        }
    }

    yaw(yawRad) {
        const id = this.requireBodyId("yaw()");
        const phys = this.physApi();
        try {
            return phys.yaw(id, +yawRad || 0);
        } catch (e) {
            throw new Error(errCtx("[ENT] yaw failed bodyId=" + id, e));
        }
    }

    applyImpulse(impulse) {
        const id = this.requireBodyId("applyImpulse()");
        const phys = this.physApi();
        try {
            return phys.applyImpulse(id, impulse);
        } catch (e) {
            throw new Error(errCtx("[ENT] applyImpulse failed bodyId=" + id, e));
        }
    }

    applyCentralForce(force) {
        const id = this.requireBodyId("applyCentralForce()");
        const phys = this.physApi();
        req(typeof phys.applyCentralForce === "function", "[ENT] engine.physics().applyCentralForce missing");
        try {
            return phys.applyCentralForce(id, force);
        } catch (e) {
            throw new Error(errCtx("[ENT] applyCentralForce failed bodyId=" + id, e));
        }
    }

    applyTorque(torque) {
        const id = this.requireBodyId("applyTorque()");
        const phys = this.physApi();
        req(typeof phys.applyTorque === "function", "[ENT] engine.physics().applyTorque missing");
        try {
            return phys.applyTorque(id, torque);
        } catch (e) {
            throw new Error(errCtx("[ENT] applyTorque failed bodyId=" + id, e));
        }
    }

    angularVelocity(v) {
        const id = this.requireBodyId("angularVelocity()");
        const phys = this.physApi();
        req(typeof phys.angularVelocity === "function", "[ENT] engine.physics().angularVelocity missing");
        try {
            if (v === undefined) return phys.angularVelocity(id);
            return phys.angularVelocity(id, v);
        } catch (e) {
            throw new Error(errCtx("[ENT] angularVelocity failed bodyId=" + id, e));
        }
    }

    clearForces() {
        const id = this.requireBodyId("clearForces()");
        const phys = this.physApi();
        req(typeof phys.clearForces === "function", "[ENT] engine.physics().clearForces missing");
        try {
            return phys.clearForces(id);
        } catch (e) {
            throw new Error(errCtx("[ENT] clearForces failed bodyId=" + id, e));
        }
    }

    lockRotation(lock = true) {
        const id = this.requireBodyId("lockRotation()");
        const phys = this.physApi();
        try {
            return phys.lockRotation(id, !!lock);
        } catch (e) {
            throw new Error(errCtx("[ENT] lockRotation failed bodyId=" + id, e));
        }
    }

    collisionGroups(group, mask) {
        const id = this.requireBodyId("collisionGroups()");
        const phys = this.physApi();
        req(typeof phys.collisionGroups === "function", "[ENT] engine.physics().collisionGroups missing");
        try {
            return phys.collisionGroups(id, group | 0, mask | 0);
        } catch (e) {
            throw new Error(errCtx("[ENT] collisionGroups failed bodyId=" + id, e));
        }
    }

    raycast(cfg) {
        const phys = this.physApi();
        req(typeof phys.raycast === "function", "[ENT] engine.physics().raycast missing");
        try {
            return phys.raycast(cfg);
        } catch (e) {
            throw new Error(errCtx("[ENT] raycast failed", e));
        }
    }

    raycastDown(distance = 2.0, startOffsetY = 0.15) {
        const id = this.requireBodyId("raycastDown()");
        const phys = this.physApi();
        req(typeof phys.position === "function", "[ENT] engine.physics().position missing");
        req(typeof phys.raycast === "function", "[ENT] engine.physics().raycast missing");

        const p = phys.position(id);
        req(p, "[ENT] raycastDown: position() returned null for bodyId=" + id);

        const px = (typeof p.x === "function") ? num(p.x(), 0) : num(p.x, 0);
        const py = (typeof p.y === "function") ? num(p.y(), 0) : num(p.y, 0);
        const pz = (typeof p.z === "function") ? num(p.z(), 0) : num(p.z, 0);

        const from = {x: px, y: py + num(startOffsetY, 0.15), z: pz};
        const to = {x: px, y: py - num(distance, 2.0), z: pz};

        try {
            return phys.raycast({from, to});
        } catch (e) {
            throw new Error(errCtx("[ENT] raycastDown failed bodyId=" + id, e));
        }
    }

    component(name, data) {
        const n = String(name || "");
        if (!n) return this;

        const id = (this.entityId | 0);
        req(id > 0, "[ENT] component(): entityId=0");

        try {
            const ent = subsystem(this._engine, "entity");
            ent.setComponent(id, n, data);
        } catch (e) {
            throw new Error(errCtx("[ENT] setComponent failed id=" + id + " name=" + n, e));
        }
        return this;
    }

    components(mapOrFn) {
        if (!mapOrFn) return this;

        const id = (this.entityId | 0);
        req(id > 0, "[ENT] components(): entityId=0");

        let map = mapOrFn;
        if (typeof mapOrFn === "function") {
            map = mapOrFn({
                entityId: id,
                surface: this.surface,
                body: this.body,
                surfaceId: (this.surfaceId | 0),
                bodyId: (this.bodyId | 0)
            });
        }

        if (!map || typeof map !== "object") return this;

        const ent = subsystem(this._engine, "entity");

        for (const k of Object.keys(map)) {
            const n = String(k || "");
            if (!n) continue;
            try {
                ent.setComponent(id, n, map[k]);
            } catch (e) {
                throw new Error(errCtx("[ENT] setComponent failed id=" + id + " name=" + n, e));
            }
        }

        return this;
    }

    destroy() {
        for (let i = this._destroyers.length - 1; i >= 0; i--) {
            this._destroyers[i]();
        }
        this._destroyers.length = 0;

        const bid = (this.bodyId | 0);
        if (bid > 0) {
            const p = subsystem(this._engine, "physics");
            p.remove(bid);
        }

        this.body = null;
        this.surface = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        this._bodyRef = null;
        this._refId = 0;
    }
}

module.exports = {EntityHandle};