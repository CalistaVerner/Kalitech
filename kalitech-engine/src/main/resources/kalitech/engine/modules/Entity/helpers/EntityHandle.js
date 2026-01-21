// FILE: resources/kalitech/builtin/helpers/entity/EntityHandle.js
"use strict";

const {req, subsystem} = require("./EntUtil.js");
const {resolveBodyAccess} = require("./BodyAccessResolver.js");

function pushErr(list, op, e) {
    const msg = (e && e.stack) ? e.stack : String(e);
    list.push(op + " :: " + msg);
}

class EntityHandle {
    constructor(engine, ctx) {
        req(engine, "[ENT] engine is required");
        this._engine = engine;

        this.uuid = (ctx && ctx.uuid != null) ? String(ctx.uuid) : "";
        if (!this.uuid) throw new Error("[ENT] EntityHandle missing uuid (UUID-only)");

        this.surfaceId = (ctx && ctx.surfaceId) ? (ctx.surfaceId | 0) : 0;
        this.bodyId = (ctx && ctx.bodyId) ? (ctx.bodyId | 0) : 0;

        this._destroyers = (ctx && Array.isArray(ctx._destroyers)) ? ctx._destroyers : [];

        this._bodyRef = null;
        this._bodyRefId = 0;

        req(engine.log && typeof engine.log === "function", "[ENT] engine.log() is required");
        this._log = engine.log();

        this.core = null;
    }

    uuidString() {
        return this.uuid || "";
    }

    hasBody() {
        return (this.bodyId | 0) > 0;
    }

    requireBodyId(opName) {
        const id = this.bodyId | 0;
        if (id <= 0) throw new Error("[ENT] " + opName + ": entity has no bodyId (uuid=" + this.uuid + ")");
        return id;
    }

    phys() {
        return subsystem(this._engine, "physics");
    }

    bodyRef() {
        const id = this.requireBodyId("bodyRef()");
        if (this._bodyRef && (this._bodyRefId | 0) === id) return this._bodyRef;

        const ref = resolveBodyAccess(this.phys(), null, id);
        this._bodyRef = ref;
        this._bodyRefId = id;
        return ref;
    }

    entityApi() {
        return subsystem(this._engine, "entity");
    }

    snapshot() {
        const ent = this.entityApi();
        req(typeof ent.snapshot === "function", "[ENT] engine.entity().snapshot(uuid) missing");
        return ent.snapshot(this.uuid);
    }

    hydrateCore() {
        if (!this.core) return null;
        const snap = this.snapshot();
        if (snap) this.core.hydrate(snap);
        return this.core;
    }

    addDestroyer(fn) {
        if (typeof fn !== "function") throw new Error("[ENT] addDestroyer(fn): fn must be a function");
        this._destroyers.push(fn);
        return this;
    }

    setComponent(type, value) {
        const ent = this.entityApi();
        req(typeof ent.setComponent === "function", "[ENT] engine.entity().setComponent(uuid,type,value) missing");
        ent.setComponent(this.uuid, String(type), value);
        return this;
    }

    getComponent(type) {
        const ent = this.entityApi();
        req(typeof ent.getComponent === "function", "[ENT] engine.entity().getComponent(uuid,type) missing");
        return ent.getComponent(this.uuid, String(type));
    }

    hasComponent(type) {
        const ent = this.entityApi();
        req(typeof ent.hasComponent === "function", "[ENT] engine.entity().hasComponent(uuid,type) missing");
        return !!ent.hasComponent(this.uuid, String(type));
    }

    destroy() {
        const errors = [];

        for (let i = 0; i < this._destroyers.length; i++) {
            try {
                this._destroyers[i]();
            } catch (e) {
                pushErr(errors, "destroyer[" + i + "]", e);
            }
        }
        this._destroyers.length = 0;

        // Single source of truth: Java performs cleanup (surface+physics) on entity destroy.
        try {
            const ent = this.entityApi();
            req(typeof ent.destroy === "function", "[ENT] engine.entity().destroy(uuid) missing");
            ent.destroy(this.uuid);
        } catch (e) {
            pushErr(errors, "entity.destroy(" + this.uuid + ")", e);
        }

        this.bodyId = 0;
        this.surfaceId = 0;
        this._bodyRef = null;
        this._bodyRefId = 0;
        this.uuid = "";
        this.core = null;

        if (errors.length) {
            throw new Error("[ENT] destroy failed:\n- " + errors.join("\n- "));
        }
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