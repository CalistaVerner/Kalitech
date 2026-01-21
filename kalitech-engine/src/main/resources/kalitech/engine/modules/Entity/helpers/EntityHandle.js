"use strict";

const {req, subsystem} = require("./EntUtil.js");
const {resolveBodyAccess} = require("./BodyAccessResolver.js");

function pushErr(list, op, e) {
    const msg = (e && e.stack) ? e.stack : String(e);
    list.push(op + " :: " + msg);
}

class EntityHandle {
    constructor(engine, ctx) {
        this._engine = engine;

        this.uuid = (ctx.uuid != null) ? String(ctx.uuid) : "";

        this.surface = ctx.surface || null;
        this.body = ctx.body || null;
        this.surfaceId = (ctx.surfaceId | 0);
        this.bodyId = (ctx.bodyId | 0);

        this._destroyers = Array.isArray(ctx._destroyers) ? ctx._destroyers : [];

        this._bodyAccess = null;
        this._bodyAccessId = 0;

        req(engine && engine.log && typeof engine.log === "function", "[ENT] engine.log() is required");
        this._log = engine.log();
        req(this._log && this._log.info && this._log.warn && this._log.error, "[ENT] engine.log() must provide info/warn/error");

        if (!this.uuid) throw new Error("[ENT] EntityHandle missing uuid (UUID-only)");

        this.core = null; // assigned by EntApi.create()
    }

    id() {
        throw new Error("[ENT] EntityHandle.id() removed (UUID-only)");
    }

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
        return this.uuidString();
    }

    toString() {
        return this.uuidString();
    }

    [Symbol.toPrimitive](_hint) {
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

    bodyAccess() {
        const id = this.requireBodyId("bodyAccess()");
        if (this._bodyAccess && (this._bodyAccessId | 0) === id) return this._bodyAccess;

        const phys = this.physApi();
        const ba = resolveBodyAccess(phys, this.body, id);

        this._bodyAccess = ba;
        this._bodyAccessId = id;
        return ba;
    }

    bodyRef() {
        return this.bodyAccess();
    }

    snapshot() {
        const ent = subsystem(this._engine, "entity");
        const uuid = this.uuid;
        if (!uuid) throw new Error("[ENT] snapshot: uuid empty");
        req(typeof ent.snapshot === "function", "[ENT] engine.entity().snapshot(uuid) missing");
        return ent.snapshot(uuid);
    }

    destroy() {
        const errors = [];

        const engine = this._engine;
        const uuid = this.uuid;
        const sid = (this.surfaceId | 0);
        const bid = (this.bodyId | 0);

        for (let i = 0; i < this._destroyers.length; i++) {
            try {
                this._destroyers[i]();
            } catch (e) {
                pushErr(errors, "destroyer[" + i + "]", e);
            }
        }
        this._destroyers.length = 0;

        if (bid > 0) {
            try {
                const phys = subsystem(engine, "physics");
                if (typeof phys.remove !== "function") throw new Error("engine.physics().remove(bodyId) missing");
                phys.remove(bid);
            } catch (e) {
                pushErr(errors, "physics.remove(" + bid + ")", e);
            }
        }

        if (sid > 0) {
            try {
                const surf = subsystem(engine, "surface");
                if (typeof surf.drop !== "function") throw new Error("engine.surface().drop(surfaceId,recursive) missing");
                surf.drop(sid, true);
            } catch (e) {
                pushErr(errors, "surface.drop(" + sid + ")", e);
            }
        }

        if (uuid) {
            try {
                const ent = subsystem(engine, "entity");
                if (typeof ent.destroy !== "function") throw new Error("engine.entity().destroy(uuid) missing");
                ent.destroy(uuid);
            } catch (e) {
                pushErr(errors, "entity.destroy(" + uuid + ")", e);
            }
        }

        this.bodyId = 0;
        this.surfaceId = 0;
        this.body = null;
        this.surface = null;
        this._bodyAccess = null;
        this._bodyAccessId = 0;
        this.uuid = "";
        this.core = null;

        if (errors.length) {
            const err = new Error("[ENT] destroy failed:\n- " + errors.join("\n- "));
            throw err;
        }
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