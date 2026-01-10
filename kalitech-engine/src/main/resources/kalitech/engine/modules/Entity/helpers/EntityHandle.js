// FILE: resources/kalitech/builtin/helpers/entity/EntityHandle.js
"use strict";

const {req, errCtx, subsystem} = require("./EntUtil.js");

class EntityHandle {
    constructor(engine, ctx) {
        this._engine = engine;

        // entityId теперь может быть 0 (UUID-only)
        this.entityId = (ctx.entityId | 0);
        this.uuid = (ctx.uuid != null) ? String(ctx.uuid) : "";

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

        if (!this.uuid) throw new Error("[ENT] EntityHandle missing uuid (UUID-only)");
    }

    id() {
        return (this.entityId | 0);
    } // optional
    uuidString() {
        return this.uuid || "";
    }

    uuid() {
        return this.uuidString();
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
        return this.uuidString();
    }

    [Symbol.toPrimitive](hint) {
        if (hint === "number") return (this.entityId | 0);
        return this.uuidString();
    }

    setVisible(v) {
        const sid = this.surfaceId | 0;
        if (!sid) throw new Error("[ENT] setVisible: surfaceId=0 uuid=" + this.uuid);

        const s = subsystem(this._engine, "surface");
        req(typeof s.setVisible === "function", "[ENT] setVisible: engine.surface().setVisible(surfaceId,bool) missing");

        s.setVisible(sid, !!v);
        return this;
    }

    setCull(hint) {
        const sid = this.surfaceId | 0;
        if (!sid) throw new Error("[ENT] setCull: surfaceId=0 uuid=" + this.uuid);

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
        if (id <= 0) throw new Error("[ENT] " + opName + ": entity has no bodyId (uuid=" + this.uuid + ")");
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

        const phys = this.physApi();
        if (phys && typeof phys.ref === "function") {
            this._bodyRef = phys.ref(id);
            this._refId = id;
            return this._bodyRef;
        }

        const teleport = (typeof phys.teleport === "function") ? phys.teleport.bind(phys) : phys.warp.bind(phys);

        this._bodyRef = Object.freeze({
            id: () => id,
            position: (v) => (v === undefined ? phys.position(id) : teleport(id, v)),
            teleport: (v) => teleport(id, v),
            warp: (v) => teleport(id, v),
            velocity: (v) => (v === undefined ? phys.velocity(id) : phys.velocity(id, v)),
            yaw: (yawRad) => phys.yaw(id, +yawRad || 0),
            applyImpulse: (imp) => phys.applyImpulse(id, imp),
            applyCentralForce: (f) => phys.applyCentralForce(id, f),
            lockRotation: (lock) => phys.lockRotation(id, !!lock),
            remove: () => phys.remove(id)
        });

        this._refId = id;
        return this._bodyRef;
    }

    // ---------------------
    // Entity ops (UUID-only)
    // ---------------------

    destroy() {
        const engine = this._engine;

        const ent = subsystem(engine, "entity");
        const surf = subsystem(engine, "surface");
        const phys = subsystem(engine, "physics");

        const uuid = this.uuid;
        const eid = (this.entityId | 0);
        const sid = (this.surfaceId | 0);
        const bid = (this.bodyId | 0);

        try {
            for (let i = 0; i < this._destroyers.length; i++) {
                try {
                    this._destroyers[i]();
                } catch (_) {
                }
            }
        } finally {
            this._destroyers.length = 0;
        }

        if (bid > 0) {
            try {
                phys.remove(bid);
            } catch (_) {
            }
            this.bodyId = 0;
            this.body = null;
        }

        if (sid > 0) {
            try {
                surf.drop(sid, true);
            } catch (_) {
            }
            this.surfaceId = 0;
            this.surface = null;
        }

        // ✅ prefer destroy(uuid)
        if (uuid && typeof ent.destroy === "function") {
            try {
                ent.destroy(uuid);
            } catch (_) {
            }
        } else if (eid > 0 && typeof ent.destroy === "function") {
            try {
                ent.destroy(eid);
            } catch (_) {
            }
        }

        this.entityId = 0;
        this.uuid = "";
    }

    addDestroyer(fn) {
        if (typeof fn !== "function") throw new Error("[ENT] addDestroyer(fn): fn must be a function");
        this._destroyers.push(fn);
        return this;
    }

    setComponent(type, value) {
        const ent = subsystem(this._engine, "entity");
        const uuid = this.uuid;
        if (!uuid) throw new Error("[ENT] setComponent: uuid empty");
        req(typeof ent.setComponent === "function", "[ENT] setComponent(uuid,type,value) missing");
        ent.setComponent(uuid, String(type), value);
        return this;
    }

    getComponent(type) {
        const ent = subsystem(this._engine, "entity");
        const uuid = this.uuid;
        if (!uuid) throw new Error("[ENT] getComponent: uuid empty");
        req(typeof ent.getComponent === "function", "[ENT] getComponent(uuid,type) missing");
        return ent.getComponent(uuid, String(type));
    }

    hasComponent(type) {
        const ent = subsystem(this._engine, "entity");
        const uuid = this.uuid;
        if (!uuid) throw new Error("[ENT] hasComponent: uuid empty");
        req(typeof ent.hasComponent === "function", "[ENT] hasComponent(uuid,type) missing");
        return !!ent.hasComponent(uuid, String(type));
    }

    logInfo(msg) {
        this._log.info(String(msg));
        return this;
    }

    logWarn(msg) {
        this._log.warn(String(msg));
        return this;
    }

    logError(msg) {
        this._log.error(String(msg));
        return this;
    }
}

module.exports = {EntityHandle};