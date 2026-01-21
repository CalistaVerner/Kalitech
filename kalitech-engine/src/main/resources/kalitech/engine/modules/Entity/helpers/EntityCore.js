// FILE: resources/kalitech/builtin/helpers/entity/EntityCore.js
"use strict";

function isObj(v) {
    return v != null && typeof v === "object";
}

function shallowFreezeCopy(obj) {
    if (!isObj(obj)) return Object.freeze(Object.create(null));
    const out = Object.create(null);
    for (const k of Object.keys(obj)) out[k] = obj[k];
    return Object.freeze(out);
}

/**
 * EntityCore (Mirror)
 * JS holds ONLY last snapshot + ids for UI.
 * Authoritative data lives in Java/ECS.
 */
class EntityCore {
    constructor(uuid, surfaceId, bodyId) {
        this.uuid = String(uuid || "");
        this.surfaceId = surfaceId | 0;
        this.bodyId = bodyId | 0;

        this.alive = !!this.uuid;
        this.snapshot = null;

        this.componentTypes = Object.freeze([]);
        this.components = Object.freeze(Object.create(null));
    }

    /**
     * Hydrate mirror from Java snapshot.
     * Snapshot contract: { uuid, alive, componentTypes, componentsByName }
     */
    hydrate(snapshot) {
        if (!isObj(snapshot)) return this;

        this.snapshot = snapshot;

        if (typeof snapshot.uuid === "string" && snapshot.uuid) {
            this.uuid = snapshot.uuid;
            this.alive = !!snapshot.alive;
        }

        const types = Array.isArray(snapshot.componentTypes) ? snapshot.componentTypes.slice(0) : [];
        this.componentTypes = Object.freeze(types);

        const byName = snapshot.componentsByName || snapshot.components || null;
        this.components = shallowFreezeCopy(byName);

        return this;
    }
}

module.exports = {EntityCore};